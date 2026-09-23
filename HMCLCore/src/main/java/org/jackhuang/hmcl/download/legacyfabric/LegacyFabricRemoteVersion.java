/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.legacyfabric;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

public class LegacyFabricRemoteVersion extends ComponentRemoteVersion {

    public static final String LOADER_META_URL = "https://meta.legacyfabric.net/v2/versions/loader";
    public static final String GAME_META_URL = "https://meta.legacyfabric.net/v2/versions/game";

    public static @Unmodifiable Task<SortedSet<LegacyFabricRemoteVersion>> fetchAsync(
            GameVersionNumber gameVersion,
            List<DownloadCandidate> loaderMetaCandidates, List<DownloadCandidate> gameMetaCandidates
    ) {
        return Task.combine(
                new GetTask(loaderMetaCandidates, null),
                new GetTask(gameMetaCandidates, null)
        ).thenApplyAsync(pair -> {
            TypeToken<List<GameVersion>> gameVersionsType = listTypeOf(GameVersion.class);

            List<GameVersion> gameVersions = JsonUtils.fromNonNullJson(pair.getKey(), gameVersionsType);

            Optional<GameVersion> metaGameVersion = gameVersions.stream()
                    .filter(it -> gameVersion.equals(GameVersionNumber.asGameVersion(it.version)))
                    .findFirst();
            if (metaGameVersion.isEmpty()) {
                return Collections.emptySortedSet();
            }

            TreeSet<LegacyFabricRemoteVersion> versions = new TreeSet<>();
            List<GameVersion> loaderVersions = JsonUtils.fromNonNullJson(pair.getValue(), gameVersionsType);
            for (GameVersion loaderVersion : loaderVersions) {
                versions.add(new LegacyFabricRemoteVersion(
                        gameVersion.toString(), loaderVersion.version,
                        List.of("%s/%s/%s"
                                .formatted(LOADER_META_URL, metaGameVersion.get().version, loaderVersion.version))));
            }

            return Collections.unmodifiableSortedSet(versions);
        });
    }

    /// Constructor.
    ///
    /// @param gameVersion the Minecraft version that this remote version suits.
    /// @param selfVersion the version string of the remote version.
    /// @param urls        the installer or universal jar original URL.
    LegacyFabricRemoteVersion(String gameVersion, String selfVersion, List<String> urls) {
        super(GameComponentType.LEGACY_FABRIC, gameVersion, selfVersion, null, urls);
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new LegacyFabricInstallTask(dependencyManager, baseManifest, this);
    }

    @JsonSerializable
    private record GameVersion(String version, String maven, boolean stable) {
    }
}
