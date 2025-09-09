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

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.*;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.game.Renderer;
import org.jackhuang.hmcl.util.platform.SystemInfo;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;

/// @author Glavo
public abstract class GameSetting {

    public static final String CURRENT_VERSION = "0";

    private static final int SUGGESTED_MEMORY;

    static {
        double totalMemoryMB = MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize());
        SUGGESTED_MEMORY = totalMemoryMB >= 32768
                ? 8192
                : Integer.max((int) (Math.round(totalMemoryMB / 4.0 / 128.0) * 128), 256);
    }

    @SerializedName("_version")
    private final StringProperty fileVersion = new SimpleStringProperty(this, "fileVersion", CURRENT_VERSION);

    public StringProperty fileVersionProperty() {
        return fileVersion;
    }

    protected transient final Map<String, JsonElement> unknownFields = new LinkedHashMap<>();

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

    /// The user customized arguments passed to JVM.
    @SerializedName("jvmOptions")
    private final StringProperty jvmOptions = new SimpleStringProperty(this, "jvmOptions", "");

    public StringProperty jvmOptionsProperty() {
        return jvmOptions;
    }

    ///  True if disallow HMCL use default JVM options.
    @SerializedName("noJVMOptions")
    private final BooleanProperty noJVMOptions = new SimpleBooleanProperty(this, "noJVMOptions", false);

    public BooleanProperty noJVMOptionsProperty() {
        return noJVMOptions;
    }

    /// True if HMCL does not check JVM validity.
    @SerializedName("notCheckJVM")
    private final BooleanProperty notCheckJVM = new SimpleBooleanProperty(this, "notCheckJVM", false);

    public BooleanProperty notCheckJVMProperty() {
        return notCheckJVM;
    }

    // Memory

    @SerializedName("autoMemory")
    private final BooleanProperty autoMemory = new SimpleBooleanProperty(this, "autoMemory", true);

    public BooleanProperty autoMemoryProperty() {
        return autoMemory;
    }

    /// The maximum memory/MB that JVM can allocate for heap.
    @SerializedName("maxMemory")
    private final IntegerProperty maxMemory = new SimpleIntegerProperty(this, "maxMemory", SUGGESTED_MEMORY);

    public IntegerProperty maxMemoryProperty() {
        return maxMemory;
    }

    /// The minimum memory that JVM can allocate for heap.
    @SerializedName("minMemory")
    private final ObjectProperty<Integer> minMemory = new SimpleObjectProperty<>(this, "minMemory", null);

    public ObjectProperty<Integer> minMemoryProperty() {
        return minMemory;
    }

    /// The permanent generation size of JVM garbage collection.
    @SerializedName("permSize")
    private final StringProperty permSize = new SimpleStringProperty(this, "permSize", "");

    public StringProperty permSizeProperty() {
        return permSize;
    }

    // Game Windows

    @SerializedName("windowsSizeType")
    private final ObjectProperty<GameWindowSizeType> windowsSizeType = new SimpleObjectProperty<>(this, "windowsSizeType", GameWindowSizeType.DEFAULT);

    /// True if Minecraft started in fullscreen mode.
    @SerializedName("fullscreen")
    private final BooleanProperty fullscreen = new SimpleBooleanProperty(this, "fullscreen", false);

    public BooleanProperty fullscreenProperty() {
        return fullscreen;
    }

    /// The width of Minecraft window, defaults 800.
    ///
    /// The field saves int value.
    /// String type prevents unexpected value from JsonParseException.
    /// We can only reset this field instead of recreating the whole setting file.
    @SerializedName("width")
    private final IntegerProperty width = new SimpleIntegerProperty(this, "width", 854);

    public IntegerProperty widthProperty() {
        return width;
    }

    /// The height of Minecraft window, defaults 480.
    ///
    /// The field saves int value.
    /// String type prevents unexpected value from JsonParseException.
    /// We can only reset this field instead of recreating the whole setting file.
    @SerializedName("height")
    private final IntegerProperty height = new SimpleIntegerProperty(this, "height", 480);

    public IntegerProperty heightProperty() {
        return height;
    }

    // ------

    @SerializedName("processPriority")
    private final ObjectProperty<ProcessPriority> processPriority = new SimpleObjectProperty<>(this, "processPriority");

    public ObjectProperty<ProcessPriority> processPriorityProperty() {
        return processPriority;
    }


    @SerializedName("nativesDir")
    private final StringProperty nativesDir = new SimpleStringProperty(this, "nativesDir", "");

    public StringProperty nativesDirProperty() {
        return nativesDir;
    }


    // Logging

    /// True if show the logs after game launched.
    @SerializedName("showLogs")
    private final BooleanProperty showLogs = new SimpleBooleanProperty(this, "showLogs", false);

    public BooleanProperty showLogsProperty() {
        return showLogs;
    }

    // Custom Command

    /// The command to launch java, i.e. optirun.
    @SerializedName("commandWrapper")
    private final StringProperty commandWrapper = new SimpleStringProperty(this, "commandWrapper", "");

    public StringProperty commandWrapperProperty() {
        return commandWrapper;
    }

    /// The command that will be executed before launching the Minecraft.
    /// Operating system relevant.
    @SerializedName("preLaunchCommand")
    private final StringProperty preLaunchCommand = new SimpleStringProperty(this, "preLaunchCommand", "");

    public StringProperty preLaunchCommandProperty() {
        return preLaunchCommand;
    }

    /// The command that will be executed after game exits.
    /// Operating system relevant.
    @SerializedName("postExitCommand")
    private final StringProperty postExitCommand = new SimpleStringProperty(this, "postExitCommand", "");

    public StringProperty postExitCommandProperty() {
        return postExitCommand;
    }


    // ---

    /// The user customized arguments passed to Minecraft.
    @SerializedName("minecraftArgs")
    private final StringProperty minecraftArgs = new SimpleStringProperty(this, "minecraftArgs", "");

    public StringProperty minecraftArgsProperty() {
        return minecraftArgs;
    }

    @SerializedName("environmentVariables")
    private final StringProperty environmentVariables = new SimpleStringProperty(this, "environmentVariables", "");

    public StringProperty environmentVariablesProperty() {
        return environmentVariables;
    }

    // Debug Options

    /// True if HMCL does not check game's completeness.
    @SerializedName("notCheckGame")
    private final BooleanProperty notCheckGame = new SimpleBooleanProperty(this, "notCheckGame", false);

    public BooleanProperty notCheckGameProperty() {
        return notCheckGame;
    }

    @SerializedName("notPatchNatives")
    private final BooleanProperty notPatchNatives = new SimpleBooleanProperty(this, "notPatchNatives", false);

    public BooleanProperty notPatchNativesProperty() {
        return notPatchNatives;
    }

    /// The server ip that will be entered after Minecraft successfully loaded ly.
    ///
    /// Format: ip:port or without port.
    @SerializedName("serverIp")
    private final StringProperty serverIp = new SimpleStringProperty(this, "serverIp", "");

    public StringProperty serverIpProperty() {
        return serverIp;
    }

    /// Your custom gameDir
    @SerializedName("runningDir")
    private final StringProperty runningDir = new SimpleStringProperty(this, "gameDir", "");

    public StringProperty runningDirProperty() {
        return runningDir;
    }

    @SerializedName("renderer")
    private final ObjectProperty<Renderer> renderer = new SimpleObjectProperty<>(this, "renderer", Renderer.DEFAULT);

    public ObjectProperty<Renderer> rendererProperty() {
        return renderer;
    }

    @SerializedName("useNativeGLFW")
    private final BooleanProperty useNativeGLFW = new SimpleBooleanProperty(this, "useNativeGLFW", false);

    public BooleanProperty useNativeGLFWProperty() {
        return useNativeGLFW;
    }

    @SerializedName("useNativeOpenAL")
    private final BooleanProperty useNativeOpenAL = new SimpleBooleanProperty(this, "useNativeOpenAL", false);

    public BooleanProperty useNativeOpenALProperty() {
        return useNativeOpenAL;
    }

}
