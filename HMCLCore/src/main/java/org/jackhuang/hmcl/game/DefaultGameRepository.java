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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.GameJsonParseFailedEvent;
import org.jackhuang.hmcl.event.RefreshedGameInstancesEvent;
import org.jackhuang.hmcl.event.RefreshingInstancesEvent;
import org.jackhuang.hmcl.event.RemoveInstanceEvent;
import org.jackhuang.hmcl.event.RenameInstanceEvent;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Stores local game instances using immutable, atomically published snapshots.
///
/// Reads capture the current `volatile` snapshot and do not acquire a lock. Refresh, save, rename,
/// removal, and base-directory changes acquire a single mutation lock, construct replacement state,
/// and publish it with one volatile write. Existing [GameInstance] values therefore never change.
///
/// Refresh, rename, and removal events run on the mutation's calling thread. The pre- and
/// post-mutation lifecycle events are fired outside the mutation lock; listeners must provide any
/// required thread confinement themselves.
@NotNullByDefault
public class DefaultGameRepository implements GameRepository {
    /// Synthetic manifest used by the pre-version-directory Classic installation layout.
    private static final GameInstanceManifest CLASSIC_MANIFEST = new GameInstanceManifest(
            new GameInstanceID("Classic"),
            "${auth_player_name} ${auth_session} --workDir ${game_directory}",
            null,
            "net.minecraft.client.Minecraft",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                    classicLibrary("lwjgl"),
                    classicLibrary("jinput"),
                    classicLibrary("lwjgl_util")),
            null,
            null,
            null,
            ReleaseType.UNKNOWN,
            null,
            null,
            0,
            false,
            false,
            null,
            null
    );

    /// Serializes all filesystem-backed repository mutations.
    private final ReentrantLock mutationLock = new ReentrantLock();

    /// Currently published immutable repository state.
    private volatile Snapshot snapshot;

    /// Publication generation used to detect mutations while a refresh event releases the lock.
    private long snapshotGeneration;

    /// Whether a refresh has completed since construction or the last base-directory change.
    private volatile boolean loaded;

    /// Cached game versions keyed by the primary jar path used to identify them.
    private final ConcurrentHashMap<Path, Optional<String>> gameVersions = new ConcurrentHashMap<>();

    /// Creates a repository rooted at the given base directory.
    ///
    /// The repository starts with an empty snapshot. Call [#refresh()] to scan instances.
    ///
    /// @param baseDirectory the repository base directory
    public DefaultGameRepository(Path baseDirectory) {
        this.snapshot = new Snapshot(new DefaultGameRepositoryLayout(baseDirectory), Map.of());
    }

    /// Creates a Classic-layout library descriptor.
    ///
    /// @param name the file name without the `.jar` suffix
    /// @return the local Classic library descriptor
    private static Library classicLibrary(String name) {
        return new Library(new Artifact("", "", ""), null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo("bin/" + name + ".jar"), null),
                null, null, null, null, null, null);
    }

    /// Returns whether the base directory contains all required Classic libraries.
    ///
    /// @param baseDirectory the repository base directory
    /// @return whether a Classic instance can be exposed
    private static boolean hasClassicVersion(Path baseDirectory) {
        Path bin = baseDirectory.resolve("bin");
        return Files.isDirectory(bin)
                && Files.exists(bin.resolve("lwjgl.jar"))
                && Files.exists(bin.resolve("jinput.jar"))
                && Files.exists(bin.resolve("lwjgl_util.jar"));
    }

    /// {@inheritDoc}
    @Override
    public GameRepositoryLayout getLayout() {
        return snapshot.layout();
    }

    /// Returns the current repository base directory.
    ///
    /// @return the base directory of the current layout
    public Path getBaseDirectory() {
        return getLayout().getBaseDirectory();
    }

    /// Replaces the repository layout with an empty layout rooted at the given directory.
    ///
    /// This method does not scan the new directory. It atomically clears the instance index,
    /// resets [#isLoaded()], and invalidates cached game-version detection.
    ///
    /// @param baseDirectory the new repository base directory
    public void setBaseDirectory(Path baseDirectory) {
        mutationLock.lock();
        try {
            loaded = false;
            publishSnapshot(new Snapshot(new DefaultGameRepositoryLayout(baseDirectory), Map.of()));
        } finally {
            mutationLock.unlock();
        }
    }

    /// Returns whether a refresh has completed for the current base directory.
    ///
    /// @return whether the repository is loaded
    public boolean isLoaded() {
        return loaded;
    }

    /// {@inheritDoc}
    @Override
    public @Unmodifiable List<? extends GameInstance> getInstances() {
        return List.copyOf(snapshot.instances().values());
    }

    /// {@inheritDoc}
    @Override
    public Optional<? extends GameInstance> getInstance(GameInstanceID instanceId) {
        return Optional.ofNullable(snapshot.instances().get(instanceId));
    }

    /// {@inheritDoc}
    @Override
    public void refresh() {
        if (EventBus.EVENT_BUS.fireEvent(new RefreshingInstancesEvent(this)) == Event.Result.DENY) {
            return;
        }

        mutationLock.lock();
        try {
            refreshImpl();
            loaded = true;
        } finally {
            mutationLock.unlock();
        }

        EventBus.EVENT_BUS.fireEvent(new RefreshedGameInstancesEvent(this));
    }

    /// Scans the current layout and publishes the resulting snapshot.
    ///
    /// This method is invoked while the mutation lock is held. It may release and reacquire the
    /// lock while firing a manifest-correction event. Overrides must call this implementation
    /// before relying on the refreshed instance index.
    protected void refreshImpl() {
        while (true) {
            GameRepositoryLayout layout = snapshot.layout();
            long expectedGeneration = snapshotGeneration;
            @Nullable Map<GameInstanceID, GameInstanceManifest> manifests =
                    scanManifests(layout, expectedGeneration);
            if (manifests == null || snapshotGeneration != expectedGeneration) {
                continue;
            }

            publishSnapshot(buildSnapshot(layout, manifests));
            return;
        }
    }

    /// Scans stored manifests and applies the repository's on-disk compatibility corrections.
    ///
    /// @param layout             the layout being scanned
    /// @param expectedGeneration the publication generation at scan start
    /// @return manifests keyed by their corrected IDs, or `null` if the scan must restart
    private @Nullable Map<GameInstanceID, GameInstanceManifest> scanManifests(
            GameRepositoryLayout layout,
            long expectedGeneration) {
        Map<GameInstanceID, GameInstanceManifest> manifests = new TreeMap<>();

        if (hasClassicVersion(layout.getBaseDirectory())) {
            manifests.put(CLASSIC_MANIFEST.id(), CLASSIC_MANIFEST);
        }

        Path versionsDirectory = layout.getBaseDirectory().resolve("versions");
        if (!Files.isDirectory(versionsDirectory)) {
            return manifests;
        }

        try (Stream<Path> stream = Files.list(versionsDirectory)) {
            for (Path directory : stream.filter(Files::isDirectory).toList()) {
                InstanceScanResult result = scanInstanceDirectory(
                        layout,
                        directory,
                        expectedGeneration);
                if (result.restart()) {
                    return null;
                }
                if (result.manifest() != null) {
                    manifests.put(result.manifest().id(), result.manifest());
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to load versions from " + versionsDirectory, e);
        }

        return manifests;
    }

    /// Scans one instance directory, correcting its conventional file names when possible.
    ///
    /// @param layout    the layout being scanned
    /// @param directory the candidate instance directory
    /// @param expectedGeneration the publication generation at scan start
    /// @return the manifest scan result
    private InstanceScanResult scanInstanceDirectory(
            GameRepositoryLayout layout,
            Path directory,
            long expectedGeneration) {
        GameInstanceID directoryId;
        try {
            directoryId = new GameInstanceID(FileUtils.getName(directory));
        } catch (IllegalArgumentException e) {
            LOG.warning("Ignoring version folder with invalid id " + directory, e);
            return InstanceScanResult.SKIPPED;
        }

        Path json = directory.resolve(directoryId.id() + ".json");
        if (Files.notExists(json) && !restoreConventionalFileNames(directoryId, directory, json)) {
            return InstanceScanResult.SKIPPED;
        }

        GameInstanceManifest manifest;
        try {
            manifest = readInstanceManifest(json);
        } catch (Exception e) {
            LOG.warning("Malformed version json " + directoryId, e);
            @Nullable Event.Result correctionResult = fireManifestCorrectionEvent(
                    json,
                    directoryId,
                    expectedGeneration);
            if (correctionResult == null) {
                return InstanceScanResult.RESTART;
            }
            if (correctionResult != Event.Result.ALLOW) {
                return InstanceScanResult.SKIPPED;
            }

            try {
                manifest = readInstanceManifest(json);
            } catch (Exception correctedReadFailure) {
                LOG.error("User corrected version json is still malformed", correctedReadFailure);
                return InstanceScanResult.SKIPPED;
            }
        }

        if (!directoryId.equals(manifest.id())) {
            try {
                moveInstanceFiles(layout.getBaseDirectory(), directoryId, manifest.id());
            } catch (IOException e) {
                LOG.warning("Ignoring instance " + manifest.id()
                        + " because instance id does not match folder name " + directoryId
                        + ", and we cannot correct it.", e);
                return InstanceScanResult.SKIPPED;
            }
        }

        return new InstanceScanResult(manifest, false);
    }

    /// Fires a manifest-correction event without holding the repository mutation lock.
    ///
    /// The mutation lock is reacquired before this method returns. A concurrent or reentrant
    /// publication invalidates the in-progress scan.
    ///
    /// @param json               the malformed manifest file
    /// @param instanceId         the instance ID inferred from its directory
    /// @param expectedGeneration the publication generation at scan start
    /// @return the event result, or `null` if repository state changed while the lock was released
    private @Nullable Event.Result fireManifestCorrectionEvent(
            Path json,
            GameInstanceID instanceId,
            long expectedGeneration) {
        Event.Result result;
        mutationLock.unlock();
        try {
            result = EventBus.EVENT_BUS.fireEvent(
                    new GameJsonParseFailedEvent(this, json, instanceId.id()));
        } finally {
            mutationLock.lock();
        }

        return snapshotGeneration == expectedGeneration ? result : null;
    }

    /// Restores a sole JSON file and its matching jar to conventional instance file names.
    ///
    /// @param instanceId the directory's instance ID
    /// @param directory  the instance directory
    /// @param targetJson the conventional JSON path
    /// @return whether the conventional JSON file exists after this method returns
    private static boolean restoreConventionalFileNames(
            GameInstanceID instanceId,
            Path directory,
            Path targetJson) {
        List<Path> jsonFiles = FileUtils.listFilesByExtension(directory, "json");
        if (jsonFiles.size() != 1) {
            LOG.info("No available json file found, ignoring version " + instanceId);
            return false;
        }

        Path sourceJson = jsonFiles.getFirst();
        LOG.info("Renaming json file " + sourceJson + " to " + targetJson);

        try {
            Files.move(sourceJson, targetJson);
        } catch (IOException e) {
            LOG.warning("Cannot rename json file, ignoring version " + instanceId, e);
            return false;
        }

        Path sourceJar = directory.resolve(FileUtils.getNameWithoutExtension(sourceJson) + ".jar");
        if (Files.exists(sourceJar)) {
            try {
                Files.move(sourceJar, directory.resolve(instanceId.id() + ".jar"));
            } catch (IOException e) {
                LOG.warning("Cannot rename jar file, ignoring version " + instanceId, e);
                return false;
            }
        }

        return true;
    }

    /// Reads a non-null instance manifest from a JSON file.
    ///
    /// @param json the manifest file
    /// @return the parsed manifest
    /// @throws IOException        if the file cannot be read
    /// @throws JsonParseException if the file is malformed or contains JSON `null`
    private static GameInstanceManifest readInstanceManifest(Path json)
            throws IOException, JsonParseException {
        @Nullable GameInstanceManifest manifest =
                JsonUtils.fromJsonFile(json, GameInstanceManifest.class);
        if (manifest == null) {
            throw new JsonParseException("Manifest is null");
        }
        return manifest;
    }

    /// Moves an instance directory and its conventional JSON and jar files to a new ID.
    ///
    /// If renaming a file fails after the directory move, completed file moves and the directory
    /// move are rolled back on a best-effort basis before the original exception is rethrown.
    ///
    /// @param baseDirectory the repository base directory
    /// @param from          the source ID
    /// @param to            the target ID
    /// @throws IOException if a required move fails
    private static void moveInstanceFiles(
            Path baseDirectory,
            GameInstanceID from,
            GameInstanceID to) throws IOException {
        Path versionsDirectory = baseDirectory.resolve("versions");
        Path sourceDirectory = versionsDirectory.resolve(from.id());
        Path targetDirectory = versionsDirectory.resolve(to.id());
        Files.move(sourceDirectory, targetDirectory);

        Path sourceJson = targetDirectory.resolve(from.id() + ".json");
        Path sourceJar = targetDirectory.resolve(from.id() + ".jar");
        Path targetJson = targetDirectory.resolve(to.id() + ".json");
        Path targetJar = targetDirectory.resolve(to.id() + ".jar");
        boolean hasJarFile = Files.exists(sourceJar);

        try {
            Files.move(sourceJson, targetJson);
            if (hasJarFile) {
                Files.move(sourceJar, targetJar);
            }
        } catch (IOException e) {
            Lang.ignoringException(() -> Files.move(targetJson, sourceJson));
            if (hasJarFile) {
                Lang.ignoringException(() -> Files.move(targetJar, sourceJar));
            }
            Lang.ignoringException(() -> Files.move(targetDirectory, sourceDirectory));
            throw e;
        }
    }

    /// Builds a fully resolved snapshot and omits manifests that cannot currently be launched.
    ///
    /// @param layout    the snapshot layout
    /// @param manifests stored manifests keyed by ID
    /// @return the immutable resolved snapshot
    private Snapshot buildSnapshot(
            GameRepositoryLayout layout,
            Map<GameInstanceID, GameInstanceManifest> manifests) {
        @Unmodifiable Map<GameInstanceID, GameInstanceManifest> manifestCopy =
                Collections.unmodifiableMap(new TreeMap<>(manifests));
        Map<GameInstanceID, GameInstance> instances = new TreeMap<>();

        for (GameInstanceManifest manifest : manifestCopy.values()) {
            try {
                GameInstanceManifest.Resolved resolved =
                        resolveManifest(manifest, manifestCopy::get, new HashSet<>());
                if (CompatibilityRule.appliesToCurrentEnvironment(
                        resolved.launchManifest().compatibilityRules())) {
                    GameInstance instance = createGameInstance(layout, manifest, resolved);
                    instances.put(instance.getId(), instance);
                }
            } catch (NoSuchGameInstanceException e) {
                LOG.warning("Ignoring version " + manifest.id()
                        + " because it inherits from a nonexistent version.");
            }
        }

        return new Snapshot(layout, instances);
    }

    /// Creates the core immutable value stored for one resolved manifest.
    ///
    /// @param layout           the snapshot layout
    /// @param manifest         the stored manifest
    /// @param resolvedManifest the resolved manifest views
    /// @return the immutable instance value
    protected GameInstance createGameInstance(
            GameRepositoryLayout layout,
            GameInstanceManifest manifest,
            GameInstanceManifest.Resolved resolvedManifest) {
        return new DefaultGameInstance(layout, manifest, resolvedManifest);
    }

    /// Publishes a proposed snapshot after allowing subclasses to reconcile higher-level state.
    ///
    /// This method must be invoked while the mutation lock is held.
    ///
    /// @param proposedSnapshot the fully built core snapshot
    private void publishSnapshot(Snapshot proposedSnapshot) {
        Snapshot adaptedSnapshot = Objects.requireNonNull(
                onSnapshotChanged(snapshot, proposedSnapshot),
                "onSnapshotChanged returned null");

        if (!adaptedSnapshot.layout().equals(proposedSnapshot.layout())) {
            throw new IllegalStateException("Snapshot adaptation cannot replace the proposed layout");
        }
        for (Map.Entry<GameInstanceID, GameInstance> entry
                : adaptedSnapshot.instances().entrySet()) {
            if (!entry.getKey().equals(entry.getValue().getId())) {
                throw new IllegalStateException("Snapshot instance key does not match its instance ID");
            }
        }

        gameVersions.clear();
        snapshot = adaptedSnapshot;
        snapshotGeneration++;
    }

    /// Reconciles subclass state immediately before a replacement snapshot is published.
    ///
    /// This method runs with the mutation lock held. It must not fire events or expose the proposed
    /// snapshot before returning. An override may replace instance values, but it must preserve the
    /// proposed layout and map each instance under its own ID.
    ///
    /// @param previousSnapshot the currently published snapshot
    /// @param proposedSnapshot the newly built core snapshot
    /// @return the snapshot to publish
    protected Snapshot onSnapshotChanged(
            Snapshot previousSnapshot,
            Snapshot proposedSnapshot) {
        return proposedSnapshot;
    }

    /// {@inheritDoc}
    @Override
    public boolean renameInstance(GameInstanceID from, GameInstanceID to) {
        if (EventBus.EVENT_BUS.fireEvent(new RenameInstanceEvent(this, from, to))
                == Event.Result.DENY) {
            return false;
        }

        mutationLock.lock();
        try {
            Snapshot currentSnapshot = snapshot;
            @Nullable GameInstance sourceInstance =
                    currentSnapshot.instances().get(from);
            if (sourceInstance == null) {
                throw new NoSuchGameInstanceException(from);
            }

            moveInstanceFiles(currentSnapshot.layout().getBaseDirectory(), from, to);

            GameInstanceManifest renamedManifest = sourceInstance.getManifest();
            if (from.equals(renamedManifest.jar())) {
                renamedManifest = renamedManifest.withJar(null);
            }
            renamedManifest = renamedManifest.withId(to);
            JsonUtils.writeToJsonFile(currentSnapshot.layout().getInstanceJson(to), renamedManifest);

            Map<GameInstanceID, GameInstanceManifest> updatedManifests =
                    manifestsFrom(currentSnapshot);
            updatedManifests.remove(from);
            updatedManifests.put(to, renamedManifest);

            for (GameInstance instance : currentSnapshot.instances().values()) {
                GameInstanceManifest manifest = instance.getManifest();
                if (!from.equals(instance.getId()) && from.equals(manifest.inheritsFrom())) {
                    GameInstanceManifest updatedManifest = manifest.withInheritsFrom(to);
                    Path targetPath =
                            currentSnapshot.layout().getInstanceJson(updatedManifest.id());
                    Files.createDirectories(targetPath.getParent());
                    JsonUtils.writeToJsonFile(targetPath, updatedManifest);
                    updatedManifests.put(updatedManifest.id(), updatedManifest);
                }
            }

            publishSnapshot(buildSnapshot(currentSnapshot.layout(), updatedManifests));
            return true;
        } catch (IOException | JsonParseException | NoSuchGameInstanceException
                 | InvalidPathException e) {
            LOG.warning("Unable to rename version " + from + " to " + to, e);
            return false;
        } finally {
            mutationLock.unlock();
        }
    }

    /// Removes an instance from the index and then moves its directory out of the live layout.
    ///
    /// A denied removal leaves both the index and disk unchanged. Once accepted, the instance is
    /// removed from the published snapshot before filesystem deletion is attempted; a later
    /// filesystem failure does not restore the index entry.
    ///
    /// @param instanceId the instance ID
    /// @return whether the directory was absent or successfully moved out of the live layout
    public boolean removeInstanceFromDisk(GameInstanceID instanceId) {
        if (EventBus.EVENT_BUS.fireEvent(new RemoveInstanceEvent(this, instanceId))
                == Event.Result.DENY) {
            return false;
        }

        mutationLock.lock();
        try {
            Snapshot currentSnapshot = snapshot;
            Map<GameInstanceID, GameInstanceManifest> updatedManifests =
                    manifestsFrom(currentSnapshot);
            updatedManifests.remove(instanceId);
            publishSnapshot(buildSnapshot(currentSnapshot.layout(), updatedManifests));

            Path instanceDirectory = currentSnapshot.layout().getInstanceRoot(instanceId);
            if (Files.notExists(instanceDirectory)) {
                return true;
            }

            Path removedDirectory = instanceDirectory.toAbsolutePath().resolveSibling(
                    FileUtils.getName(instanceDirectory) + "_removed");
            try {
                Files.move(instanceDirectory, removedDirectory, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.warning("Unable to remove version folder: " + instanceDirectory, e);
                return false;
            }

            try {
                if (FileUtils.moveToTrash(removedDirectory)) {
                    return true;
                }

                deleteRemovedInstanceDirectory(instanceDirectory, removedDirectory);
                return true;
            } finally {
                refreshAsync().start();
            }
        } finally {
            mutationLock.unlock();
        }
    }

    /// Deletes manifest files before recursively deleting a directory that could not be trashed.
    ///
    /// Failures are logged and do not change the successful removal result because the directory is
    /// already outside the live instance layout.
    ///
    /// @param originalDirectory the original live directory used in log messages
    /// @param removedDirectory  the directory moved out of the live layout
    private static void deleteRemovedInstanceDirectory(
            Path originalDirectory,
            Path removedDirectory) {
        for (Path path : FileUtils.listFilesByExtension(removedDirectory, "json")) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                LOG.warning("Failed to delete file " + path, e);
            }
        }

        try {
            FileUtils.deleteDirectory(removedDirectory);
        } catch (IOException e) {
            LOG.warning("Unable to remove version folder: " + originalDirectory, e);
        }
    }

    /// {@inheritDoc}
    @Override
    public Optional<String> getGameVersion(GameInstanceManifest manifest) {
        Snapshot currentSnapshot = snapshot;
        try {
            GameInstanceManifest launchManifest = resolveManifest(
                    manifest,
                    id -> {
                        @Nullable GameInstance instance =
                                currentSnapshot.instances().get(id);
                        return instance == null ? null : instance.getManifest();
                    },
                    new HashSet<>()).launchManifest();
            GameInstanceID jarId =
                    Optional.ofNullable(launchManifest.jar()).orElse(launchManifest.id());
            Path instanceJar = currentSnapshot.layout().getInstanceJarFile(jarId);

            return gameVersions.computeIfAbsent(instanceJar, jar -> {
                Optional<String> gameVersion = GameVersion.minecraftVersion(jar);
                if (gameVersion.isEmpty()) {
                    LOG.warning("Cannot find out game version of " + manifest.id()
                            + ", primary jar: " + jar
                            + ", jar exists: " + Files.exists(jar));
                }
                return gameVersion;
            });
        } catch (NoSuchGameInstanceException e) {
            return Optional.empty();
        }
    }

    /// Returns the shared library path for an artifact.
    ///
    /// @param artifact the artifact coordinates
    /// @return the artifact file below the shared libraries directory
    public Path getArtifactFile(Artifact artifact) {
        return artifact.getPath(getLayout().getLibrariesDirectory());
    }

    /// {@inheritDoc}
    @Override
    public AssetIndex getAssetIndex(String assetId) throws IOException {
        try {
            return Objects.requireNonNull(JsonUtils.fromJsonFile(
                    getLayout().getAssetIndexFile(assetId),
                    AssetIndex.class));
        } catch (JsonParseException | NullPointerException e) {
            throw new IOException("Asset index file malformed", e);
        }
    }

    /// {@inheritDoc}
    @Override
    public Path getActualAssetDirectory(GameInstance instance, String assetId) {
        try {
            return reconstructAssets(instance, assetId);
        } catch (IOException | JsonParseException e) {
            LOG.error("Unable to reconstruct asset directory", e);
            return getLayout().getAssetDirectory();
        }
    }

    /// Reconstructs virtual assets and legacy resources required by an asset index.
    ///
    /// Existing targets are retained. Copies completed before an I/O failure remain on disk.
    ///
    /// @param instance the instance receiving legacy resources
    /// @param assetId  the asset index ID
    /// @return the virtual asset directory when sufficiently populated, otherwise the shared asset
    /// directory
    /// @throws IOException        if an asset file cannot be copied
    /// @throws JsonParseException if the asset index is malformed
    protected Path reconstructAssets(GameInstance instance, String assetId)
            throws IOException, JsonParseException {
        GameRepositoryLayout layout = getLayout();
        Path assetsDirectory = layout.getAssetDirectory();
        Path indexFile = layout.getAssetIndexFile(assetId);
        Path virtualRoot = assetsDirectory.resolve("virtual").resolve(assetId);

        if (!Files.isRegularFile(indexFile)) {
            return assetsDirectory;
        }

        @Nullable AssetIndex index =
                JsonUtils.fromJsonFile(indexFile, AssetIndex.class);
        if (index == null) {
            return assetsDirectory;
        }

        if (!index.isVirtual()) {
            return assetsDirectory;
        }

        Path resourcesDirectory = instance.getRunDirectory().resolve("resources");
        int existingObjects = 0;
        int totalObjects = index.getObjects().size();
        for (Map.Entry<String, AssetObject> entry : index.getObjects().entrySet()) {
            Path target = virtualRoot.resolve(entry.getKey());
            Path original = layout.getAssetObject(entry.getValue());
            if (Files.exists(original)) {
                existingObjects++;
                if (!Files.isRegularFile(target)) {
                    FileUtils.copyFile(original, target);
                }

                if (index.needMapToResources()) {
                    target = resourcesDirectory.resolve(entry.getKey());
                    if (!Files.isRegularFile(target)) {
                        FileUtils.copyFile(original, target);
                    }
                }
            }
        }

        // A mostly missing object store is characteristic of the old, non-virtual asset layout.
        return existingObjects * 10 < totalObjects ? assetsDirectory : virtualRoot;
    }

    /// Creates a task that persists a manifest and atomically replaces the repository snapshot.
    ///
    /// Resolved manifests are converted back to a patch-preserving stored representation before
    /// being written. The returned task performs all work while holding the mutation lock.
    ///
    /// @param instanceManifest the manifest to save
    /// @return a task whose result is the stored manifest
    public Task<GameInstanceManifest> saveAsync(GameInstanceManifest instanceManifest) {
        return Task.supplyAsync(() -> {
            mutationLock.lock();
            try {
                GameInstanceManifest savedManifest =
                        instanceManifest.isResolvedPreservingPatches()
                                ? MaintainTask.maintainPreservingPatches(this, instanceManifest)
                                : instanceManifest;

                Snapshot currentSnapshot = snapshot;
                Path json = currentSnapshot.layout()
                        .getInstanceJson(savedManifest.id())
                        .toAbsolutePath();
                Files.createDirectories(json.getParent());
                JsonUtils.writeToJsonFile(json, savedManifest);

                Map<GameInstanceID, GameInstanceManifest> updatedManifests =
                        manifestsFrom(currentSnapshot);
                updatedManifests.put(savedManifest.id(), savedManifest);
                publishSnapshot(buildSnapshot(currentSnapshot.layout(), updatedManifests));
                return savedManifest;
            } finally {
                mutationLock.unlock();
            }
        });
    }

    /// Returns the HMCL modpack configuration file for an instance.
    ///
    /// @param instanceId the instance ID
    /// @return the modpack configuration path
    public Path getModpackConfiguration(GameInstanceID instanceId) {
        return getLayout().getInstanceRoot(instanceId).resolve("modpack.json");
    }

    /// Reads an instance's HMCL modpack configuration when present.
    ///
    /// @param instanceId the instance ID
    /// @return the parsed configuration, or `null` if the file is absent
    /// @throws IOException                 if the file cannot be read
    /// @throws NoSuchGameInstanceException if the instance is absent
    public @Nullable ModpackConfiguration<?> readModpackConfiguration(
            GameInstanceID instanceId) throws IOException, NoSuchGameInstanceException {
        if (!hasInstance(instanceId)) {
            throw new NoSuchGameInstanceException(instanceId);
        }
        Path file = getModpackConfiguration(instanceId);
        if (Files.notExists(file)) {
            return null;
        }
        return JsonUtils.fromJsonFile(file, ModpackConfiguration.class);
    }

    /// Returns whether an instance has an HMCL modpack configuration file.
    ///
    /// @param instanceId the instance ID
    /// @return whether the modpack configuration exists
    public boolean isModpack(GameInstanceID instanceId) {
        return Files.exists(getModpackConfiguration(instanceId));
    }

    /// {@inheritDoc}
    @Override
    public GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest)
            throws NoSuchGameInstanceException {
        Snapshot currentSnapshot = snapshot;
        return resolveManifest(
                manifest,
                id -> {
                    @Nullable GameInstance instance =
                            currentSnapshot.instances().get(id);
                    return instance == null ? null : instance.getManifest();
                },
                new HashSet<>());
    }

    /// Resolves one manifest using the supplied immutable parent lookup.
    ///
    /// Circular inheritance is cut at the first repeated manifest and logged. A missing parent
    /// fails the whole resolution.
    ///
    /// @param manifest      the manifest to resolve
    /// @param manifestLookup lookup for inherited manifests
    /// @param resolvedSoFar IDs already visited in this resolution
    /// @return resolved launch and standalone views
    /// @throws NoSuchGameInstanceException if an inherited manifest is absent
    private static GameInstanceManifest.Resolved resolveManifest(
            GameInstanceManifest manifest,
            ManifestLookup manifestLookup,
            Set<GameInstanceID> resolvedSoFar) throws NoSuchGameInstanceException {
        GameInstanceManifest launchManifest;
        GameInstanceManifest standaloneManifest = manifest.isRoot()
                ? manifest
                : addPatches(
                        addPatches(
                                new GameInstanceManifest(manifest.id()),
                                List.of(manifest.toPatch())),
                        manifest.patches());

        if (manifest.inheritsFrom() == null) {
            if (manifest.isRoot()) {
                // Preserve current compatibility for externally installed root manifests.
                launchManifest = manifest.patches() != null
                        ? new GameInstanceManifest(manifest.id()).withPatches(manifest.patches())
                        : manifest;
            } else {
                launchManifest = manifest;
            }
            launchManifest = launchManifest.withJar(
                    manifest.jar() == null ? manifest.id() : manifest.jar());
        } else if (!resolvedSoFar.add(manifest.id())) {
            LOG.warning("Found circular dependency versions: " + resolvedSoFar);
            launchManifest = (manifest.jar() == null
                    ? manifest.withJar(manifest.id())
                    : manifest).withInheritsFrom(null);
        } else {
            @Nullable GameInstanceManifest parentManifest =
                    manifestLookup.find(manifest.inheritsFrom());
            if (parentManifest == null) {
                throw new NoSuchGameInstanceException(manifest.inheritsFrom());
            }

            GameInstanceManifest.Resolved parentResolved =
                    resolveManifest(parentManifest, manifestLookup, resolvedSoFar);
            launchManifest = manifest.merge(parentResolved.launchManifest());
            standaloneManifest = addPatches(
                    addPatches(
                            parentResolved.standaloneManifest(),
                            Collections.singleton(manifest.toPatch())),
                    manifest.patches());
        }

        if (manifest.patches() != null && !manifest.patches().isEmpty()) {
            @Unmodifiable List<GameInstancePatch> sortedPatches =
                    manifest.patches().stream()
                            .sorted(Comparator.comparing(GameInstancePatch::getPriority))
                            .toList();
            for (GameInstancePatch patch : sortedPatches) {
                launchManifest = patch.merge(launchManifest);
            }
        }

        launchManifest = launchManifest.withId(manifest.id()).withPatches(null);
        standaloneManifest = standaloneManifest.withId(manifest.id());
        if (launchManifest.jar() != null) {
            standaloneManifest = standaloneManifest.withJar(launchManifest.jar());
        }

        return new GameInstanceManifest.Resolved(
                manifest,
                launchManifest,
                standaloneManifest);
    }

    /// Adds patches while replacing existing identified patches with matching IDs.
    ///
    /// @param manifest   the manifest to update
    /// @param additional additional patches, or `null`
    /// @return the original manifest when no patches are supplied, otherwise an updated manifest
    private static GameInstanceManifest addPatches(
            GameInstanceManifest manifest,
            @Nullable Collection<GameInstancePatch> additional) {
        if (additional == null || additional.isEmpty()) {
            return manifest;
        }

        Set<String> patchIds = new HashSet<>();
        for (GameInstancePatch patch : additional) {
            if (patch.id() != null) {
                patchIds.add(patch.id());
            }
        }

        List<GameInstancePatch> patches = new ArrayList<>();
        if (manifest.patches() != null) {
            for (GameInstancePatch patch : manifest.patches()) {
                if (patch.id() == null || !patchIds.contains(patch.id())) {
                    patches.add(patch);
                }
            }
        }
        patches.addAll(additional);
        return manifest.withPatches(patches);
    }

    /// Copies stored manifests from a snapshot into a mutable sorted map.
    ///
    /// @param sourceSnapshot the source snapshot
    /// @return a mutable map keyed by instance ID
    private static Map<GameInstanceID, GameInstanceManifest> manifestsFrom(
            Snapshot sourceSnapshot) {
        Map<GameInstanceID, GameInstanceManifest> manifests = new TreeMap<>();
        for (GameInstance instance : sourceSnapshot.instances().values()) {
            manifests.put(instance.getId(), instance.getManifest());
        }
        return manifests;
    }

    /// Looks up a manifest during inheritance resolution.
    @FunctionalInterface
    private interface ManifestLookup {
        /// Returns a stored manifest by ID.
        ///
        /// @param instanceId the parent instance ID
        /// @return the stored manifest, or `null` when absent
        @Nullable GameInstanceManifest find(GameInstanceID instanceId);
    }

    /// Result of scanning one instance directory.
    ///
    /// @param manifest the parsed manifest, or `null` when the directory should be skipped
    /// @param restart  whether the entire repository scan must restart
    @NotNullByDefault
    private record InstanceScanResult(
            @Nullable GameInstanceManifest manifest,
            boolean restart) {
        /// Shared result for a directory that should be ignored.
        private static final InstanceScanResult SKIPPED =
                new InstanceScanResult(null, false);

        /// Shared result requesting a complete scan restart.
        private static final InstanceScanResult RESTART =
                new InstanceScanResult(null, true);
    }

    /// Immutable repository state published as one volatile value.
    ///
    /// @param layout    the path layout used by every instance in this snapshot
    /// @param instances an unmodifiable, ID-sorted map of immutable instance values
    @NotNullByDefault
    protected record Snapshot(
            GameRepositoryLayout layout,
            @Unmodifiable Map<GameInstanceID, GameInstance> instances) {
        /// Creates a snapshot with a defensive sorted copy of the instance map.
        protected Snapshot {
            Objects.requireNonNull(layout);
            instances = Collections.unmodifiableMap(new TreeMap<>(instances));
        }
    }
}
