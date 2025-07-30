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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.UnsupportedPlatformException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Glavo
 */
public enum ExecutableFileType {
    EXE("exe", EnumSet.of(OperatingSystem.WINDOWS)),
    BAT("bat", EnumSet.of(OperatingSystem.WINDOWS)),
    MSI("msi", EnumSet.of(OperatingSystem.WINDOWS)),
    MSIX("msix", EnumSet.of(OperatingSystem.WINDOWS)),

    APPIMAGE("AppImage", EnumSet.of(OperatingSystem.LINUX)),
    ;

    public static @Nullable ExecutableFileType detect(@NotNull Path file) {
        String extension = FileUtils.getExtension(file);
        if (StringUtils.isBlank(extension)) {
            return null;
        }

        for (ExecutableFileType type : values()) {
            if (type.extension.equalsIgnoreCase(extension)) {
                return type;
            }
        }

        return null;
    }

    private final String extension;
    private final EnumSet<OperatingSystem> supportedOperatingSystems;

    ExecutableFileType(String extension, EnumSet<OperatingSystem> supportedOperatingSystems) {
        this.extension = extension;
        this.supportedOperatingSystems = supportedOperatingSystems;
    }

    public boolean isCompatible(@NotNull Platform platform) {
        return supportedOperatingSystems.contains(platform.getOperatingSystem());
    }

    public void execute(@NotNull Path file, @NotNull List<String> args) throws IOException, InterruptedException, UnsupportedPlatformException {
        if (!isCompatible(Platform.SYSTEM_PLATFORM))
            throw new UnsupportedPlatformException();

        var commands = new ArrayList<String>(args.size() + 1);
        commands.add(file.toAbsolutePath().normalize().toString());
        commands.addAll(args);
        SystemUtils.callExternalProcess(commands);
    }
}
