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

import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersionInfo;
import org.jackhuang.hmcl.download.game.GameRemoteVersions;
import org.jackhuang.hmcl.download.game.GameVersionList;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.ResourceCleaner;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.Semaphore;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.logging.Logger.registerAccessToken;

/// @author Glavo
@NotNullByDefault
public final class HMCLDownloadProvider {
    private static final String BMCLAPI_ROOT = System.getProperty("hmcl.bmclapi.override", "https://bmclapi2.bangbang93.com");

    private static final VarHandle VERSION_LIST_STATES_HANDLE = MethodHandles.arrayElementVarHandle(VersionListState[].class);
    private final @Nullable VersionListState[] versionListStates = new VersionListState[GameComponentType.ALL.size()];
    private volatile DownloadSource versionListSource = DownloadSource.DEFAULT;
    private volatile DownloadSource fileSource = DownloadSource.DEFAULT;

    public @Unmodifiable Task<SortedSet<ComponentRemoteVersion>> getVersions(
            GameComponentType type,
            @Nullable GameVersionNumber gameVersion,
            boolean refresh) throws Exception {
        assert (type == GameComponentType.GAME) == (gameVersion == null);

        final VersionListState state = getState(type);

        if (!refresh) {
            state.semaphore.acquire();
            try {
                SortedSet<ComponentRemoteVersion> result = state.tryGet(gameVersion);
                if (result != null) {
                    return Task.completed(result);
                }
            } finally {
                state.semaphore.release();
            }
        }

        @SuppressWarnings("unchecked")
        var task = (Task<SortedSet<ComponentRemoteVersion>>) (Task<? extends SortedSet<? extends ComponentRemoteVersion>>) switch (type) {
            case GAME -> fetchGameVersions();
            default -> throw new AssertionError();
        };

        return task.thenApplyAsync(result -> {
            state.semaphore.acquire();
            try {
                state.put(gameVersion, result);
            } finally {
                state.semaphore.release();
            }
            return result;
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

    private static final String DEFAULT_VERSION_LIST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest.json";
    private static final String BMCLAPI_VERSION_LIST_URL = BMCLAPI_ROOT + "/mc/game/version_manifest.json";

    /// @see GameRemoteVersion
    private Task<SortedSet<GameRemoteVersion>> fetchGameVersions() {
        DownloadSource source = this.versionListSource;

        List<DownloadCandidate> candidates;
        if (LocaleUtils.IS_CHINA_MAINLAND) {
            candidates = switch (source) {
                case DEFAULT, OFFICIAL -> List.of(
                        DownloadCandidate.of(DEFAULT_VERSION_LIST_URL), DownloadCandidate.of(BMCLAPI_VERSION_LIST_URL)
                );
                case MIRROR -> List.of(
                        DownloadCandidate.of(BMCLAPI_VERSION_LIST_URL), DownloadCandidate.of(DEFAULT_VERSION_LIST_URL)
                );
            };
        } else {
            candidates = switch (source) {
                case DEFAULT, OFFICIAL -> List.of(DownloadCandidate.of(DEFAULT_VERSION_LIST_URL));
                case MIRROR -> List.of(
                        DownloadCandidate.of(BMCLAPI_VERSION_LIST_URL), DownloadCandidate.of(DEFAULT_VERSION_LIST_URL)
                );
            };
        }

        return new GetTask(candidates, null).thenGetJsonAsync(GameRemoteVersions.class)
                .thenApplyAsync(root -> {
                    GameRemoteVersions unlistedVersions = null;

                    //noinspection DataFlowIssue
                    try (Reader input = new InputStreamReader(
                            GameVersionList.class.getResourceAsStream("/assets/game/unlisted-versions.json"))) {
                        unlistedVersions = JsonUtils.GSON.fromJson(input, GameRemoteVersions.class);
                    } catch (Throwable e) {
                        LOG.warning("Failed to load unlisted versions", e);
                    }

                    var versions = new TreeSet<GameRemoteVersion>();

                    if (unlistedVersions != null) {
                        for (GameRemoteVersionInfo unlistedVersion : unlistedVersions.versions()) {
                            versions.add(new GameRemoteVersion(
                                    unlistedVersion.gameVersion(),
                                    List.of(unlistedVersion.url()),
                                    unlistedVersion.type(), unlistedVersion.releaseTime()));
                        }
                    }

                    for (GameRemoteVersionInfo remoteVersion : root.versions()) {
                        versions.add(new GameRemoteVersion(
                                remoteVersion.gameVersion(),
                                List.of(remoteVersion.url()),
                                remoteVersion.type(), remoteVersion.releaseTime()));
                    }

                    return versions;
                });
    }

    private static final class VersionListState {
        private final GameComponentType type;
        private final Semaphore semaphore = new Semaphore(1);
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

        void put(@Nullable GameVersionNumber gameVersion, SortedSet<ComponentRemoteVersion> componentRemoteVersions) {
            assert (type == GameComponentType.GAME) == (gameVersion == null);
            versions.put(gameVersion, new SoftReference<>(componentRemoteVersions));
        }
    }

}

