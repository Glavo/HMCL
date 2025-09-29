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

import com.google.gson.*;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.game.Renderer;
import org.jackhuang.hmcl.util.Lang;

import java.util.Locale;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Used to read game settings from old `hmclversion.cfg` and `hmcl.json`.
///
/// @author Glavo
final class GameSettingMigrator {

    private static int parseJsonPrimitive(JsonPrimitive primitive) {
        if (primitive == null)
            return 0;
        else if (primitive.isNumber())
            return primitive.getAsInt();
        else
            return Lang.parseInt(primitive.getAsString(), 0);
    }

    private static final VersionIconType[] VERSION_ICON_TYPES = {
            VersionIconType.DEFAULT,
            VersionIconType.GRASS,
            VersionIconType.CHEST,
            VersionIconType.CHICKEN,
            VersionIconType.COMMAND,
            VersionIconType.OPTIFINE,
            VersionIconType.CRAFT_TABLE,
            VersionIconType.FABRIC,
            VersionIconType.FORGE,
            VersionIconType.NEO_FORGE,
            VersionIconType.FURNACE,
            VersionIconType.QUILT,
            VersionIconType.APRIL_FOOLS,
            VersionIconType.CLEANROOM,
    };

    private static final ProcessPriority[] PROCESS_PRIORITIES = {
            ProcessPriority.LOW,
            ProcessPriority.BELOW_NORMAL,
            ProcessPriority.NORMAL,
            ProcessPriority.ABOVE_NORMAL,
            ProcessPriority.HIGH
    };

    private static final JavaVersionType[] JAVA_VERSION_TYPES = {
            JavaVersionType.DEFAULT,
            JavaVersionType.AUTO,
            JavaVersionType.VERSION,
            JavaVersionType.DETECTED,
            JavaVersionType.CUSTOM
    };

    private static <E extends Enum<E>> E parseJsonPrimitive(JsonPrimitive primitive,
                                                            E[] enumConstants,
                                                            E defaultValue) {
        if (primitive == null || primitive.isJsonNull())
            return defaultValue;
        else if (primitive.isNumber()) {
            int index = primitive.getAsInt();
            if (index >= 0 && index < enumConstants.length)
                return enumConstants[index];
            else {
                LOG.warning("Unknown enum value " + primitive + " for " + enumConstants[0].getClass().getName());
                return defaultValue;
            }
        } else {
            String name = primitive.getAsString();
            for (E enumConstant : enumConstants) {
                if (enumConstant.name().equalsIgnoreCase(name)) {
                    return enumConstant;
                }
            }
            return defaultValue;
        }
    }

    static GameSetting fromJson(JsonObject obj, boolean global) {
        GameSetting setting = global
                ? new GlobalGameSetting(null) // TODO
                : new InstanceGameSetting();

        if (setting instanceof InstanceGameSetting instanceGameSetting) {
            instanceGameSetting.versionIconProperty().set(parseJsonPrimitive(obj.getAsJsonPrimitive("versionIcon"), VERSION_ICON_TYPES, VersionIconType.DEFAULT));

            boolean useGlobal = Optional.ofNullable(obj.get("usesGlobal")).map(JsonElement::getAsBoolean).orElse(false);
            if (!useGlobal) {
                instanceGameSetting.getOverrides().addAll(GameSetting.KNOWN_PARTITIONS.values());
            }
        }

        int maxMemoryN = parseJsonPrimitive(Optional.ofNullable(obj.get("maxMemory")).map(JsonElement::getAsJsonPrimitive).orElse(null));
        if (maxMemoryN <= 0) maxMemoryN = GameSetting.SUGGESTED_MEMORY;

        setting.jvmOptionsProperty().set(Optional.ofNullable(obj.get("javaArgs")).map(JsonElement::getAsString).orElse(""));
        setting.minecraftArgsProperty().set(Optional.ofNullable(obj.get("minecraftArgs")).map(JsonElement::getAsString).orElse(""));
        setting.environmentVariablesProperty().set(Optional.ofNullable(obj.get("environmentVariables")).map(JsonElement::getAsString).orElse(""));
        setting.maxMemoryProperty().set(maxMemoryN);
        setting.minMemoryProperty().set(Optional.ofNullable(obj.get("minMemory")).map(JsonElement::getAsInt).orElse(null));
        setting.autoMemoryProperty().set(Optional.ofNullable(obj.get("autoMemory")).map(JsonElement::getAsBoolean).orElse(true));
        setting.permSizeProperty().set(Optional.ofNullable(obj.get("permSize")).map(JsonElement::getAsString).orElse(""));
        setting.widthProperty().set(Optional.ofNullable(obj.get("width")).map(JsonElement::getAsJsonPrimitive).map(GameSettingMigrator::parseJsonPrimitive).orElse(0));
        setting.heightProperty().set(Optional.ofNullable(obj.get("height")).map(JsonElement::getAsJsonPrimitive).map(GameSettingMigrator::parseJsonPrimitive).orElse(0));
        setting.fullscreenProperty().set(Optional.ofNullable(obj.get("fullscreen")).map(JsonElement::getAsBoolean).orElse(false));
        setting.customJavaPathProperty().set(Optional.ofNullable(obj.get("javaDir")).map(JsonElement::getAsString).orElse(""));
        setting.preLaunchCommandProperty().set(Optional.ofNullable(obj.get("precalledCommand")).map(JsonElement::getAsString).orElse(""));
        setting.postExitCommandProperty().set(Optional.ofNullable(obj.get("postExitCommand")).map(JsonElement::getAsString).orElse(""));
        setting.serverIpProperty().set(Optional.ofNullable(obj.get("serverIp")).map(JsonElement::getAsString).orElse(""));
        setting.commandWrapperProperty().set(Optional.ofNullable(obj.get("wrapper")).map(JsonElement::getAsString).orElse(""));
        setting.runningDirProperty().set(Optional.ofNullable(obj.get("gameDir")).map(JsonElement::getAsString).orElse(""));
        setting.nativesDirProperty().set(Optional.ofNullable(obj.get("nativesDir")).map(JsonElement::getAsString).orElse(""));
        setting.noJVMOptionsProperty().set(Optional.ofNullable(obj.get("noJVMArgs")).map(JsonElement::getAsBoolean).orElse(false));
        setting.notCheckGameProperty().set(Optional.ofNullable(obj.get("notCheckGame")).map(JsonElement::getAsBoolean).orElse(false));
        setting.notCheckJVMProperty().set(Optional.ofNullable(obj.get("notCheckJVM")).map(JsonElement::getAsBoolean).orElse(false));
        setting.notPatchNativesProperty().set(Optional.ofNullable(obj.get("notPatchNatives")).map(JsonElement::getAsBoolean).orElse(false));
        setting.showLogsProperty().set(Optional.ofNullable(obj.get("showLogs")).map(JsonElement::getAsBoolean).orElse(false));
        // TODO: setting.setLauncherVisibility(parseJsonPrimitive(obj.getAsJsonPrimitive("launcherVisibility"), LauncherVisibility.class, LauncherVisibility.HIDE));
        setting.processPriorityProperty().set(parseJsonPrimitive(obj.getAsJsonPrimitive("processPriority"), PROCESS_PRIORITIES, ProcessPriority.NORMAL));
        setting.useNativeGLFWProperty().set(Optional.ofNullable(obj.get("useNativeGLFW")).map(JsonElement::getAsBoolean).orElse(false));
        setting.useNativeOpenALProperty().set(Optional.ofNullable(obj.get("useNativeOpenAL")).map(JsonElement::getAsBoolean).orElse(false));
        // TODO: setting.setGameDirType(parseJsonPrimitive(obj.getAsJsonPrimitive("gameDirType"), GameDirectoryType.class, GameDirectoryType.ROOT_FOLDER));
        setting.defaultJavaPathProperty().set(Optional.ofNullable(obj.get("defaultJavaPath")).map(JsonElement::getAsString).orElse(null));
        // TODO: setting.setNativesDirType(parseJsonPrimitive(obj.getAsJsonPrimitive("nativesDirType"), NativesDirectoryType.class, NativesDirectoryType.VERSION_FOLDER));

        if (obj.get("javaVersionType") != null) {
            JavaVersionType javaVersionType = parseJsonPrimitive(obj.getAsJsonPrimitive("javaVersionType"), JAVA_VERSION_TYPES, JavaVersionType.DEFAULT);
            setting.javaTypeProperty().set(javaVersionType);
            setting.customJavaPathProperty().set(Optional.ofNullable(obj.get("java")).map(JsonElement::getAsString).orElse(null));
        } else {
            String java = Optional.ofNullable(obj.get("java")).map(JsonElement::getAsString).orElse("");
            switch (java) {
                case "Default":
                    setting.javaTypeProperty().set(JavaVersionType.DEFAULT);
                    break;
                case "Auto":
                    setting.javaTypeProperty().set(JavaVersionType.AUTO);
                    break;
                case "Custom":
                    setting.javaTypeProperty().set(JavaVersionType.CUSTOM);
                    break;
                default:
                    setting.customJavaPathProperty().set(java);
            }
        }

        setting.rendererProperty().set(Optional.ofNullable(obj.get("renderer")).map(JsonElement::getAsString)
                .flatMap(name -> {
                    try {
                        return Optional.of(Renderer.valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        return Optional.empty();
                    }
                }).orElseGet(() -> {
                    boolean useSoftwareRenderer = Optional.ofNullable(obj.get("useSoftwareRenderer")).map(JsonElement::getAsBoolean).orElse(false);
                    return useSoftwareRenderer ? Renderer.LLVMPIPE : Renderer.DEFAULT;
                }));

        return setting;
    }

    private GameSettingMigrator() {
    }
}
