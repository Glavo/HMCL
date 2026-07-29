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

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Provides indexed access, lifecycle operations, and shared-file I/O for local game instances.
///
/// Repository state is exposed as immutable snapshots. A call to [#getInstances()] and each
/// instance returned by that snapshot remain safe to read after a later mutation, but a subsequent
/// call may return replacement instance values. Implementations must serialize state mutations and
/// publish a complete new snapshot atomically; readers must never observe a partially updated index.
///
/// Repository lifecycle events are fired by the thread performing the mutation and outside the
/// repository's mutation lock. Event listeners must arrange any required thread confinement.
@NotNullByDefault
public interface GameRepository {
    /// Returns the immutable path layout of the current repository snapshot.
    ///
    /// The returned layout may be replaced when the repository's base directory changes.
    ///
    /// @return the current repository layout
    GameRepositoryLayout getLayout();

    /// Returns all instances in the current snapshot.
    ///
    /// The list and its instance values remain valid after later repository mutations.
    ///
    /// @return an unmodifiable snapshot list of instances
    @Unmodifiable List<? extends GameInstance> getInstances();

    /// Returns an instance from the current snapshot.
    ///
    /// @param instanceId the instance ID
    /// @return the instance, or empty if the current snapshot does not contain the ID
    Optional<? extends GameInstance> getInstance(GameInstanceID instanceId);

    /// Resolves inheritance into launch and standalone manifest views.
    ///
    /// @param manifest the manifest to resolve
    /// @return the resolved manifest view
    /// @throws NoSuchGameInstanceException if a referenced parent is absent from the current snapshot
    GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest)
            throws NoSuchGameInstanceException;

    /// Returns whether the instance exists in the current repository index.
    ///
    /// @param instanceId the instance ID
    /// @return whether the instance exists
    default boolean hasInstance(GameInstanceID instanceId) {
        return getInstance(instanceId).isPresent();
    }

    /// Returns the number of instances in the current snapshot.
    ///
    /// @return the loaded instance count
    default int getInstanceCount() {
        return getInstances().size();
    }

    /// Reloads repository state from the backing storage.
    ///
    /// If a pre-refresh event denies the operation, the current snapshot is retained. Otherwise,
    /// one complete new snapshot is published before the post-refresh event is fired.
    void refresh();

    /// Creates a task that reloads repository state from the backing storage.
    ///
    /// @return a task that calls [#refresh()]
    default Task<Void> refreshAsync() {
        return Task.runAsync(this::refresh);
    }

    /// Returns the working directory used when launching an instance.
    ///
    /// Missing IDs use the repository base directory so callers can compute a safe fallback while
    /// an instance is being created.
    ///
    /// @param instanceId the instance ID
    /// @return the instance run directory, or the base directory if the instance is absent
    default Path getRunDirectory(GameInstanceID instanceId) {
        return getInstance(instanceId)
                .map(GameInstance::getRunDirectory)
                .orElseGet(() -> getLayout().getBaseDirectory());
    }

    /// Returns the primary client jar path for an instance.
    ///
    /// The `jar` ID of the resolved launch manifest is used when present; otherwise the launch
    /// manifest's own ID is used.
    ///
    /// @param instance the instance whose jar should be located
    /// @return the primary client jar path
    default Path getInstanceJar(GameInstance instance) {
        return instance.getInstanceJar();
    }

    /// Detects the Minecraft game version associated with a manifest.
    ///
    /// @param manifest the manifest to inspect
    /// @return the detected Minecraft game version, or empty if it cannot be determined
    Optional<String> getGameVersion(GameInstanceManifest manifest);

    /// Renames an instance and updates repository-managed references.
    ///
    /// @param from the current instance ID
    /// @param to   the target instance ID
    /// @return whether the instance was renamed
    boolean renameInstance(GameInstanceID from, GameInstanceID to);

    /// Returns the asset directory that should be used at launch time.
    ///
    /// Asset reconstruction may copy object files into virtual or legacy resource directories. If
    /// reconstruction fails, this method returns the shared asset directory.
    ///
    /// @param instance the instance whose run directory receives any legacy resources
    /// @param assetId  the asset index ID
    /// @return the reconstructed virtual directory or the shared asset directory
    Path getActualAssetDirectory(GameInstance instance, String assetId);

    /// Returns an existing asset object path by logical asset name.
    ///
    /// @param assetId the asset index ID
    /// @param name    the logical asset name
    /// @return the asset object path, or empty if the name is absent from the asset index
    /// @throws IOException if the asset index cannot be read or the referenced object is invalid
    default Optional<Path> getAssetObject(String assetId, String name) throws IOException {
        try {
            @Nullable AssetObject assetObject =
                    getAssetIndex(assetId).getObjects().get(name);
            return assetObject == null
                    ? Optional.empty()
                    : Optional.of(getLayout().getAssetObject(assetObject));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "Unrecognized asset object " + name + " in asset " + assetId,
                    e);
        }
    }

    /// Reads an asset index.
    ///
    /// @param assetId the asset index ID
    /// @return the asset index
    /// @throws IOException if the index file is missing or malformed
    AssetIndex getAssetIndex(String assetId) throws IOException;

    /// Returns the classpath entries whose library files are present on disk.
    ///
    /// @param manifest the manifest whose libraries should be mapped to classpath entries
    /// @return a new mutable set of absolute classpath entries for existing non-native libraries
    default Set<String> getClasspath(GameInstanceManifest manifest) {
        Set<String> classpath = new LinkedHashSet<>();
        if (manifest.libraries() != null) {
            for (Library library : manifest.libraries()) {
                if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                    Path file = getLayout().getLibraryFile(manifest.id(), library);
                    if (Files.isRegularFile(file)) {
                        classpath.add(FileUtils.getAbsolutePath(file));
                    }
                }
            }
        }

        return classpath;
    }
}
