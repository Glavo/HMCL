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
package org.jackhuang.hmcl.download.neoforge;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.ComponentRemoteVersionList;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

@NotNullByDefault
public final class NeoForgeRemoteVersion extends ComponentRemoteVersion {

    private static final String OLD_URL = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/forge";
    private static final String META_URL = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";

    public static Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchBMCLAsync(String bmclRoot, GameVersionNumber gameVersion) {
        record NeoForgeBMCLVersion(String rawVersion,
                                   String version,
                                   @SerializedName("mcversion") String mcVersion) {
            public NeoForgeBMCLVersion {
                Objects.requireNonNull(rawVersion, "rawVersion");
                Objects.requireNonNull(version, "version");
                Objects.requireNonNull(mcVersion, "mcversion");
            }
        }

        return new GetTask(DownloadCandidates.of(bmclRoot + "/neoforge/list/" + gameVersion)).thenGetJsonAsync(listTypeOf(NeoForgeBMCLVersion.class))
                .thenApplyAsync(neoForgeVersions -> {
                    var versions = new TreeSet<NeoForgeRemoteVersion>();
                    for (NeoForgeBMCLVersion neoForgeVersion : neoForgeVersions) {
                        versions.add(new NeoForgeRemoteVersion(
                                GameVersionNumber.asGameVersion(neoForgeVersion.mcVersion),
                                NeoForgeRemoteVersion.normalize(neoForgeVersion.version),
                                List.of(bmclRoot + "/neoforge/version/" + neoForgeVersion.version + "/download/installer.jar")
                        ));
                    }
                    return ComponentRemoteVersionList.of(GameComponentType.NEO_FORGE, versions);
                });
    }

    public NeoForgeRemoteVersion(GameVersionNumber gameVersion, String selfVersion, List<String> urls) {
        super(GameComponentType.NEO_FORGE, gameVersion, selfVersion, null, getType(selfVersion), urls);
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new NeoForgeInstallTask(dependencyManager, baseManifest, this);
    }

    private static Type getType(String version) {
        return version.contains("beta") || version.contains("alpha") ? Type.SNAPSHOT : Type.RELEASE;
    }

    public static String normalize(String version) {
        if (version.startsWith("1.20.1-")) {
            if (version.startsWith("forge-", "1.20.1-".length())) {
                return version.substring("1.20.1-forge-".length());
            } else {
                return version.substring("1.20.1-".length());
            }
        } else {
            return version;
        }
    }
}
