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

import java.nio.file.Path;
import java.util.Objects;

/// Immutable core game instance captured in a repository snapshot.
@NotNullByDefault
public final class DefaultGameInstance implements GameInstance {
    /// Layout used to derive instance paths.
    private final GameRepositoryLayout layout;

    /// Stable ID of this snapshot value.
    private final GameInstanceID id;

    /// Stored manifest captured by this snapshot value.
    private final GameInstanceManifest manifest;

    /// Eagerly resolved manifest captured by this snapshot value.
    private final GameInstanceManifest.Resolved resolvedManifest;

    /// Creates an immutable instance value.
    ///
    /// The manifest and both resolved views must describe the same instance ID.
    ///
    /// @param layout           the repository layout used for paths
    /// @param manifest         the stored manifest
    /// @param resolvedManifest the eagerly resolved views of `manifest`
    /// @throws IllegalArgumentException if the manifest IDs differ
    public DefaultGameInstance(
            GameRepositoryLayout layout,
            GameInstanceManifest manifest,
            GameInstanceManifest.Resolved resolvedManifest) {
        this.layout = Objects.requireNonNull(layout);
        this.manifest = Objects.requireNonNull(manifest);
        this.resolvedManifest = Objects.requireNonNull(resolvedManifest);
        this.id = manifest.id();

        if (!id.equals(resolvedManifest.unresolved().id())
                || !id.equals(resolvedManifest.launchManifest().id())
                || !id.equals(resolvedManifest.standaloneManifest().id())) {
            throw new IllegalArgumentException("All manifest views must describe the same instance");
        }
    }

    /// {@inheritDoc}
    @Override
    public GameInstanceID getId() {
        return id;
    }

    /// {@inheritDoc}
    @Override
    public GameInstanceManifest getManifest() {
        return manifest;
    }

    /// {@inheritDoc}
    @Override
    public GameInstanceManifest.Resolved getResolvedManifest() {
        return resolvedManifest;
    }

    /// {@inheritDoc}
    @Override
    public Path getInstanceRoot() {
        return layout.getInstanceRoot(id);
    }

    /// {@inheritDoc}
    @Override
    public Path getInstanceJar() {
        GameInstanceManifest launchManifest = getLaunchManifest();
        GameInstanceID jarId = launchManifest.jar() == null
                ? launchManifest.id()
                : launchManifest.jar();
        return layout.getInstanceJarFile(jarId);
    }

    /// {@inheritDoc}
    ///
    /// Core instances use the repository base directory. Higher layers may provide a different
    /// per-instance policy.
    @Override
    public Path getRunDirectory() {
        return layout.getBaseDirectory();
    }
}
