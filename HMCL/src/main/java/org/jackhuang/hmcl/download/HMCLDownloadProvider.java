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
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.ResourceCleaner;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.Semaphore;

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

        boolean acquired = false;
        if (!refresh) {
            state.semaphore.acquire();
            acquired = true;
            SortedSet<ComponentRemoteVersion> result = state.tryGet(gameVersion);
            if (result != null) {
                state.semaphore.release();
                return Task.completed(result);
            }
        }

        if (!acquired) {
            state.semaphore.acquire();
        }

        ResourceCleaner cleaner = ResourceCleaner.ofSemaphore(state.semaphore);

        Task<? extends SortedSet<? extends ComponentRemoteVersion>> task;
        try {
            task = switch (type) {
                case GAME -> fetchGameVersions();
                default -> throw new AssertionError();
            };
            task.onDone().register(cleaner::close);
        } catch (Throwable e) {
            cleaner.close();
            throw e;
        }

        @SuppressWarnings("unchecked") var result = (Task<SortedSet<ComponentRemoteVersion>>) task;
        return result;
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

    private Task<SortedSet<GameRemoteVersion>> fetchGameVersions() {
        DownloadSource versionListSource = this.versionListSource;
        return null; // TODO
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

