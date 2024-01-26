/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.io;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

public final class Unzipper {
    private final Path zipFile;
    private final Path dest;
    private boolean replaceExistentFile = false;
    private boolean terminateIfSubDirectoryNotExists = false;
    private String subDirectory = "/";
    private FileFilter filter;
    private Charset encoding = StandardCharsets.UTF_8;

    /**
     * Decompress the given zip file to a directory.
     *
     * @param zipFile the input zip file to be uncompressed
     * @param destDir the dest directory to hold uncompressed files
     */
    public Unzipper(Path zipFile, Path destDir) {
        this.zipFile = zipFile;
        this.dest = destDir;
    }

    /**
     * True if replace the existent files in destination directory,
     * otherwise those conflict files will be ignored.
     */
    public Unzipper setReplaceExistentFile(boolean replaceExistentFile) {
        this.replaceExistentFile = replaceExistentFile;
        return this;
    }

    /**
     * Will be called for every entry in the zip file.
     * Callback returns false if you want leave the specific file uncompressed.
     */
    public Unzipper setFilter(FileFilter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * Will only uncompress files in the "subDirectory", their path will be also affected.
     *
     * For example, if you set subDirectory to /META-INF, files in /META-INF/ will be
     * uncompressed to the destination directory without creating META-INF folder.
     *
     * Default value: "/"
     */
    public Unzipper setSubDirectory(String subDirectory) {
        this.subDirectory = FileUtils.normalizePath(subDirectory);
        return this;
    }

    public Unzipper setEncoding(Charset encoding) {
        this.encoding = encoding;
        return this;
    }

    public Unzipper setTerminateIfSubDirectoryNotExists() {
        this.terminateIfSubDirectoryNotExists = true;
        return this;
    }

    /**
     * Decompress the given zip file to a directory.
     *
     * @throws IOException if zip file is malformed or filesystem error.
     */
    public void unzip() throws IOException {
        Path destDir = this.dest.toAbsolutePath().normalize();
        Files.createDirectories(destDir);

        boolean subDirectoryNotExists = true;

        try (ZipFile zf = CompressingUtils.openZipFileWithSuitableEncoding(zipFile, encoding)) {
            String root = subDirectory.replace('\\', '/');
            if (!root.startsWith("/")|| (root.length() > 1 && root.endsWith("/")))
                throw new IllegalArgumentException("Subdirectory for unzipper must be absolute");

            root = root.equals("/") ? "" : root.substring(1) + "/";

            CopyOption[] copyOptions = replaceExistentFile ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new CopyOption[]{};
            Enumeration<ZipArchiveEntry> entries = zf.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(root))
                    continue;

                String relativePath = entry.getName().substring(root.length());
                Path destFile = destDir.resolve(relativePath).normalize();
                if (destFile.startsWith(destDir)) {
                    throw new IOException("Invalid entry: " + entry.getName());
                }

                if (filter != null && !filter.accept(entry, destFile, relativePath))
                    continue;

                subDirectoryNotExists = false;

                try (InputStream in = zf.getInputStream(entry)) {
                    Files.copy(in, destFile, copyOptions);
                }
            }
        }

        if (subDirectoryNotExists && !terminateIfSubDirectoryNotExists) {
            throw new IOException("Directory " + subDirectory + " does not exist");
        }
    }

    @FunctionalInterface
    public interface FileFilter {
        boolean accept(ZipArchiveEntry zipEntry, Path destFile, String entryPath) throws IOException;
    }
}
