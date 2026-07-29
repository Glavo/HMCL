/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.DefaultIsolationType;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameWindowType;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.ProxyType;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HMCL game repository implementation backed by a GameDirectory and per-instance game settings.
@NotNullByDefault
public final class HMCLGameRepository extends DefaultGameRepository {
    /// References an optional game instance in a repository.
    ///
    /// @param repository the owning game repository
    /// @param instance   the stable game instance, or `null` when only repository context is available
    @NotNullByDefault
    public record InstanceReference(
            HMCLGameRepository repository,
            @Nullable HMCLGameInstance instance) {
        /// Creates a reference from an optional instance ID.
        ///
        /// @param repository the owning repository
        /// @param instanceId the instance ID, or `null`
        public InstanceReference(
                HMCLGameRepository repository,
                @Nullable GameInstanceID instanceId) {
            this(
                    repository,
                    instanceId == null
                            ? null
                            : repository.getOrCreateStableInstance(instanceId));
        }

        /// Validates that a referenced instance belongs to the repository.
        public InstanceReference {
            if (instance != null && instance.getRepository() != repository) {
                throw new IllegalArgumentException(
                        "Referenced instance belongs to a different repository");
            }
        }

        /// Returns the optional instance ID.
        ///
        /// @return the instance ID, or `null`
        public @Nullable GameInstanceID instanceId() {
            return instance == null ? null : instance.getId();
        }
    }

    /// Directory under the instance root that stores HMCL-managed instance metadata.
    private static final String INSTANCE_METADATA_DIRECTORY = ".hmcl";

    /// Directory under the instance metadata directory that stores instance configuration files.
    private static final String INSTANCE_CONFIG_DIRECTORY = "config";

    /// Directory under the instance metadata directory that stores instance state files.
    private static final String INSTANCE_STATE_DIRECTORY = "state";

    /// The persistent game directory for this repository.
    private final GameDirectory gameDirectory;

    /// The selected instance ID persisted for this repository's game directory.
    private final ObjectBinding<@Nullable GameInstanceID> selectedInstance;

    /// Stable HMCL identities, including provisional identities prepared before manifest saving.
    private final ConcurrentHashMap<GameInstanceID, HMCLGameInstance> stableInstances =
            new ConcurrentHashMap<>();

    public final EventManager<Event> onInstanceIconChanged = new EventManager<>();

    /// Creates a repository backed by the given game directory.
    public HMCLGameRepository(GameDirectory gameDirectory) {
        super(gameDirectory.getPath().toPath());
        this.gameDirectory = gameDirectory;
        this.selectedInstance = Bindings.valueAt(settings().getSelectedInstance(), gameDirectory.getId());
        gameDirectory.pathProperty().addListener((a, b, newValue) -> changeDirectory(newValue.toPath()));
    }

    /// Returns the persistent game directory for this repository.
    public GameDirectory getGameDirectory() {
        return gameDirectory;
    }

    /// Returns the selected instance ID property for this repository's game directory.
    public Binding<@Nullable GameInstanceID> selectedInstanceProperty() {
        return selectedInstance;
    }

    /// Returns the selected instance ID for this repository's game directory.
    public @Nullable GameInstanceID getSelectedInstance() {
        return selectedInstance.get();
    }

    /// Sets the selected instance ID for this repository's game directory.
    public void setSelectedInstance(@Nullable GameInstanceID instanceId) {
        settings().setSelectedInstance(gameDirectory.getId(), instanceId);
    }

    /// Refreshes the selected instance ID after instances are loaded.
    public void refreshSelectedInstance() {
        @Nullable GameInstanceID selectedInstance = settings().getSelectedInstance(gameDirectory.getId());
        @Nullable GameInstanceID refreshedInstance = selectedInstance;
        if (refreshedInstance == null || !hasInstance(refreshedInstance)) {
            refreshedInstance = getInstances().isEmpty() ? null : getInstances().getFirst().getId();
        }
        if (!Objects.equals(selectedInstance, refreshedInstance)) {
            setSelectedInstance(refreshedInstance);
        }
    }

    /// Returns a dependency manager using the currently selected download provider.
    public DefaultDependencyManager getDependency() {
        return getDependency(DownloadProviders.getDownloadProvider());
    }

    /// Returns a dependency manager using the given download provider.
    public DefaultDependencyManager getDependency(DownloadProvider downloadProvider) {
        return new DefaultDependencyManager(this, downloadProvider, HMCLCacheRepository.REPOSITORY);
    }

    /// Reconciles immutable core values into stable HMCL identities before snapshot publication.
    ///
    /// Settings are loaded only for identities created here for the first time. A base-directory
    /// replacement detaches identities from the previous physical repository.
    ///
    /// @param previousSnapshot the currently published snapshot
    /// @param proposedSnapshot the newly built core snapshot
    /// @return a snapshot containing stable HMCL identities
    @Override
    protected Snapshot onSnapshotChanged(
            Snapshot previousSnapshot,
            Snapshot proposedSnapshot) {
        if (!previousSnapshot.layout().getBaseDirectory()
                .equals(proposedSnapshot.layout().getBaseDirectory())) {
            stableInstances.values().forEach(HMCLGameInstance::detach);
            stableInstances.clear();
        }

        Map<GameInstanceID, GameInstance> reconciled = new TreeMap<>();
        for (GameInstance coreInstance : proposedSnapshot.instances().values()) {
            HMCLGameInstance instance = stableInstances.compute(
                    coreInstance.getId(),
                    (instanceId, current) -> {
                        if (current == null || current.isDetached()) {
                            return new HMCLGameInstance(this, coreInstance);
                        }
                        current.updateBase(coreInstance);
                        return current;
                    });
            reconciled.put(instance.getId(), instance);
        }
        return new Snapshot(proposedSnapshot.layout(), reconciled);
    }

    /// Returns stable HMCL identities from the current repository snapshot.
    ///
    /// @return an unmodifiable ID-sorted snapshot list
    @Override
    public @Unmodifiable List<HMCLGameInstance> getInstances() {
        return super.getInstances().stream()
                .map(HMCLGameInstance.class::cast)
                .toList();
    }

    /// Returns the stable HMCL identity for an active instance.
    ///
    /// @param instanceId the instance ID
    /// @return the active identity, or empty if it is not in the current snapshot
    @Override
    public Optional<HMCLGameInstance> getInstance(GameInstanceID instanceId) {
        return super.getInstance(instanceId).map(HMCLGameInstance.class::cast);
    }

    /// Returns or creates a provisional stable identity for pre-publication settings and paths.
    ///
    /// @param instanceId the instance ID
    /// @return the stable or provisional identity
    private HMCLGameInstance getOrCreateStableInstance(GameInstanceID instanceId) {
        return stableInstances.computeIfAbsent(instanceId, id -> {
            GameInstanceManifest manifest = new GameInstanceManifest(id);
            GameInstanceManifest.Resolved resolvedManifest = resolve(manifest);
            GameInstance base = new DefaultGameInstance(getLayout(), manifest, resolvedManifest);
            return new HMCLGameInstance(this, base);
        });
    }

    @Override
    public Path getRunDirectory(GameInstanceID instanceId) {
        @Nullable HMCLGameInstance instance = stableInstances.get(instanceId);
        return instance != null ? instance.getRunDirectory() : super.getRunDirectory(instanceId);
    }

    public Stream<GameInstanceManifest> getDisplayInstanceManifests() {
        return getInstances().stream()
                .map(GameInstance::getManifest)
                .filter(manifest -> !manifest.isHidden())
                .sorted(Comparator.comparing(
                                (GameInstanceManifest manifest) -> Lang.requireNonNullElse(
                                        manifest.releaseTime(),
                                        Instant.EPOCH))
                        .thenComparing(
                                manifest -> VersionNumber.asVersion(manifest.id().id())));
    }

    @Override
    protected void refreshImpl() {
        super.refreshImpl();

        try {
            Path file = getBaseDirectory().resolve("launcher_profiles.json");
            if (!Files.exists(file) && !getInstances().isEmpty()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, PROFILE);
            }
        } catch (IOException ex) {
            LOG.warning("Unable to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex);
        }
    }

    public void changeDirectory(Path newDirectory) {
        setBaseDirectory(newDirectory);
        refreshAsync().start();
    }

    private void clean(Path directory) throws IOException {
        FileUtils.deleteDirectory(directory.resolve("crash-reports"));
        FileUtils.deleteDirectory(directory.resolve("logs"));
    }

    public void clean(GameInstanceID instanceId) throws IOException {
        clean(getBaseDirectory());
        clean(getRunDirectory(instanceId));
    }

    /// Renames an instance and detaches the stable identity associated with its former ID.
    ///
    /// @param from the current instance ID
    /// @param to   the target instance ID
    /// @return whether the instance was renamed
    @Override
    public boolean renameInstance(GameInstanceID from, GameInstanceID to) {
        boolean renamed = super.renameInstance(from, to);
        if (renamed) {
            @Nullable HMCLGameInstance oldIdentity = stableInstances.remove(from);
            if (oldIdentity != null) {
                oldIdentity.detach();
            }
        }
        return renamed;
    }

    /// Removes an instance from disk and clears its cached HMCL settings state.
    @Override
    public boolean removeInstanceFromDisk(GameInstanceID instanceId) {
        boolean removed = super.removeInstanceFromDisk(instanceId);
        if (removed) {
            @Nullable HMCLGameInstance instance = stableInstances.remove(instanceId);
            if (instance != null) {
                instance.detach();
            }
        }
        return removed;
    }

    public void duplicateInstance(GameInstanceID srcId, GameInstanceID dstId, boolean copySaves) throws IOException {
        HMCLGameInstance sourceInstance = getInstance(srcId).orElseThrow();
        Path srcDir = sourceInstance.getInstanceRoot();
        HMCLGameInstance destinationInstance = getOrCreateStableInstance(dstId);
        Path dstDir = destinationInstance.getInstanceRoot();
        GameInstanceManifest fromManifest = sourceInstance.getManifest();

        List<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(srcId.id() + ".jar");
        blackList.add(srcId.id() + ".json");
        if (!copySaves)
            blackList.add("saves");

        if (Files.exists(dstDir)) throw new IOException("Instance exists");

        Files.createDirectories(dstDir);
        FileUtils.copyDirectory(srcDir, dstDir, path -> Modpack.acceptFile(path, blackList, null));

        Path fromJson = srcDir.resolve(srcId.id() + ".json");
        Path fromJar = srcDir.resolve(srcId.id() + ".jar");
        Path toJson = dstDir.resolve(dstId.id() + ".json");
        Path toJar = dstDir.resolve(dstId.id() + ".jar");

        if (Files.exists(fromJar)) {
            Files.copy(fromJar, toJar);
        }
        Files.copy(fromJson, toJson);

        JsonUtils.writeToJsonFile(toJson, fromManifest.withId(dstId).withJar(dstId));

        boolean copyOriginalGameDir;
        try {
            copyOriginalGameDir = !Files.isSameFile(
                    sourceInstance.getRunDirectory(),
                    sourceInstance.getInstanceRoot());
        } catch (IOException e) {
            copyOriginalGameDir = true;
        }

        Path srcGameDir = sourceInstance.getRunDirectory();

        GameSettings.Instance newGameSettings = sourceInstance.copyGameSettings();
        newGameSettings.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        newGameSettings.runningDirectoryProperty().setValue("");
        destinationInstance.initializeGameSettings(newGameSettings);
        destinationInstance.saveGameSettingsSync();

        Path dstGameDir = destinationInstance.getRunDirectory();

        if (copyOriginalGameDir)
            FileUtils.copyDirectory(srcGameDir, dstGameDir, path -> Modpack.acceptFile(path, blackList, null));
    }

    /// Returns the HMCL-managed metadata directory under the instance root.
    ///
    /// This directory stores instance-scoped files owned by HMCL.
    public Path getInstanceMetadataDirectory(GameInstanceID instanceId) {
        return getLayout()
                .getInstanceRoot(instanceId)
                .resolve(INSTANCE_METADATA_DIRECTORY);
    }

    /// Returns the HMCL-managed configuration directory under the instance metadata directory.
    public Path getInstanceConfigDirectory(GameInstanceID instanceId) {
        return getInstanceMetadataDirectory(instanceId).resolve(INSTANCE_CONFIG_DIRECTORY);
    }

    /// Returns the HMCL-managed state directory under the instance metadata directory.
    public Path getInstanceStateDirectory(GameInstanceID instanceId) {
        return getInstanceMetadataDirectory(instanceId).resolve(INSTANCE_STATE_DIRECTORY);
    }

    public @Nullable GameSettings.Instance createInstanceGameSettings(GameInstanceID instanceId) {
        return getInstance(instanceId)
                .map(HMCLGameInstance::createGameSettings)
                .orElse(null);
    }

    @Nullable
    public GameSettings.Instance getInstanceGameSettings(GameInstanceID instanceId) {
        return getOrCreateStableInstance(instanceId).getGameSettings();
    }

    @Nullable
    public GameSettings.Instance getInstanceGameSettingsOrCreate(GameInstanceID instanceId) {
        return getOrCreateStableInstance(instanceId).getOrCreateGameSettings();
    }

    /// Returns whether the instance-specific game settings file cannot be overwritten safely.
    ///
    /// @param instanceId the instance ID
    /// @return whether the instance settings are loaded in read-only mode
    public boolean isInstanceGameSettingsReadOnly(GameInstanceID instanceId) {
        return getOrCreateStableInstance(instanceId).isGameSettingsReadOnly();
    }

    /// Backs up and overwrites the instance-specific game settings file with the currently loaded settings.
    ///
    /// @param instanceId the instance ID
    public void forceOverwriteInstanceGameSettings(GameInstanceID instanceId) {
        getOrCreateStableInstance(instanceId).forceOverwriteGameSettings();
    }

    /// Returns the explicit parent preset of the instance, falling back to the default preset.
    public GameSettings.Preset getParentGameSettings(@Nullable GameSettings.Instance instance) {
        @Nullable GameSettingsPresetID parent = instance != null ? instance.parentProperty().getValue() : null;
        @Nullable GameSettings.Preset parentSetting =
                SettingsManager.getGameSettings(parent);
        return parentSetting != null ? parentSetting : SettingsManager.getDefaultGameSettingsPresetOrCreate();
    }

    public GameSettings.Effective getEffectiveGameSettings(GameInstanceID instanceId) {
        return getOrCreateStableInstance(instanceId).getEffectiveGameSettings();
    }

    public void applyDefaultIsolationSetting(GameInstanceID instanceId) {
        getInstance(instanceId).ifPresent(HMCLGameInstance::applyDefaultIsolationSetting);
    }

    /// Returns whether a new instance should use an isolated running directory under the default isolation settings.
    public boolean shouldIsolateNewInstance(boolean modded) {
        GameSettings.Preset preset = getParentGameSettings(null);
        DefaultIsolationType type = Lang.requireNonNullElse(preset.defaultIsolationTypeProperty().getValue(), DefaultIsolationType.MODDED);
        return switch (type) {
            case NEVER -> false;
            case ALWAYS -> true;
            case MODDED -> modded;
        };
    }

    /// Applies default isolation to a new instance before its manifest is saved.
    public void applyDefaultIsolationSettingForNewInstance(GameInstanceID instanceId, boolean modded) {
        getOrCreateStableInstance(instanceId)
                .applyDefaultIsolationSettingForNewInstance(
                        shouldIsolateNewInstance(modded));
    }

    public Optional<Path> getInstanceIconFile(GameInstanceID instanceId) {
        return getOrCreateStableInstance(instanceId).getIconFile();
    }

    public void setInstanceIconFile(GameInstanceID instanceId, Path iconFile) throws IOException {
        getOrCreateStableInstance(instanceId).setIconFile(iconFile);
    }

    public void deleteIconFile(GameInstanceID instanceId) {
        getOrCreateStableInstance(instanceId).deleteIconFile();
    }

    public Image getInstanceIconImage(@Nullable GameInstanceID instanceId) {
        if (instanceId == null || !isLoaded()) {
            return GameInstanceIconType.DEFAULT.getIcon();
        }
        return getInstance(instanceId)
                .map(HMCLGameInstance::getIconImage)
                .orElseGet(() -> GameInstanceIconType.DEFAULT.getIcon());
    }

    public void saveGameSettings(GameInstanceID instanceId) {
        @Nullable HMCLGameInstance instance = stableInstances.get(instanceId);
        if (instance != null) {
            instance.saveGameSettings();
        }
    }

    public LaunchOptions.Builder getLaunchOptions(GameInstanceID instanceId, JavaRuntime javaVersion, Path gameDir, List<String> javaAgents, List<String> javaArguments, boolean makeLaunchScript) {
        HMCLGameInstance instance = getInstance(instanceId).orElseThrow();
        GameSettings.Effective vs = instance.getEffectiveGameSettings();
        boolean noJVMOptions = vs.getInheritable(GameSettings::noJVMOptionsProperty);
        boolean autoMemory = vs.getInheritable(GameSettings::autoMemoryProperty);
        GameVersionNumber gameVersionNumber =
                GameVersionNumber.asGameVersion(getGameVersion(instance.getManifest()));

        @Nullable Integer maxMemory;
        if (autoMemory) {
            maxMemory = noJVMOptions
                    ? null
                    : Math.toIntExact(getAutoAllocatedMemory(SystemInfo.getPhysicalMemoryStatus().available()) / 1024L / 1024L);
        } else {
            maxMemory = vs.getMaxMemory();
        }

        LaunchOptions.Builder builder = new LaunchOptions.Builder()
                .setInstanceId(instanceId)
                .setGameDir(gameDir)
                .setJava(javaVersion)
                .setVersionType(Metadata.TITLE)
                .setVersionName(instanceId.id())
                .setProfileName(Metadata.TITLE)
                .setGameArguments(StringUtils.tokenize(vs.getInheritable(GameSettings::gameArgumentsProperty)))
                .setOverrideJavaArguments(StringUtils.tokenize(vs.getInheritable(GameSettings::jvmOptionsProperty)))
                .setMaxMemory(maxMemory)
                .setMinMemory(vs.getInheritable(GameSettings::minMemoryProperty))
                .setMetaspace(Lang.toIntOrNull(vs.getInheritable(GameSettings::permSizeProperty)))
                .setEnvironmentVariables(
                        Lang.mapOf(StringUtils.tokenize(vs.getInheritable(GameSettings::environmentVariablesProperty))
                                .stream()
                                .map(it -> {
                                    int idx = it.indexOf('=');
                                    return idx >= 0 ? pair(it.substring(0, idx), it.substring(idx + 1)) : pair(it, "");
                                })
                                .collect(Collectors.toList())
                        )
                )
                .setWidth(vs.getWidth())
                .setHeight(vs.getHeight())
                .setFullscreen(vs.getInheritable(GameSettings::windowTypeProperty) == GameWindowType.FULLSCREEN)
                .setWrapper(vs.getInheritable(GameSettings::commandWrapperProperty))
                .setProxyOption(getProxyOption())
                .setPreLaunchCommand(vs.getInheritable(GameSettings::preLaunchCommandProperty))
                .setPostExitCommand(vs.getInheritable(GameSettings::postExitCommandProperty))
                .setNoGeneratedJVMArgs(noJVMOptions)
                .setNoGeneratedOptimizingJVMArgs(vs.getInheritable(GameSettings::noOptimizingJVMOptionsProperty))
                .setUseCustomNatives(vs.getInheritable(GameSettings::useCustomNativesProperty))
                .setNativesDir(vs.getInheritable(GameSettings::nativesDirectoryProperty))
                .setProcessPriority(vs.getInheritable(GameSettings::processPriorityProperty))
                .setGraphicsBackend(vs.getInheritable(GameSettings::graphicsBackendProperty))
                .setRenderer(vs.getRenderer(gameVersionNumber))
                .setEnableDebugLogOutput(vs.getInheritable(GameSettings::enableDebugLogOutputProperty))
                .setAllowAutoAgent(vs.getInheritable(GameSettings::allowAutoAgentProperty))
                .setDisableAutoGameOptions(vs.getInheritable(GameSettings::disableAutoGameOptionsProperty))
                .setUseNativeGLFW(vs.getInheritable(GameSettings::useNativeGLFWProperty))
                .setUseNativeOpenAL(vs.getInheritable(GameSettings::useNativeOpenALProperty))
                .setDaemon(!makeLaunchScript && vs.getInheritable(GameSettings::launcherVisibilityProperty).isDaemon())
                .setJavaAgents(javaAgents)
                .setJavaArguments(javaArguments);

        @Nullable QuickPlayOption quickPlayOption = vs.getQuickPlayOption();
        if (quickPlayOption != null) {
            builder.setQuickPlayOption(quickPlayOption);
        }

        Path json = getModpackConfiguration(instanceId);
        if (Files.exists(json)) {
            try {
                String jsonText = Files.readString(json);
                @Nullable ModpackConfiguration<?> modpackConfiguration =
                        JsonUtils.GSON.fromJson(jsonText, ModpackConfiguration.class);
                if (modpackConfiguration != null) {
                    @Nullable ModpackProvider provider =
                            ModpackHelper.getProviderByType(modpackConfiguration.getType());
                    if (provider != null) {
                        provider.injectLaunchOptions(jsonText, builder);
                    }
                }
            } catch (IOException | JsonParseException e) {
                LOG.warning("Failed to parse modpack configuration file " + json, e);
            }
        }

        if (autoMemory && builder.getJavaArguments().stream().anyMatch(it -> it.startsWith("-Xmx")))
            builder.setMaxMemory(null);

        return builder;
    }

    @Override
    public Path getModpackConfiguration(GameInstanceID instanceId) {
        return getOrCreateStableInstance(instanceId).getModpackConfiguration();
    }

    public void markInstanceAsModpack(GameInstanceID instanceId) {
        getOrCreateStableInstance(instanceId).setBeingModpack(true);
    }

    public void undoMark(GameInstanceID instanceId) {
        @Nullable HMCLGameInstance instance = stableInstances.get(instanceId);
        if (instance != null) {
            instance.setBeingModpack(false);
        }
    }

    public void markInstanceLaunchedAbnormally(GameInstanceID instanceId) {
        getOrCreateStableInstance(instanceId).markLaunchedAbnormally();
    }

    public boolean unmarkInstanceLaunchedAbnormally(GameInstanceID instanceId) {
        return getOrCreateStableInstance(instanceId).unmarkLaunchedAbnormally();
    }

    private static final String PROFILE = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";


    // These instance ids are forbidden because they may conflict with modpack configuration filenames
    private static final @Unmodifiable Set<String> FORBIDDEN_INSTANCE_IDS =
            Set.of("modpack", "minecraftinstance", "manifest");

    public static boolean isValidInstanceId(String id) {
        if (FORBIDDEN_INSTANCE_IDS.contains(id))
            return false;

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS &&
                FORBIDDEN_INSTANCE_IDS.contains(id.toLowerCase(Locale.ROOT)))
            return false;

        return FileUtils.isNameValidForJar(id);
    }

    /**
     * Returns true if the given instance id conflicts with an existing instance.
     */
    public boolean instanceIdConflicts(String instanceId) {
        try {
            return instanceIdConflicts(new GameInstanceID(instanceId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean instanceIdConflicts(GameInstanceID id) {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            // on Windows, filenames are case-insensitive
            for (HMCLGameInstance instance : getInstances()) {
                if (instance.getId().toString().equalsIgnoreCase(id.toString())) {
                    return true;
                }
            }
            return false;
        } else {
            return hasInstance(id);
        }
    }

    public static long getAutoAllocatedMemory(long available) {
        long usable = available - 512 * 1024 * 1024; // Reserve 512 MiB memory for off-heap memory and HMCL itself
        if (usable <= 0) {
            return available;
        }

        final long threshold = 8L * 1024 * 1024 * 1024; // 8 GiB
        final long suggested;
        if (usable <= threshold)
            suggested = (long) (usable * 0.8);
        else
            suggested = Math.min(
                    (long) (threshold * 0.8 + (usable - threshold) * 0.2),
                    16L * 1024 * 1024 * 1024);
        return suggested;
    }

    public static ProxyOption getProxyOption() {
        return switch (settings().proxyTypeProperty().get()) {
            case SYSTEM -> ProxyOption.Default.INSTANCE;
            case DIRECT -> ProxyOption.Direct.INSTANCE;
            case HTTP, SOCKS -> {
                String proxyHost = settings().proxyHostProperty().get();
                int proxyPort = settings().proxyPortProperty().get();

                if (StringUtils.isBlank(proxyHost) || proxyPort < 0 || proxyPort > 0xFFFF) {
                    yield ProxyOption.Default.INSTANCE;
                }

                String proxyUser = settings().proxyUserProperty().get();
                String proxyPass = settings().proxyPasswordProperty().get();

                if (StringUtils.isBlank(proxyUser)) {
                    proxyUser = null;
                    proxyPass = null;
                } else if (proxyPass == null) {
                    proxyPass = "";
                }

                if (settings().proxyTypeProperty().get() == ProxyType.HTTP) {
                    yield new ProxyOption.Http(proxyHost, proxyPort, proxyUser, proxyPass);
                } else {
                    yield new ProxyOption.Socks(proxyHost, proxyPort, proxyUser, proxyPass);
                }
            }
        };
    }
}
