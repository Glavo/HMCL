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
package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Task;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.zip.ZipOutputStream;

/**
 * @author Glavo
 */
public final class WorldBackupTask extends Task<Path> {

    private final World world;
    private final Path backupsDir;

    public WorldBackupTask(World world, Path backupsDir) {
        this.world = world;
        this.backupsDir = backupsDir;
    }

    @Override
    public void execute() throws Exception {
        try (FileChannel lockChannel = world.lock()) {
            Files.createDirectories(backupsDir);
            String time = LocalDateTime.now().format(WorldBackupsPage.TIME_FORMATTER);
            String baseName = time + "_" + world.getFileName();
            Path backupFile = null;
            OutputStream outputStream = null;

            int count;
            for (count = 0; count < 256; count++) {
                try {
                    backupFile = backupsDir.resolve(baseName + (count == 0 ? "" : " " + count) + ".zip").toAbsolutePath();
                    outputStream = Files.newOutputStream(backupFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
                    break;
                } catch (FileAlreadyExistsException ignored) {
                }
            }

            if (outputStream == null)
                throw new IOException("Too many attempts");

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(outputStream))) {
                world.export(zipOutputStream, world.getFileName());
            }

            setResult(backupFile);
        }
    }
}
