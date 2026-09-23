/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.download;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

@NotNullByDefault
public class DownloadProvider2 {

    private static final VarHandle VERSION_LIST_STATES_HANDLE = MethodHandles.arrayElementVarHandle(VersionListState[].class);
    private final @Nullable VersionListState[] versionListStates = new VersionListState[GameComponentType.ALL.size()];

    @SuppressWarnings("unchecked")
    public @Unmodifiable Task<SortedSet<ComponentRemoteVersion>> getVersions(
            GameComponentType type,
            @Nullable GameVersionNumber gameVersion,
            boolean refresh) throws Exception {
        assert (type == GameComponentType.GAME) == (gameVersion == null);

        final VersionListState state = getState(type);

        if (!refresh) {
            state.lock.readLock().lockInterruptibly();
            try {
                SortedSet<ComponentRemoteVersion> result = state.tryGet(gameVersion);
                if (result != null) {
                    return Task.completed(result);
                }
            } finally {
                state.lock.readLock().unlock();
            }
        }

        var task = fetchVersionsAsync(type, gameVersion);

        return task.thenApplyAsync(result -> {
            state.lock.writeLock().lockInterruptibly();
            try {
                state.put(gameVersion, result);
            } finally {
                state.lock.writeLock().unlock();
            }
            return (SortedSet<ComponentRemoteVersion>) result;
        });
    }

    private VersionListState getState(GameComponentType type) {
        @Nullable VersionListState currentState = versionListStates[type.ordinal()];
        if (currentState != null) {
            return currentState;
        }

        VersionListState state = new VersionListState(type);
        if (VERSION_LIST_STATES_HANDLE.compareAndSet(versionListStates, type.ordinal(), null, state)) {
            return state;
        } else {
            state = (VersionListState) VERSION_LIST_STATES_HANDLE.getVolatile(versionListStates, type.ordinal());
            Objects.requireNonNull(state, "VersionListState should not be null after compareAndSet failure");
            return state;
        }
    }

    protected <V extends ComponentRemoteVersion> @Unmodifiable Task<SortedSet<V>> fetchFabricVersionsAsync(
            GameVersionNumber gameVersion,
            List<DownloadCandidate> loaderMetaCandidates, List<DownloadCandidate> gameMetaCandidates,
            BiFunction<String, String, V> function
    ) {
        return Task.combine(
                new GetTask(loaderMetaCandidates, null),
                new GetTask(gameMetaCandidates, null)
        ).thenApplyAsync(pair -> {
            @JsonSerializable
            record GameVersion(String version, String maven, boolean stable) {
            }

            TypeToken<List<GameVersion>> gameVersionsType = listTypeOf(GameVersion.class);

            List<GameVersion> gameVersions = JsonUtils.fromNonNullJson(pair.getKey(), gameVersionsType);

            Optional<GameVersion> metaGameVersion = gameVersions.stream()
                    .filter(it -> gameVersion.equals(GameVersionNumber.asGameVersion(it.version)))
                    .findFirst();
            if (metaGameVersion.isEmpty()) {
                return Collections.emptySortedSet();
            }

            SortedSet<V> versions = new TreeSet<>();
            List<GameVersion> loaderVersions = JsonUtils.fromNonNullJson(pair.getValue(), gameVersionsType);
            for (GameVersion loaderVersion : loaderVersions) {
                versions.add(function.apply(metaGameVersion.get().version, loaderVersion.version));
            }

            return Collections.unmodifiableSortedSet(versions);
        });
    }


    protected <V extends ComponentRemoteVersion> Task<SortedSet<V>> fetchModrinthVersionsAsync(
            String modId,
            GameVersionNumber gameVersion,
            Function<RemoteAddon.Version, V> mapper
    ) {
        return Task.supplyAsync(Schedulers.io(), () -> {
            return ModrinthRemoteAddonRepository.MODS.getRemoteVersionsById(null, modId)
                    .filter(it -> {
                        for (String supportedGameVersion : it.gameVersions()) {
                            if (GameVersionNumber.asGameVersion(supportedGameVersion).equals(gameVersion)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .map(mapper)
                    .collect(Collectors.toCollection(() -> (SortedSet<V>) new TreeSet<V>()));
        });
    }

    protected Task<? extends SortedSet<? extends ComponentRemoteVersion>> fetchVersionsAsync(
            GameComponentType type, @Nullable GameVersionNumber gameVersion
    ) {
        assert (type == GameComponentType.GAME) == (gameVersion == null);

        return switch (type) {
            case GAME ->
                    GameRemoteVersion.fetchAsync(List.of(DownloadCandidate.of(GameRemoteVersion.VERSION_MANIFEST_URL)));
            case LEGACY_FABRIC -> fetchFabricVersionsAsync(
                    gameVersion,
                    List.of(DownloadCandidate.of(LegacyFabricRemoteVersion.GAME_META_URL)),
                    List.of(DownloadCandidate.of(LegacyFabricRemoteVersion.LOADER_META_URL)),
                    (metaGameVersion, loaderVersion) -> new LegacyFabricRemoteVersion(
                            gameVersion.toString(), loaderVersion,
                            List.of("%s/%s/%s".formatted(LegacyFabricRemoteVersion.LOADER_META_URL, metaGameVersion, loaderVersion)))
            );
            case LEGACY_FABRIC_API -> fetchModrinthVersionsAsync(
                    LegacyFabricAPIRemoteVersion.MODRINTH_ID,
                    gameVersion,
                    it -> new LegacyFabricAPIRemoteVersion(
                            gameVersion.toString(),
                            it.version(),
                            it.name(),
                            it.datePublished(),
                            it,
                            List.of(it.file().url()))
            );
            case FABRIC -> fetchFabricVersionsAsync(
                    gameVersion,
                    List.of(DownloadCandidate.of(LegacyFabricRemoteVersion.GAME_META_URL)),
                    List.of(DownloadCandidate.of(LegacyFabricRemoteVersion.LOADER_META_URL)),
                    (metaGameVersion, loaderVersion) -> new LegacyFabricRemoteVersion(
                            gameVersion.toString(), loaderVersion,
                            List.of("%s/%s/%s".formatted(LegacyFabricRemoteVersion.LOADER_META_URL, metaGameVersion, loaderVersion)))
            );

            default -> throw new AssertionError("TODO");
        };
    }

    private static final class VersionListState {
        private final GameComponentType type;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final Map<@Nullable GameVersionNumber, SoftReference<SortedSet<ComponentRemoteVersion>>> versions = new HashMap<>();

        private VersionListState(GameComponentType type) {
            this.type = type;
        }

        @Unmodifiable
        @Nullable SortedSet<ComponentRemoteVersion> tryGet(@Nullable GameVersionNumber gameVersion) {
            assert (type == GameComponentType.GAME) == (gameVersion == null);

            @Nullable SoftReference<SortedSet<ComponentRemoteVersion>> resultRef = versions.get(gameVersion);
            return resultRef != null ? resultRef.get() : null;
        }

        @SuppressWarnings("unchecked")
        void put(@Nullable GameVersionNumber gameVersion, SortedSet<? extends ComponentRemoteVersion> componentRemoteVersions) {
            assert (type == GameComponentType.GAME) == (gameVersion == null);
            versions.put(gameVersion, new SoftReference<>((SortedSet<ComponentRemoteVersion>) componentRemoteVersions));
        }
    }
}
