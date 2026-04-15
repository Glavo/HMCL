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
package org.jackhuang.hmcl.setting;

import com.google.gson.annotations.SerializedName;
import javafx.beans.property.*;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.game.Renderer;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.property.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
@NotNullByDefault
public sealed abstract class GameSetting extends ObservableSetting {

    public static final class Instance extends GameSetting {
        public Instance() {
            register();
        }

        /// The parent global game setting ID.
        @SerializedName("parent")
        private final SettingProperty<UUID> parent = newSettingProperty("parent");

        public SettingProperty<UUID> parentProperty() {
            return parent;
        }

        /// The icon of the instance.
        private final SettingProperty<VersionIconType> icon = newSettingProperty("", VersionIconType.DEFAULT);

        public SettingProperty<VersionIconType> iconProperty() {
            return icon;
        }

        /// Whether to isolate the instance from other instances.
        @SerializedName("isolation")
        private final SettingProperty<Boolean> isolation = newSettingProperty("isolation", false);

        public SettingProperty<Boolean> isolationProperty() {
            return isolation;
        }
    }

    public static final class Global extends GameSetting {
        public Global() {
            register();
        }

        /// Whether to enable the version isolation strategy when installing a new instance.
        @SerializedName("defaultIsolationType")
        private final SettingProperty<DefaultIsolationType> defaultIsolationType = newSettingProperty("defaultIsolationType", DefaultIsolationType.MODED);

        public SettingProperty<DefaultIsolationType> defaultIsolationTypeProperty() {
            return defaultIsolationType;
        }
    }

    protected final <T> SettingProperty<T> newSettingProperty(String name) {
        return new SimpleSettingProperty<>(this, null, name);
    }

    protected final <T> SettingProperty<T> newSettingProperty(String name, T defaultValue) {
        return new SimpleSettingProperty<>(this, null, name, defaultValue);
    }

    protected final <T> SettingProperty<T> newSettingProperty(SettingGroup group, String name) {
        return new SimpleSettingProperty<>(this, group, name);
    }

    protected final <T> SettingProperty<T> newSettingProperty(SettingGroup group, String name, T defaultValue) {
        return new SimpleSettingProperty<>(this, group, name, defaultValue);
    }

    protected final <T> InheritableProperty<T> newInheritableProperty(String name, T defaultValue) {
        return new SimpleInheritableProperty<>(this, name, defaultValue);
    }

    /// If the value is `null`:
    /// - For global game setting, it is equivalent to [JavaVersionType#AUTO].
    /// - For instance game setting, it inherits the value from global game setting.
    @SerializedName("javaType")
    private final InheritableProperty<JavaVersionType> javaType = newInheritableProperty("javaType", JavaVersionType.AUTO);

    public InheritableProperty<JavaVersionType> javaTypeProperty() {
        return javaType;
    }

    /// The custom Java version.
    @SerializedName("javaVersion")
    private final SettingProperty<String> javaVersion = newSettingProperty("javaVersion", "");

    public SettingProperty<String> javaVersionProperty() {
        return javaVersion;
    }

    /// User customized Java path or `null` if user uses system Java.
    @SerializedName("customJavaPath")
    private final SettingProperty<String> customJavaPath = newSettingProperty("customJavaPath", "");

    public SettingProperty<String> customJavaPathProperty() {
        return customJavaPath;
    }

    /// Path to Java executable, or `null` if user customizes the Java path.
    ///
    /// It's used to determine which Java runtime to use when multiple Java runtimes match the selected Java version.
    @SerializedName("defaultJavaPath")
    private final SettingProperty<String> defaultJavaPath = newSettingProperty("defaultJavaPath", "");

    public SettingProperty<String> defaultJavaPathProperty() {
        return defaultJavaPath;
    }

    public @Nullable JavaRuntime getJava(@Nullable GameVersionNumber gameVersion, @Nullable Version version) throws InterruptedException {
        switch (Objects.requireNonNullElse(javaType.getValue(), JavaVersionType.AUTO)) {
            case DEFAULT:
                return JavaRuntime.getDefault();
            case AUTO:
                return JavaManager.findSuitableJava(gameVersion, version);
            case CUSTOM:
                try {
                    return JavaManager.getJava(Path.of(customJavaPathProperty().getValue()));
                } catch (IOException | InvalidPathException e) {
                    return null; // Custom Java not found
                }
            case VERSION: {
                String javaVersion = javaVersionProperty().getValue();
                if (StringUtils.isBlank(javaVersion)) {
                    return JavaManager.findSuitableJava(gameVersion, version);
                }

                int majorVersion = -1;
                try {
                    majorVersion = Integer.parseInt(javaVersion);
                } catch (NumberFormatException ignored) {
                }

                if (majorVersion < 0) {
                    LOG.warning("Invalid Java version: " + javaVersion);
                    return null;
                }

                final int finalMajorVersion = majorVersion;
                Collection<JavaRuntime> allJava = JavaManager.getAllJava().stream()
                        .filter(it -> it.getParsedVersion() == finalMajorVersion)
                        .collect(Collectors.toList());
                return JavaManager.findSuitableJava(allJava, gameVersion, version);
            }
            case DETECTED: {
                String javaVersion = javaVersionProperty().getValue();
                if (StringUtils.isBlank(javaVersion)) {
                    return JavaManager.findSuitableJava(gameVersion, version);
                }

                try {
                    String defaultJavaPath = defaultJavaPathProperty().getValue();
                    if (StringUtils.isNotBlank(defaultJavaPath)) {
                        JavaRuntime java = JavaManager.getJava(Path.of(defaultJavaPath).toRealPath());
                        if (java.getVersion().equals(javaVersion)) {
                            return java;
                        }
                    }
                } catch (IOException | InvalidPathException ignored) {
                }

                for (JavaRuntime java : JavaManager.getAllJava()) {
                    if (java.getVersion().equals(javaVersion)) {
                        return java;
                    }
                }

                return null;
            }
            default:
                throw new AssertionError("Java Type: " + javaTypeProperty().getValue());
        }
    }

    // JVM Options

    /// The user customized JVM options.
    @SerializedName("jvmOptions")
    private final SettingProperty<String> jvmOptions = newSettingProperty("jvmOptions", "");

    public SettingProperty<String> jvmOptionsProperty() {
        return jvmOptions;
    }

    /// If `true`, HMCL will not use default JVM arguments.
    @SerializedName("noJVMOptions")
    private final SettingProperty<Boolean> noJVMOptionsProperty = newSettingProperty("noJVMOptions", false);

    public SettingProperty<Boolean> noJVMOptionsProperty() {
        return noJVMOptionsProperty;
    }

    /// If `true`, HMCL will not use the default optimizing JVM options.
    @SerializedName("noOptimizingJVMOptions")
    private final SettingProperty<Boolean> noOptimizingJVMOptionsProperty = newSettingProperty("noOptimizingJVMOptions", false);

    public SettingProperty<Boolean> noOptimizingJVMOptionsProperty() {
        return noOptimizingJVMOptionsProperty;
    }

    /// If `true`, HMCL does not check JVM validity.
    @SerializedName("notCheckJVM")
    private final SettingProperty<Boolean> notCheckJVMProperty = newSettingProperty("notCheckJVM", false);

    public SettingProperty<Boolean> notCheckJVMProperty() {
        return notCheckJVMProperty;
    }

    // Memory

    /// If `true`, HMCL will automatically adjust the memory allocation.
    @SerializedName("autoMemory")
    private final SettingProperty<Boolean> autoMemory = newSettingProperty("autoMemory", true);

    public SettingProperty<Boolean> autoMemoryProperty() {
        return autoMemory;
    }

    /// The minimum memory that JVM can allocate for heap.
    @SerializedName("minMemory")
    private final SettingProperty<@Nullable Integer> minMemory = newSettingProperty("minMemory");

    public SettingProperty<@Nullable Integer> minMemoryProperty() {
        return minMemory;
    }

    /// The maximum memory that JVM can allocate for heap.
    @SerializedName("maxMemory")
    private final SettingProperty<@Nullable Integer> maxMemory = newSettingProperty("maxMemory");

    public SettingProperty<@Nullable Integer> maxMemoryProperty() {
        return maxMemory;
    }

    /// The permanent generation size of JVM garbage collection.
    @SerializedName("permSize")
    private final SettingProperty<String> permSizeProperty = newSettingProperty("permSize", "");

    public SettingProperty<String> permSizeProperty() {
        return permSizeProperty;
    }

    // Game Window

    @SerializedName("windowType")
    private final SettingProperty<GameWindowType> windowType = newSettingProperty("windowType", GameWindowType.DEFAULT);

    public SettingProperty<GameWindowType> windowTypeProperty() {
        return windowType;
    }

    /// The width of the game window.
    @SerializedName("width")
    private final SettingProperty<Double> width = newSettingProperty("width", 0.0);

    public SettingProperty<Double> widthProperty() {
        return width;
    }

    /// The height of the game window.
    @SerializedName("height")
    private final SettingProperty<Double> height = newSettingProperty("height", 0.0);

    public SettingProperty<Double> heightProperty() {
        return height;
    }

    // Misc

    /// The process priority of the game.
    @SerializedName("processPriority")
    private final SettingProperty<ProcessPriority> processPriority = newSettingProperty("processPriority", ProcessPriority.NORMAL);

    public SettingProperty<ProcessPriority> processPriorityProperty() {
        return processPriority;
    }

    @SerializedName("launcherVisibility")
    private final SettingProperty<LauncherVisibility> launcherVisibility = newSettingProperty("launcherVisibility", LauncherVisibility.KEEP);

    public SettingProperty<LauncherVisibility> launcherVisibilityProperty() {
        return launcherVisibility;
    }

    /// The user customized arguments passed to the game.
    @SerializedName("gameArgs")
    private final SettingProperty<String> gameArgs = newSettingProperty("gameArgs", "");

    public SettingProperty<String> gameArgsProperty() {
        return gameArgs;
    }

    /// The directory where the game will be launched.
    @SerializedName("runningDir")
    private final SettingProperty<String> runningDir = newSettingProperty("runningDir", "");

    public SettingProperty<String> runningDirProperty() {
        return runningDir;
    }

    /// The renderer used by the game.
    @SerializedName("renderer")
    private final SettingProperty<Renderer> renderer = newSettingProperty("renderer");

    public SettingProperty<Renderer> rendererProperty() {
        return renderer;
    }

    /// The user customized environment variables passed to game.
    @SerializedName("environmentVariables")
    private final SettingProperty<String> environmentVariables = newSettingProperty("environmentVariables", "");

    public SettingProperty<String> environmentVariablesProperty() {
        return environmentVariables;
    }

    /// The command wrapper for launching Minecraft.
    ///
    /// For example, `optirun` for NVIDIA Optimus.
    @SerializedName("commandWrapper")
    private final SettingProperty<String> commandWrapper = newSettingProperty("commandWrapper", "");

    public SettingProperty<String> commandWrapperProperty() {
        return commandWrapper;
    }

    /// The command that will be executed before launching the game.
    @SerializedName("preLaunchCommand")
    private final SettingProperty<String> preLaunchCommand = newSettingProperty("preLaunchCommand", "");

    public SettingProperty<String> preLaunchCommandProperty() {
        return preLaunchCommand;
    }

    /// The command that will be executed after the game exits.
    @SerializedName("postExitCommand")
    private final SettingProperty<String> postExitCommand = newSettingProperty("postExitCommand", "");

    public SettingProperty<String> postExitCommandProperty() {
        return postExitCommand;
    }

    // Quick Play

    /// The IP address of the server to join.
    @SerializedName("serverIP")
    private final SettingProperty<String> serverIP = newSettingProperty("serverIP", "");

    public SettingProperty<String> serverIPProperty() {
        return serverIP;
    }

    // Logging

    /// If `true`, show the logs after game launched.
    @SerializedName("showLogs")
    private final InheritableProperty<Boolean> showLogsProperty = newInheritableProperty("showLogs", false);

    public InheritableProperty<Boolean> showLogsProperty() {
        return showLogsProperty;
    }

    /// If `true`, enable debug log output.
    private final InheritableProperty<Boolean> enableDebugLogOutputProperty = newInheritableProperty("enableDebugLogOutput", false);

    public InheritableProperty<Boolean> enableDebugLogOutputProperty() {
        return enableDebugLogOutputProperty;
    }

    // Native Libraries

    /// If `true`, HMCL does not patch native libraries.
    private final SettingProperty<Boolean> notPatchNatives = newSettingProperty("notPatchNatives", false);

    public SettingProperty<Boolean> notPatchNativesProperty() {
        return notPatchNatives;
    }

    /// The path to the store native libraries.
    @SerializedName("nativesDir")
    private final SettingProperty<String> nativesDir = newSettingProperty("nativesDir", "");

    public SettingProperty<String> nativesDirProperty() {
        return nativesDir;
    }

    /// If `true`, HMCL will use native GLFW.
    @SerializedName("useNativeGLFW")
    private final SettingProperty<Boolean> useNativeGLFW = newSettingProperty("nativeGLFW", false);

    public SettingProperty<Boolean> useNativeGLFWProperty() {
        return useNativeGLFW;
    }

    /// If `true`, HMCL will use native OpenAL.
    @SerializedName("useNativeOpenAL")
    private final SettingProperty<Boolean> useNativeOpenAL = newSettingProperty("nativeOpenAL", false);

    public SettingProperty<Boolean> useNativeOpenALProperty() {
        return useNativeOpenAL;
    }
}
