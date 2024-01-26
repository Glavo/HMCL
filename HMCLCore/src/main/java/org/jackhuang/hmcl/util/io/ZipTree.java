package org.jackhuang.hmcl.util.io;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.Closeable;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Glavo
 */
public final class ZipTree implements Closeable {

    private final ZipFile zipFile;
    private final Dir root = new Dir();

    public ZipTree(ZipFile zipFile) throws IOException {
        this.zipFile = zipFile;

        try {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                addEntry(entries.nextElement());
            }
        } catch (Throwable e) {
            try {
                zipFile.close();
            } catch (IOException e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    private void addEntry(ZipArchiveEntry entry) throws IOException {
        String[] list = entry.getName().split("/");

        for (String item : list) {
            if (item.isEmpty() || item.equals(".") || item.equals("..")) {
                throw new IOException("Invalid name: " + entry.getName());
            }
        }


        Dir dir = root;

        for (int i = 0, end = entry.isDirectory() ? list.length : list.length - 1; i < end; i++) {
            String item = list[i];

            if (dir.files.containsKey(item)) {
                throw new IOException("A file and a directory have the same name: " + entry.getName());
            }

            dir = dir.subDirs.computeIfAbsent(item, name -> new Dir());
        }

        if (entry.isDirectory()) {
            if (dir.entry != null) {
                throw new IOException("Duplicate entry: " + entry.getName());
            }
            dir.entry = entry;
        } else {
            String fileName = list[list.length - 1];

            if (dir.subDirs.containsKey(fileName)) {
                throw new IOException("A file and a directory have the same name: " + entry.getName());
            }

            if (dir.files.containsKey(fileName)) {
                throw new IOException("Duplicate entry: " + entry.getName());
            }

            dir.files.put(fileName, entry);
        }
    }

    public ZipFile getZipFile() {
        return zipFile;
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private static final class Dir {
        ZipArchiveEntry entry;

        final Map<String, Dir> subDirs = new HashMap<>();
        final Map<String, ZipArchiveEntry> files = new HashMap<>();
    }
}
