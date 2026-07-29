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

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.setting.DefaultIsolationType;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.LegacyGameSettingsMigrator;
import org.jackhuang.hmcl.setting.SettingFileUtils;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Provides HMCL's stable identity and settings state for one game instance ID.
///
/// The object identity remains stable across ordinary repository refreshes. Its `volatile` core
/// instance reference is replaced with the latest immutable repository value before a refreshed
/// snapshot is published. Manifest and path reads therefore require no lock.
///
/// Settings initialization, read-only state, replacement, and save preparation are serialized by
/// an instance-local lock. The returned [GameSettings.Instance] remains mutable; callers must obey
/// the threading requirements of its observable properties. Settings objects are retained across
/// refreshes and are loaded only when this stable identity is first created.
@NotNullByDefault
public final class HMCLGameInstance implements GameInstance {
    /// Directory below the instance root that stores HMCL-managed metadata.
    private static final String INSTANCE_METADATA_DIRECTORY = ".hmcl";

    /// Directory below HMCL metadata that stores user-editable instance configuration.
    private static final String INSTANCE_CONFIG_DIRECTORY = "config";

    /// Directory below HMCL metadata that stores launcher-owned instance state.
    private static final String INSTANCE_STATE_DIRECTORY = "state";

    /// File name for instance-specific game settings.
    private static final String INSTANCE_GAME_SETTINGS_FILENAME = "instance-game-settings.json";

    /// Repository that owns this stable identity.
    private final HMCLGameRepository repository;

    /// ID represented by this stable identity.
    private final GameInstanceID id;

    /// Latest immutable core instance snapshot.
    private volatile GameInstance base;

    /// Whether an in-progress installation must force the instance-root run directory.
    private volatile boolean beingModpack;

    /// Serializes settings lifecycle state.
    private final Object settingsLock = new Object();

    /// Loaded instance settings, or `null` when the instance has no local settings.
    private @Nullable GameSettings.Instance settings;

    /// Whether the settings file and legacy migration have been checked.
    private boolean settingsLoaded;

    /// Whether the current settings file must be preserved without automatic writes.
    private boolean settingsReadOnly;

    /// Whether this identity has been explicitly detached after instance removal.
    private volatile boolean detached;

    /// Creates and immediately loads settings for a stable instance identity.
    ///
    /// @param repository the owning HMCL repository
    /// @param base       the initial immutable core instance
    HMCLGameInstance(HMCLGameRepository repository, GameInstance base) {
        this.repository = Objects.requireNonNull(repository);
        this.base = Objects.requireNonNull(base);
        this.id = base.getId();
        getGameSettings();
    }

    /// Returns the owning HMCL repository.
    ///
    /// @return the owning repository
    public HMCLGameRepository getRepository() {
        return repository;
    }

    /// Replaces the delegated core instance after validating its ID.
    ///
    /// @param newBase the latest immutable core instance
    /// @throws IllegalArgumentException if the instance ID differs
    void updateBase(GameInstance newBase) {
        if (!id.equals(newBase.getId())) {
            throw new IllegalArgumentException("Cannot replace an instance with a different ID");
        }
        base = newBase;
        detached = false;
    }

    /// Marks this identity as explicitly removed from its repository.
    void detach() {
        detached = true;
        beingModpack = false;
    }

    /// Returns whether this identity was explicitly removed.
    ///
    /// @return whether the identity is detached
    boolean isDetached() {
        return detached;
    }

    /// {@inheritDoc}
    @Override
    public GameInstanceID getId() {
        return id;
    }

    /// {@inheritDoc}
    @Override
    public GameInstanceManifest getManifest() {
        return base.getManifest();
    }

    /// {@inheritDoc}
    @Override
    public GameInstanceManifest.Resolved getResolvedManifest() {
        return base.getResolvedManifest();
    }

    /// {@inheritDoc}
    @Override
    public Path getInstanceRoot() {
        return base.getInstanceRoot();
    }

    /// {@inheritDoc}
    @Override
    public Path getInstanceJar() {
        return base.getInstanceJar();
    }

    /// {@inheritDoc}
    ///
    /// An instance being installed as a modpack, or one with an existing modpack configuration,
    /// always uses its instance root. Otherwise an explicit local running-directory override wins,
    /// followed by the parent preset and finally the core repository's base-directory policy.
    @Override
    public Path getRunDirectory() {
        if (beingModpack || isModpack()) {
            return getInstanceRoot();
        }

        @Nullable GameSettings.Instance localSettings = getGameSettings();
        boolean useLocalRunningDirectory = localSettings != null
                && localSettings.getOverrideProperties()
                .contains(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        String runningDirectory =
                getSelectedRunningDirectory(localSettings, useLocalRunningDirectory);

        if (StringUtils.isBlank(runningDirectory)) {
            return useLocalRunningDirectory ? getInstanceRoot() : base.getRunDirectory();
        }

        try {
            return Path.of(runningDirectory);
        } catch (InvalidPathException ignored) {
            return getInstanceRoot();
        }
    }

    /// Selects the running-directory string from local settings or the effective parent preset.
    ///
    /// @param localSettings           the local settings, or `null`
    /// @param useLocalRunningDirectory whether the local override is enabled
    /// @return the selected string, with `null` property values converted to an empty string
    private String getSelectedRunningDirectory(
            @Nullable GameSettings.Instance localSettings,
            boolean useLocalRunningDirectory) {
        if (useLocalRunningDirectory) {
            if (localSettings == null) {
                return "";
            }
            return Objects.requireNonNullElse(
                    localSettings.runningDirectoryProperty().getValue(),
                    "");
        }

        return Objects.requireNonNullElse(
                repository.getParentGameSettings(localSettings)
                        .runningDirectoryProperty()
                        .getValue(),
                "");
    }

    /// Returns the HMCL-managed metadata directory.
    ///
    /// @return the `.hmcl` directory below the instance root
    public Path getMetadataDirectory() {
        return getInstanceRoot().resolve(INSTANCE_METADATA_DIRECTORY);
    }

    /// Returns the HMCL-managed configuration directory.
    ///
    /// @return the configuration directory below instance metadata
    public Path getConfigDirectory() {
        return getMetadataDirectory().resolve(INSTANCE_CONFIG_DIRECTORY);
    }

    /// Returns the HMCL-managed state directory.
    ///
    /// @return the state directory below instance metadata
    public Path getStateDirectory() {
        return getMetadataDirectory().resolve(INSTANCE_STATE_DIRECTORY);
    }

    /// Returns the current instance settings file.
    ///
    /// @return the instance settings path
    private Path getGameSettingsFile() {
        return getConfigDirectory().resolve(INSTANCE_GAME_SETTINGS_FILENAME);
    }

    /// Returns the loaded local settings, loading or migrating them on first access.
    ///
    /// @return the local settings, or `null` when no local settings exist
    public @Nullable GameSettings.Instance getGameSettings() {
        synchronized (settingsLock) {
            ensureGameSettingsLoaded();
            return settings;
        }
    }

    /// Loads the local settings file and performs legacy migration once.
    ///
    /// This method must be called while holding `settingsLock`.
    private void ensureGameSettingsLoaded() {
        if (settingsLoaded) {
            return;
        }
        settingsLoaded = true;

        InstanceGameSettingsLoadResult result = loadGameSettingsFile(getGameSettingsFile());
        if (result.settings() != null) {
            initializeGameSettingsLocked(result.settings(), result.allowSave());
            return;
        }
        if (!result.allowSave()) {
            settingsReadOnly = true;
            return;
        }

        @Nullable GameSettingsPresetID legacyParent =
                repository.getGameDirectory().getLegacyGameSettings();
        if (SettingsManager.getGameSettings(legacyParent) == null) {
            legacyParent = null;
        }

        @Nullable LegacyGameSettingsMigrator.InstanceMigrationResult migrationResult =
                LegacyGameSettingsMigrator.migrateInstanceGameSettings(
                        this,
                        legacyParent);
        if (migrationResult == null) {
            return;
        }

        initializeGameSettingsLocked(migrationResult.setting(), true);
        try {
            saveGameSettingsSyncLocked();
            migrationResult.saveReceipt();
        } catch (IOException e) {
            LOG.warning("Failed to save migrated instance game settings for " + id, e);
        }
    }

    /// Loads and schema-checks a current-format instance settings file.
    ///
    /// @param file the settings file
    /// @return the loaded settings and whether future writes are allowed
    private static InstanceGameSettingsLoadResult loadGameSettingsFile(Path file) {
        if (!Files.exists(file)) {
            return new InstanceGameSettingsLoadResult(null, true);
        }

        try {
            @Nullable JsonObject jsonObject = JsonUtils.fromJsonFile(
                    LauncherSettings.SETTINGS_GSON,
                    file,
                    JsonObject.class);
            if (jsonObject == null) {
                LOG.warning("Instance game settings are empty: " + file);
                return new InstanceGameSettingsLoadResult(new GameSettings.Instance(), true);
            }

            JsonSchema.CompatibilityResult schemaResult =
                    JsonSchema.check(jsonObject, GameSettings.Instance.CURRENT_SCHEMA);
            switch (schemaResult.status()) {
                case MISSING -> LOG.warning("Missing schema in instance game settings: " + file);
                case INVALID -> LOG.warning("Invalid schema in instance game settings: "
                        + file + ", Actual: " + schemaResult.invalidValue());
                case UNPARSEABLE -> LOG.warning("Unparseable schema in instance game settings: "
                        + file + ", Actual: " + schemaResult.actual());
                case UNEXPECTED_ID -> LOG.warning(
                        "Unexpected instance game settings schema. Expected: "
                                + GameSettings.Instance.CURRENT_SCHEMA
                                + ", Actual: "
                                + schemaResult.actual());
                case UNSUPPORTED_MAJOR, READ_ONLY_PRESERVE_SCHEMA -> LOG.warning(
                        "Unsupported instance game settings schema. Expected: "
                                + GameSettings.Instance.CURRENT_SCHEMA
                                + ", Actual: "
                                + schemaResult.actual());
                case READ_WRITE, READ_WRITE_PRESERVE_SCHEMA -> {
                }
            }

            if (!schemaResult.readable()) {
                GameSettings.Instance fallback = new GameSettings.Instance();
                fallback.setSavable(false);
                return new InstanceGameSettingsLoadResult(fallback, false);
            }

            @Nullable GameSettings.Instance loadedSettings =
                    LauncherSettings.SETTINGS_GSON.fromJson(
                            jsonObject,
                            GameSettings.Instance.class);
            if (loadedSettings == null) {
                LOG.warning("Instance game settings deserialized to null: " + file);
                GameSettings.Instance fallback = new GameSettings.Instance();
                fallback.setBackupOnNextSave(true);
                return new InstanceGameSettingsLoadResult(fallback, true);
            }

            if (!schemaResult.preserveSchema()
                    && !GameSettings.Instance.CURRENT_SCHEMA.equals(loadedSettings.getSchema())) {
                loadedSettings.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
            }
            return new InstanceGameSettingsLoadResult(
                    loadedSettings,
                    schemaResult.allowSave());
        } catch (JsonParseException e) {
            LOG.warning("Failed to parse game setting " + file, e);
            GameSettings.Instance fallback = new GameSettings.Instance();
            fallback.setBackupOnNextSave(true);
            return new InstanceGameSettingsLoadResult(fallback, true);
        } catch (Exception e) {
            LOG.warning("Failed to load game setting " + file, e);
            return new InstanceGameSettingsLoadResult(null, false);
        }
    }

    /// Creates local settings when the instance is present and writable.
    ///
    /// @return the existing or newly created settings, or `null` if creation is not allowed
    public @Nullable GameSettings.Instance createGameSettings() {
        synchronized (settingsLock) {
            ensureGameSettingsLoaded();
            if (!repository.hasInstance(id) || settingsReadOnly) {
                return null;
            }
            if (settings != null) {
                return settings;
            }
            return initializeGameSettingsLocked(new GameSettings.Instance(), true);
        }
    }

    /// Returns local settings, creating them when allowed.
    ///
    /// @return the existing or newly created settings, or `null` if creation is not allowed
    public @Nullable GameSettings.Instance getOrCreateGameSettings() {
        @Nullable GameSettings.Instance currentSettings = getGameSettings();
        return currentSettings != null ? currentSettings : createGameSettings();
    }

    /// Replaces this identity's local settings and enables automatic saves.
    ///
    /// This package-private operation supports settings prepared before a new manifest is published.
    ///
    /// @param newSettings the settings to install
    /// @return the installed settings
    GameSettings.Instance initializeGameSettings(GameSettings.Instance newSettings) {
        synchronized (settingsLock) {
            settingsLoaded = true;
            return initializeGameSettingsLocked(newSettings, true);
        }
    }

    /// Installs settings and their save listener.
    ///
    /// This method must be called while holding `settingsLock`.
    ///
    /// @param newSettings the settings to install
    /// @param allowSave   whether automatic saves are allowed
    /// @return the installed settings
    private GameSettings.Instance initializeGameSettingsLocked(
            GameSettings.Instance newSettings,
            boolean allowSave) {
        normalizeRunningDirectoryOverride(newSettings);
        newSettings.setSavable(allowSave);
        settings = newSettings;
        settingsLoaded = true;
        settingsReadOnly = !allowSave;
        if (allowSave) {
            newSettings.addListener(event -> saveGameSettings());
        }
        return newSettings;
    }

    /// Keeps legacy custom running directories enabled under property-source selection.
    ///
    /// @param gameSettings the settings being initialized
    private static void normalizeRunningDirectoryOverride(GameSettings.Instance gameSettings) {
        if (StringUtils.isNotBlank(gameSettings.runningDirectoryProperty().getValue())) {
            gameSettings.getOverrideProperties()
                    .add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        }
    }

    /// Returns whether the settings file is loaded in preservation-only mode.
    ///
    /// @return whether normal writes are disabled
    public boolean isGameSettingsReadOnly() {
        synchronized (settingsLock) {
            ensureGameSettingsLoaded();
            return settingsReadOnly;
        }
    }

    /// Backs up an incompatible settings file and enables writes using the current schema.
    public void forceOverwriteGameSettings() {
        synchronized (settingsLock) {
            ensureGameSettingsLoaded();
            if (settings == null) {
                settings = new GameSettings.Instance();
            }

            boolean installAutoSave = !settings.isSavable();
            Path file = getGameSettingsFile().toAbsolutePath().normalize();
            SettingFileUtils.backupInvalidConfig(file);
            settings.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
            settings.setSavable(true);
            settings.setBackupOnNextSave(false);
            settingsReadOnly = false;
            saveGameSettingsLocked();
            if (installAutoSave) {
                settings.addListener(event -> saveGameSettings());
            }
        }
    }

    /// Returns settings resolved against the selected parent preset.
    ///
    /// @return the effective game settings
    public GameSettings.Effective getEffectiveGameSettings() {
        @Nullable GameSettings.Instance localSettings = getGameSettings();
        return GameSettings.resolve(
                repository.getParentGameSettings(localSettings),
                localSettings);
    }

    /// Copies local settings or materializes the effective parent reference for duplication.
    ///
    /// @return an independent settings object for a new instance
    GameSettings.Instance copyGameSettings() {
        @Nullable GameSettings.Instance localSettings = getGameSettings();
        if (localSettings != null) {
            return JsonUtils.clone(
                    LauncherSettings.SETTINGS_GSON,
                    localSettings,
                    TypeToken.get(GameSettings.Instance.class));
        }

        GameSettings.Instance copied = new GameSettings.Instance();
        copied.parentProperty().setValue(
                getEffectiveGameSettings().getPreset().idProperty().getValue());
        return copied;
    }

    /// Applies the parent preset's default isolation rule to this existing instance.
    public void applyDefaultIsolationSetting() {
        @Nullable GameSettings.Instance localSettings = getGameSettings();
        GameSettings.Preset preset = repository.getParentGameSettings(localSettings);
        DefaultIsolationType isolationType = Lang.requireNonNullElse(
                preset.defaultIsolationTypeProperty().getValue(),
                DefaultIsolationType.MODDED);
        boolean isolated = switch (isolationType) {
            case NEVER -> false;
            case ALWAYS -> true;
            case MODDED -> LibraryAnalyzer.isModded(getResolvedManifest());
        };

        if (isolated) {
            @Nullable GameSettings.Instance settingsToUpdate =
                    localSettings != null ? localSettings : getOrCreateGameSettings();
            if (settingsToUpdate != null
                    && settingsToUpdate.getOverrideProperties()
                    .add(GameSettings.PROPERTY_RUNNING_DIRECTORY)) {
                saveGameSettings();
            }
        }
    }

    /// Applies a precomputed default-isolation decision before the instance is published.
    ///
    /// @param isolated whether the new instance should be isolated
    void applyDefaultIsolationSettingForNewInstance(boolean isolated) {
        if (!isolated) {
            return;
        }

        synchronized (settingsLock) {
            ensureGameSettingsLoaded();
            if (settingsReadOnly) {
                return;
            }
            if (settings == null) {
                initializeGameSettingsLocked(new GameSettings.Instance(), true);
            }
            if (settings.getOverrideProperties()
                    .add(GameSettings.PROPERTY_RUNNING_DIRECTORY)) {
                saveGameSettingsLocked();
            }
        }
    }

    /// Queues a safe save of writable local settings.
    public void saveGameSettings() {
        synchronized (settingsLock) {
            saveGameSettingsLocked();
        }
    }

    /// Prepares and queues a safe settings save.
    ///
    /// This method must be called while holding `settingsLock`.
    private void saveGameSettingsLocked() {
        if (detached || settings == null || settingsReadOnly) {
            return;
        }

        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOG.warning("Failed to create directory: " + file.getParent(), e);
        }

        if (settings.isBackupOnNextSave()) {
            settings.setBackupOnNextSave(false);
            SettingFileUtils.backupInvalidConfig(file);
        }
        FileSaver.save(file, LauncherSettings.SETTINGS_GSON.toJson(settings));
    }

    /// Saves writable local settings synchronously.
    ///
    /// @throws IOException if the file cannot be written safely
    void saveGameSettingsSync() throws IOException {
        synchronized (settingsLock) {
            saveGameSettingsSyncLocked();
        }
    }

    /// Performs a synchronous settings save while holding `settingsLock`.
    ///
    /// @throws IOException if the file cannot be written safely
    private void saveGameSettingsSyncLocked() throws IOException {
        if (detached || settings == null || settingsReadOnly) {
            return;
        }

        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        if (settings.isBackupOnNextSave()) {
            settings.setBackupOnNextSave(false);
            SettingFileUtils.backupInvalidConfig(file);
        }
        FileUtils.saveSafely(file, LauncherSettings.SETTINGS_GSON.toJson(settings));
    }

    /// Returns whether this instance has a modpack configuration or is being installed as one.
    ///
    /// @return whether modpack run-directory rules apply
    public boolean isModpack() {
        return beingModpack || Files.exists(getModpackConfiguration());
    }

    /// Returns the HMCL modpack configuration path.
    ///
    /// @return the `modpack.cfg` path
    public Path getModpackConfiguration() {
        return getInstanceRoot().resolve("modpack.cfg");
    }

    /// Sets whether an in-progress modpack installation is using this identity.
    ///
    /// @param value whether installation is in progress
    void setBeingModpack(boolean value) {
        beingModpack = value;
    }

    /// Returns an existing custom icon file.
    ///
    /// @return the first supported icon path, or empty when none exists
    public Optional<Path> getIconFile() {
        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = getInstanceRoot().resolve("icon." + extension);
            if (Files.exists(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /// Replaces the custom icon file.
    ///
    /// @param iconFile the source image
    /// @throws IOException              if the image cannot be copied
    /// @throws IllegalArgumentException if its extension is unsupported
    public void setIconFile(Path iconFile) throws IOException {
        String extension = FileUtils.getExtension(iconFile).toLowerCase(Locale.ROOT);
        if (!FXUtils.IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported icon file: " + extension);
        }

        deleteIconFile();
        FileUtils.copyFile(
                iconFile,
                getInstanceRoot().resolve("icon." + extension));
    }

    /// Deletes all supported custom icon files.
    public void deleteIconFile() {
        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = getInstanceRoot().resolve("icon." + extension);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete icon file: " + file, e);
            }
        }
    }

    /// Returns the configured, custom, or inferred icon image for this instance.
    ///
    /// @return the instance icon
    public Image getIconImage() {
        @Nullable GameSettings.Instance localSettings = getGameSettings();
        GameInstanceIconType iconType = localSettings != null
                ? Lang.requireNonNullElse(
                        localSettings.iconProperty().getValue(),
                        GameInstanceIconType.DEFAULT)
                : GameInstanceIconType.DEFAULT;
        if (iconType != GameInstanceIconType.DEFAULT) {
            return iconType.getIcon();
        }

        Optional<Path> iconFile = getIconFile();
        if (iconFile.isPresent()) {
            try {
                return FXUtils.loadImage(iconFile.get(), 64, 64, true, true);
            } catch (Exception e) {
                LOG.warning("Failed to load instance icon of " + id, e);
            }
        }

        GameInstanceManifest.Resolved resolvedManifest = getResolvedManifest();
        if (LibraryAnalyzer.isModded(resolvedManifest)) {
            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolvedManifest, null);
            if (analyzer.has(LibraryAnalyzer.LibraryType.FABRIC)) {
                return GameInstanceIconType.FABRIC.getIcon();
            } else if (analyzer.has(LibraryAnalyzer.LibraryType.QUILT)) {
                return GameInstanceIconType.QUILT.getIcon();
            } else if (analyzer.has(LibraryAnalyzer.LibraryType.LEGACY_FABRIC)) {
                return GameInstanceIconType.LEGACY_FABRIC.getIcon();
            } else if (analyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE)) {
                return GameInstanceIconType.NEO_FORGE.getIcon();
            } else if (analyzer.has(LibraryAnalyzer.LibraryType.FORGE)) {
                return GameInstanceIconType.FORGE.getIcon();
            } else if (analyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM)) {
                return GameInstanceIconType.CLEANROOM.getIcon();
            } else if (analyzer.has(LibraryAnalyzer.LibraryType.LITELOADER)) {
                return GameInstanceIconType.CHICKEN.getIcon();
            } else if (analyzer.has(LibraryAnalyzer.LibraryType.OPTIFINE)) {
                return GameInstanceIconType.OPTIFINE.getIcon();
            }
        }

        @Nullable String gameVersion = repository.getGameVersion(
                resolvedManifest.launchManifest()).orElse(null);
        if (gameVersion != null) {
            GameVersionNumber versionNumber = GameVersionNumber.asGameVersion(gameVersion);
            if (versionNumber.isAprilFools()) {
                return GameInstanceIconType.APRIL_FOOLS.getIcon();
            } else if (versionNumber instanceof GameVersionNumber.LegacySnapshot) {
                return GameInstanceIconType.COMMAND.getIcon();
            } else if (versionNumber instanceof GameVersionNumber.Old) {
                return GameInstanceIconType.CRAFT_TABLE.getIcon();
            }
        }
        return GameInstanceIconType.GRASS.getIcon();
    }

    /// Marks the previous launch as abnormal by creating the instance marker file.
    public void markLaunchedAbnormally() {
        try {
            Files.createFile(getInstanceRoot().resolve(".abnormal"));
        } catch (IOException ignored) {
        }
    }

    /// Removes the abnormal-launch marker when present.
    ///
    /// @return whether a regular marker file was present
    public boolean unmarkLaunchedAbnormally() {
        Path file = getInstanceRoot().resolve(".abnormal");
        if (!Files.isRegularFile(file)) {
            return false;
        }

        try {
            Files.delete(file);
        } catch (IOException e) {
            LOG.warning("Failed to delete abnormal mark file: " + file, e);
        }
        return true;
    }

    /// Returns a native directory using the current core instance root.
    ///
    /// This override makes the delegation explicit for stable identities.
    ///
    /// @param platform the target platform
    /// @return the platform-specific native directory
    @Override
    public Path getNativeDirectory(Platform platform) {
        return GameInstance.super.getNativeDirectory(platform);
    }

    /// Result of loading an instance-specific game settings file.
    ///
    /// @param settings  the loaded settings, or `null` when unavailable
    /// @param allowSave whether the file may be overwritten
    @NotNullByDefault
    private record InstanceGameSettingsLoadResult(
            @Nullable GameSettings.Instance settings,
            boolean allowSave) {
    }
}
