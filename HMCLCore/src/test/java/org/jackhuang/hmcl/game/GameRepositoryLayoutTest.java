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
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests pure path computation in [DefaultGameRepositoryLayout].
@NotNullByDefault
public final class GameRepositoryLayoutTest {
    /// Verifies conventional instance, shared-library, asset, and logging paths.
    @Test
    public void computesConventionalPaths() {
        Path baseDirectory = Path.of("games", "default");
        GameRepositoryLayout layout = new DefaultGameRepositoryLayout(baseDirectory);
        GameInstanceID instanceId = new GameInstanceID("1.21.8");

        assertEquals(baseDirectory, layout.getBaseDirectory());
        assertEquals(
                baseDirectory.resolve("versions").resolve("1.21.8"),
                layout.getInstanceRoot(instanceId));
        assertEquals(
                baseDirectory.resolve("versions").resolve("1.21.8").resolve("1.21.8.json"),
                layout.getInstanceJson(instanceId));
        assertEquals(
                baseDirectory.resolve("versions").resolve("1.21.8").resolve("1.21.8.jar"),
                layout.getInstanceJarFile(instanceId));
        assertEquals(baseDirectory.resolve("libraries"), layout.getLibrariesDirectory());
        assertEquals(baseDirectory.resolve("assets"), layout.getAssetDirectory());
        assertEquals(
                baseDirectory.resolve("assets").resolve("indexes").resolve("25.json"),
                layout.getAssetIndexFile("25"));

        AssetObject assetObject =
                new AssetObject("0123456789abcdef0123456789abcdef01234567", 42);
        assertEquals(
                baseDirectory.resolve("assets")
                        .resolve("objects")
                        .resolve(assetObject.getLocation()),
                layout.getAssetObject(assetObject));

        LoggingInfo loggingInfo =
                new LoggingInfo(new IdDownloadInfo("client-log.xml", ""));
        assertEquals(
                baseDirectory.resolve("assets")
                        .resolve("log_configs")
                        .resolve("client-log.xml"),
                layout.getLoggingObject("25", loggingInfo));
    }

    /// Verifies shared and instance-local library resolution.
    @Test
    public void resolvesLibraryHints() {
        GameRepositoryLayout layout = new DefaultGameRepositoryLayout(Path.of("game"));
        GameInstanceID owner = new GameInstanceID("forge");
        Artifact artifact = new Artifact("com.example", "demo", "1.0");
        Library sharedLibrary = new Library(artifact);
        Library namedLocalLibrary = new Library(
                artifact,
                null,
                null,
                null,
                null,
                null,
                null,
                "local",
                "patched.jar");
        Library conventionalLocalLibrary = new Library(
                artifact,
                null,
                null,
                null,
                null,
                null,
                null,
                "local",
                null);

        assertEquals(
                Path.of("game").resolve("libraries").resolve(artifact.getPath()),
                layout.getLibraryFile(owner, sharedLibrary));
        assertEquals(
                Path.of("game").resolve("versions").resolve("forge")
                        .resolve("libraries")
                        .resolve("patched.jar"),
                layout.getLibraryFile(owner, namedLocalLibrary));
        assertEquals(
                Path.of("game").resolve("versions").resolve("forge")
                        .resolve("libraries")
                        .resolve(artifact.getFileName()),
                layout.getLibraryFile(owner, conventionalLocalLibrary));
    }
}
