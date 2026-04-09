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
import org.jackhuang.hmcl.setting.property.InheritableProperty;
import org.jackhuang.hmcl.setting.property.SimpleInheritableProperty;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.gson.RawPreservingObjectProperty;
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
        private final ObjectProperty<UUID> parent = new SimpleObjectProperty<>(this, "parent");

        public ObjectProperty<UUID> parentProperty() {
            return parent;
        }

        /// The icon of the instance.
        private final ObjectProperty<VersionIconType> icon = new SimpleObjectProperty<>(this, "");

        public ObjectProperty<VersionIconType> iconProperty() {
            return icon;
        }

        /// Whether to isolate the instance from other instances.
        @SerializedName("isolation")
        private final BooleanProperty isolation = new SimpleBooleanProperty(this, "isolation", false);

        public BooleanProperty isolationProperty() {
            return isolation;
        }
    }

    public static final class Global extends GameSetting {
        public Global() {
            register();
        }

        /// Whether to enable the version isolation strategy when installing a new instance.
        @SerializedName("defaultIsolationType")
        private final ObjectProperty<DefaultIsolationType> defaultIsolationType = new RawPreservingObjectProperty<>(this, "defaultIsolationType", DefaultIsolationType.MODED);

        public ObjectProperty<DefaultIsolationType> defaultIsolationTypeProperty() {
            return defaultIsolationType;
        }
    }

    protected final <T> InheritableProperty<T> newInheritableProperty(String name) {
        return new SimpleInheritableProperty<>(this, name);
    }

    protected final <T> InheritableProperty<T> newInheritableProperty(String name, T defaultValue) {
        return new SimpleInheritableProperty<>(this, name, defaultValue);
    }

    /// If the value is `null`:
    /// - For global game setting, it is equivalent to [JavaVersionType#AUTO].
    /// - For instance game setting, it inherits the value from global game setting.
    @SerializedName("javaType")
    private final ObjectProperty<@Nullable JavaVersionType> javaType = new RawPreservingObjectProperty<>(this, "javaType");

    public ObjectProperty<@Nullable JavaVersionType> javaTypeProperty() {
        return javaType;
    }

    /// The custom Java version.
    @SerializedName("javaVersion")
    private final StringProperty javaVersion = new SimpleStringProperty(this, "javaVersion", "");

    public StringProperty javaVersionProperty() {
        return javaVersion;
    }

    /// User customized Java path or `null` if user uses system Java.
    @SerializedName("customJavaPath")
    private final StringProperty customJavaPath = new SimpleStringProperty(this, "customJavaPath", "");

    public StringProperty customJavaPathProperty() {
        return customJavaPath;
    }

    /// Path to Java executable, or `null` if user customizes the Java path.
    ///
    /// It's used to determine which Java runtime to use when multiple Java runtimes match the selected Java version.
    @SerializedName("defaultJavaPath")
    private final StringProperty defaultJavaPath = new SimpleStringProperty(this, "defaultJavaPath", "");

    public StringProperty defaultJavaPathProperty() {
        return defaultJavaPath;
    }

    public @Nullable JavaRuntime getJava(@Nullable GameVersionNumber gameVersion, @Nullable Version version) throws InterruptedException {
        switch (Objects.requireNonNullElse(javaType.get(), JavaVersionType.AUTO)) {
            case DEFAULT:
                return JavaRuntime.getDefault();
            case AUTO:
                return JavaManager.findSuitableJava(gameVersion, version);
            case CUSTOM:
                try {
                    return JavaManager.getJava(Path.of(customJavaPathProperty().get()));
                } catch (IOException | InvalidPathException e) {
                    return null; // Custom Java not found
                }
            case VERSION: {
                String javaVersion = javaVersionProperty().get();
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
                String javaVersion = javaVersionProperty().get();
                if (StringUtils.isBlank(javaVersion)) {
                    return JavaManager.findSuitableJava(gameVersion, version);
                }

                try {
                    String defaultJavaPath = defaultJavaPathProperty().get();
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
                throw new AssertionError("Java Type: " + javaTypeProperty().get());
        }
    }

    // JVM Options

    /// The user customized JVM options.
    @SerializedName("jvmOptions")
    private final StringProperty jvmOptions = new SimpleStringProperty(this, "jvmOptions", "");

    public StringProperty jvmOptionsProperty() {
        return jvmOptions;
    }

    /// If `true`, HMCL will not use default JVM arguments.
    @SerializedName("noJVMOptions")
    private final BooleanProperty noJVMOptionsProperty = new SimpleBooleanProperty(this, "noJVMOptions", false);

    public BooleanProperty noJVMOptionsProperty() {
        return noJVMOptionsProperty;
    }

    /// If `true`, HMCL will not use the default optimizing JVM options.
    @SerializedName("noOptimizingJVMOptions")
    private final BooleanProperty noOptimizingJVMOptionsProperty = new SimpleBooleanProperty(this, "noOptimizingJVMOptions", false);

    public BooleanProperty noOptimizingJVMOptionsProperty() {
        return noOptimizingJVMOptionsProperty;
    }

    /// If `true`, HMCL does not check JVM validity.
    @SerializedName("notCheckJVM")
    private final BooleanProperty notCheckJVMProperty = new SimpleBooleanProperty(this, "notCheckJVM", false);

    public BooleanProperty notCheckJVMProperty() {
        return notCheckJVMProperty;
    }

    // Memory

    /// If `true`, HMCL will automatically adjust the memory allocation.
    @SerializedName("autoMemory")
    private final BooleanProperty autoMemory = new SimpleBooleanProperty(this, "autoMemory", true);

    public BooleanProperty autoMemoryProperty() {
        return autoMemory;
    }

    /// The minimum memory that JVM can allocate for heap.
    @SerializedName("minMemory")
    private final ObjectProperty<@Nullable Integer> minMemory = new SimpleObjectProperty<>(this, "minMemory", null);

    public ObjectProperty<@Nullable Integer> minMemoryProperty() {
        return minMemory;
    }

    /// The maximum memory that JVM can allocate for heap.
    @SerializedName("maxMemory")
    private final ObjectProperty<@Nullable Integer> maxMemory = new SimpleObjectProperty<>(this, "maxMemory", null);

    public ObjectProperty<@Nullable Integer> maxMemoryProperty() {
        return maxMemory;
    }

    /// The permanent generation size of JVM garbage collection.
    @SerializedName("permSize")
    private final StringProperty permSizeProperty = new SimpleStringProperty(this, "permSize", "");

    public StringProperty permSizeProperty() {
        return permSizeProperty;
    }

    // Game Window

    @SerializedName("windowType")
    private final ObjectProperty<GameWindowType> windowType = new SimpleObjectProperty<>(this, "windowType", GameWindowType.DEFAULT);

    public ObjectProperty<GameWindowType> windowTypeProperty() {
        return windowType;
    }

    /// The width of the game window.
    @SerializedName("width")
    private final DoubleProperty width = new SimpleDoubleProperty(this, "width", 0.0);

    public DoubleProperty widthProperty() {
        return width;
    }

    /// The height of the game window.
    @SerializedName("height")
    private final DoubleProperty height = new SimpleDoubleProperty(this, "height", 0.0);

    public DoubleProperty heightProperty() {
        return height;
    }

    // Misc

    /// The process priority of the game.
    @SerializedName("processPriority")
    private final ObjectProperty<ProcessPriority> processPriority = new RawPreservingObjectProperty<>(this, "processPriority", ProcessPriority.NORMAL);

    public ObjectProperty<ProcessPriority> processPriorityProperty() {
        return processPriority;
    }

    @SerializedName("launcherVisibility")
    private final ObjectProperty<LauncherVisibility> launcherVisibility = new RawPreservingObjectProperty<>(this, "launcherVisibility", LauncherVisibility.KEEP);

    public ObjectProperty<LauncherVisibility> launcherVisibilityProperty() {
        return launcherVisibility;
    }

    /// The user customized arguments passed to the game.
    @SerializedName("gameArgs")
    private final StringProperty gameArgs = new SimpleStringProperty(this, "gameArgs", "");

    public StringProperty gameArgsProperty() {
        return gameArgs;
    }

    /// The directory where the game will be launched.
    @SerializedName("runningDir")
    private final StringProperty runningDir = new SimpleStringProperty(this, "runningDir", "");

    public StringProperty runningDirProperty() {
        return runningDir;
    }

    /// The renderer used by the game.
    @SerializedName("renderer")
    private final ObjectProperty<Renderer> renderer = new RawPreservingObjectProperty<>(this, "renderer");

    public ObjectProperty<Renderer> rendererProperty() {
        return renderer;
    }

    /// The user customized environment variables passed to game.
    @SerializedName("environmentVariables")
    private final StringProperty environmentVariables = new SimpleStringProperty(this, "environmentVariables", "");

    public StringProperty environmentVariablesProperty() {
        return environmentVariables;
    }

    /// The command wrapper for launching Minecraft.
    ///
    /// For example, `optirun` for NVIDIA Optimus.
    @SerializedName("commandWrapper")
    private final StringProperty commandWrapper = new SimpleStringProperty(this, "commandWrapper", "");

    public StringProperty commandWrapperProperty() {
        return commandWrapper;
    }

    /// The command that will be executed before launching the game.
    @SerializedName("preLaunchCommand")
    private final StringProperty preLaunchCommand = new SimpleStringProperty(this, "preLaunchCommand", "");

    public StringProperty preLaunchCommandProperty() {
        return preLaunchCommand;
    }

    /// The command that will be executed after the game exits.
    @SerializedName("postExitCommand")
    private final StringProperty postExitCommand = new SimpleStringProperty(this, "postExitCommand", "");

    public StringProperty postExitCommandProperty() {
        return postExitCommand;
    }

    // Quick Play

    /// The IP address of the server to join.
    @SerializedName("serverIP")
    private final StringProperty serverIP = new SimpleStringProperty(this, "serverIP", "");

    public StringProperty serverIPProperty() {
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
    private final BooleanProperty notPatchNatives = new SimpleBooleanProperty(this, "notPatchNatives", false);

    public BooleanProperty notPatchNativesProperty() {
        return notPatchNatives;
    }

    /// The path to the store native libraries.
    @SerializedName("nativesDir")
    private final StringProperty nativesDir = new SimpleStringProperty(this, "nativesDir", "");

    public StringProperty nativesDirProperty() {
        return nativesDir;
    }

    /// If `true`, HMCL will use native GLFW.
    @SerializedName("useNativeGLFW")
    private final BooleanProperty useNativeGLFW = new SimpleBooleanProperty(this, "nativeGLFW", false);

    public BooleanProperty useNativeGLFWProperty() {
        return useNativeGLFW;
    }

    /// If `true`, HMCL will use native OpenAL.
    @SerializedName("useNativeOpenAL")
    private final BooleanProperty useNativeOpenAL = new SimpleBooleanProperty(this, "nativeOpenAL", false);

    public BooleanProperty useNativeOpenALProperty() {
        return useNativeOpenAL;
    }
}
