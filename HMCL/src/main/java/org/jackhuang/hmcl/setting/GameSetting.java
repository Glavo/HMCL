
/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.game.GameDirectoryType;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.game.Renderer;
import org.jackhuang.hmcl.util.platform.SystemInfo;

import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;

/// @author Glavo
public abstract class GameSetting {

    private static final int SUGGESTED_MEMORY;

    static {
        double totalMemoryMB = MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize());
        SUGGESTED_MEMORY = totalMemoryMB >= 32768
                ? 8192
                : Integer.max((int) (Math.round(totalMemoryMB / 4.0 / 128.0) * 128), 256);
    }

    // Java

    @SerializedName("javaType")
    private final ObjectProperty<JavaVersionType> javaType = new SimpleObjectProperty<>(this, "javaType", JavaVersionType.AUTO);

    public ObjectProperty<JavaVersionType> javaTypeProperty() {
        return javaType;
    }

    @SerializedName("javaVersion")
    private final StringProperty javaVersion = new SimpleStringProperty(this, "javaVersion", "");

    public StringProperty javaVersionProperty() {
        return javaVersion;
    }

    /// User customized java path or null if user uses system Java.
    @SerializedName("customJavaPath")
    private final StringProperty customJavaPath = new SimpleStringProperty(this, "customJavaPath", "");

    public StringProperty customJavaPathProperty() {
        return customJavaPath;
    }

    /// Path to Java executable, or null if user customizes java directory.
    /// It's used to determine which JRE to use when multiple JREs match the selected Java version.
    @SerializedName("defaultJavaPath")
    private final StringProperty defaultJavaPath = new SimpleStringProperty(this, "defaultJavaPath", "");

    public StringProperty defaultJavaPathProperty() {
        return defaultJavaPath;
    }

    // Memory

    private final BooleanProperty autoMemory = new SimpleBooleanProperty(this, "autoMemory", true);

    public BooleanProperty autoMemoryProperty() {
        return autoMemory;
    }

    /// The maximum memory/MB that JVM can allocate for heap.
    private final IntegerProperty maxMemory = new SimpleIntegerProperty(this, "maxMemory", SUGGESTED_MEMORY);

    public IntegerProperty maxMemoryProperty() {
        return maxMemory;
    }

    /// The minimum memory that JVM can allocate for heap.
    private final ObjectProperty<Integer> minMemory = new SimpleObjectProperty<>(this, "minMemory", null);

    public ObjectProperty<Integer> minMemoryProperty() {
        return minMemory;
    }

    /// The permanent generation size of JVM garbage collection.
    private final StringProperty permSize = new SimpleStringProperty(this, "permSize", "");

    public StringProperty permSizeProperty() {
        return permSize;
    }

    // Game Settings

    /// True if Minecraft started in fullscreen mode.
    private final BooleanProperty fullscreen = new SimpleBooleanProperty(this, "fullscreen", false);

    public BooleanProperty fullscreenProperty() {
        return fullscreen;
    }

    /// The width of Minecraft window, defaults 800.
    ///
    /// The field saves int value.
    /// String type prevents unexpected value from JsonParseException.
    /// We can only reset this field instead of recreating the whole setting file.
    private final IntegerProperty width = new SimpleIntegerProperty(this, "width", 854);

    public IntegerProperty widthProperty() {
        return width;
    }

    /// The height of Minecraft window, defaults 480.
    ///
    /// The field saves int value.
    /// String type prevents unexpected value from JsonParseException.
    /// We can only reset this field instead of recreating the whole setting file.
    private final IntegerProperty height = new SimpleIntegerProperty(this, "height", 480);

    public IntegerProperty heightProperty() {
        return height;
    }

    // ------

    private final StringProperty nativesDir = new SimpleStringProperty(this, "nativesDir", "");

    public StringProperty nativesDirProperty() {
        return nativesDir;
    }

    // Custom Command

    /// The command to launch java, i.e. optirun.
    private final StringProperty commandWrapper = new SimpleStringProperty(this, "wrapper", "");

    public StringProperty commandWrapperProperty() {
        return commandWrapper;
    }

    /// The command that will be executed before launching the Minecraft.
    /// Operating system relevant.
    private final StringProperty preLaunchCommand = new SimpleStringProperty(this, "preLaunchCommand", "");

    public StringProperty preLaunchCommandProperty() {
        return preLaunchCommand;
    }

    /// The command that will be executed after game exits.
    /// Operating system relevant.
    private final StringProperty postExitCommand = new SimpleStringProperty(this, "postExitCommand", "");

    public StringProperty postExitCommandProperty() {
        return postExitCommand;
    }

    // JVM Options

    /// The user customized arguments passed to JVM.
    private final StringProperty jvmOptions = new SimpleStringProperty(this, "jvmOptions", "");

    public StringProperty jvmOptionsProperty() {
        return jvmOptions;
    }

    ///  True if disallow HMCL use default JVM arguments.
    private final BooleanProperty noJVMArgs = new SimpleBooleanProperty(this, "noJVMArgs", false);

    public BooleanProperty noJVMArgsProperty() {
        return noJVMArgs;
    }

    /// True if HMCL does not check JVM validity.
    private final BooleanProperty notCheckJVM = new SimpleBooleanProperty(this, "notCheckJVM", false);

    public BooleanProperty notCheckJVMProperty() {
        return notCheckJVM;
    }

    // ---

    /// The user customized arguments passed to Minecraft.
    private final StringProperty minecraftArgs = new SimpleStringProperty(this, "minecraftArgs", "");

    public StringProperty minecraftArgsProperty() {
        return minecraftArgs;
    }

    //

    private final StringProperty environmentVariables = new SimpleStringProperty(this, "environmentVariables", "");

    public StringProperty environmentVariablesProperty() {
        return environmentVariables;
    }

    /// True if HMCL does not check game's completeness.
    private final BooleanProperty notCheckGame = new SimpleBooleanProperty(this, "notCheckGame", false);

    public BooleanProperty notCheckGameProperty() {
        return notCheckGame;
    }

    private final BooleanProperty notPatchNatives = new SimpleBooleanProperty(this, "notPatchNatives", false);

    public BooleanProperty notPatchNativesProperty() {
        return notPatchNatives;
    }

    /// True if show the logs after game launched.
    private final BooleanProperty showLogs = new SimpleBooleanProperty(this, "showLogs", false);

    public BooleanProperty showLogsProperty() {
        return showLogs;
    }

    /// The server ip that will be entered after Minecraft successfully loaded ly.
    ///
    /// Format: ip:port or without port.
    private final StringProperty serverIp = new SimpleStringProperty(this, "serverIp", "");

    public StringProperty serverIpProperty() {
        return serverIp;
    }

    private final ObjectProperty<GameDirectoryType> gameDirType = new SimpleObjectProperty<>(this, "gameDirType", GameDirectoryType.ROOT_FOLDER);

    public ObjectProperty<GameDirectoryType> gameDirTypeProperty() {
        return gameDirType;
    }

    /// Your custom gameDir
    private final StringProperty gameDir = new SimpleStringProperty(this, "gameDir", "");

    public StringProperty gameDirProperty() {
        return gameDir;
    }

    private final ObjectProperty<ProcessPriority> processPriority = new SimpleObjectProperty<>(this, "processPriority", ProcessPriority.NORMAL);

    public ObjectProperty<ProcessPriority> processPriorityProperty() {
        return processPriority;
    }

    private final ObjectProperty<Renderer> renderer = new SimpleObjectProperty<>(this, "renderer", Renderer.DEFAULT);

    public ObjectProperty<Renderer> rendererProperty() {
        return renderer;
    }

    private final BooleanProperty useNativeGLFW = new SimpleBooleanProperty(this, "useNativeGLFW", false);

    public BooleanProperty useNativeGLFWProperty() {
        return useNativeGLFW;
    }

    private final BooleanProperty useNativeOpenAL = new SimpleBooleanProperty(this, "useNativeOpenAL", false);

    public BooleanProperty useNativeOpenALProperty() {
        return useNativeOpenAL;
    }

    private final ObjectProperty<VersionIconType> versionIcon = new SimpleObjectProperty<>(this, "versionIcon", VersionIconType.DEFAULT);

    public ObjectProperty<VersionIconType> versionIconProperty() {
        return versionIcon;
    }

}
