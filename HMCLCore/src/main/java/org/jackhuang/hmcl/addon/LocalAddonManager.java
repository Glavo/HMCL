/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/// Manages filesystem-backed add-ons belonging to one game instance.
///
/// Subclasses derive manifest data and paths from the supplied instance. A higher-level stable
/// instance may expose newer delegated state to later queries, but subclasses may cache derived
/// paths or metadata and therefore need to be recreated after the applicable policy changes.
@NotNullByDefault
public abstract class LocalAddonManager<T extends LocalAddonFile> {
    /// File-name suffix used for a disabled add-on.
    public static final String DISABLED_EXTENSION = ".disabled";

    /// File-name suffix used for an add-on retained as an old version.
    public static final String OLD_EXTENSION = ".old";

    /// Returns an add-on's logical name without manager-owned state suffixes.
    ///
    /// @param file the add-on path
    /// @return the file name without `.disabled` or `.old`
    public static String getLocalAddonName(Path file) {
        return StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
    }

    /// Serializes mutable manager state and filesystem operations.
    protected final ReentrantLock lock = new ReentrantLock();

    /// Local add-ons discovered by the most recent refresh.
    protected final Set<T> localFiles = new LinkedHashSet<>();

    /// Instance whose add-ons are managed.
    protected final GameInstance instance;

    /// Creates a manager for one game instance.
    ///
    /// @param instance the instance whose add-ons are managed
    protected LocalAddonManager(GameInstance instance) {
        this.instance = instance;
    }

    /// Returns the managed instance.
    ///
    /// @return the managed instance
    public GameInstance getInstance() {
        return instance;
    }

    /// Returns the managed instance ID.
    ///
    /// @return the managed instance ID
    public GameInstanceID getInstanceId() {
        return instance.getId();
    }

    /// Returns the directory scanned and modified by this manager.
    ///
    /// @return the add-on directory
    public abstract Path getDirectory();

    /// Reloads add-ons from disk, replacing the manager's in-memory state.
    ///
    /// @throws IOException if the add-on directory cannot be read
    public abstract void refresh() throws IOException;

    /// Returns the ordering applied to values returned by [#getLocalFiles()].
    ///
    /// @return the local add-on comparator
    public abstract Comparator<T> getComparator();

    /// Returns a sorted immutable snapshot of local add-ons.
    ///
    /// @return the sorted add-ons
    /// @throws IOException if a subclass must refresh and cannot read its directory
    public @Unmodifiable List<T> getLocalFiles() throws IOException {
        lock.lock();
        try {
            return localFiles.stream().sorted(getComparator()).toList();
        } finally {
            lock.unlock();
        }
    }

    /// Marks or unmarks an add-on as an old retained version.
    ///
    /// The path is renamed only when the source file exists. The in-memory local-file set is
    /// updated to match the requested state.
    ///
    /// @param modFile the add-on to update
    /// @param old     whether to mark it as old
    /// @return the target path
    /// @throws IOException if the file cannot be renamed
    public Path setOld(T modFile, boolean old) throws IOException {
        lock.lock();
        try {
            Path newPath;
            if (old) {
                newPath = backupFile(modFile.getFile());
                localFiles.remove(modFile);
            } else {
                newPath = restoreFile(modFile.getFile());
                localFiles.add(modFile);
            }
            return newPath;
        } finally {
            lock.unlock();
        }
    }

    /// Renames an add-on path to use the old-version suffix.
    ///
    /// @param file the source path
    /// @return the target path
    /// @throws IOException if the existing source cannot be moved
    private Path backupFile(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.addSuffix(
                        StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION),
                        OLD_EXTENSION
                )
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }

    /// Removes the old-version suffix from an add-on path.
    ///
    /// @param file the source path
    /// @return the target path
    /// @throws IOException if the existing source cannot be moved
    private Path restoreFile(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.removeSuffix(FileUtils.getName(file), OLD_EXTENSION)
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }
}
