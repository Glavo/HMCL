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

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * This utility class consists of some util methods operating on InputStream/OutputStream.
 *
 * @author huangyuhui
 */
public final class IOUtils {

    private IOUtils() {
    }

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    public static MemoryOutputStream readFullyWithoutClosing(InputStream stream) throws IOException {
        MemoryOutputStream result = new MemoryOutputStream(Math.max(stream.available(), 32));
        result.copyFrom(stream);
        return result;
    }

    public static String readFullyAsStringWithClosing(InputStream stream) throws IOException {
        return readFullyWithoutClosing(stream).toString();
    }

    /**
     * Read all bytes to a buffer from given input stream, and close the input stream finally.
     *
     * @param stream the InputStream being read, closed finally.
     * @return all bytes read from the stream
     * @throws IOException if an I/O error occurs.
     */
    public static MemoryOutputStream readFully(InputStream stream) throws IOException {
        try {
            MemoryOutputStream result = new MemoryOutputStream(Math.max(stream.available(), 32));
            result.copyFrom(stream);
            return result;
        } finally {
            stream.close();
        }
    }

    public static byte[] readFullyAsByteArray(InputStream stream) throws IOException {
        return readFully(stream).toByteArrayNoCopy();
    }

    public static String readFullyAsString(InputStream stream) throws IOException {
        return readFully(stream).toString();
    }

    public static void copyTo(InputStream src, OutputStream dest) throws IOException {
        copyTo(src, dest, new byte[DEFAULT_BUFFER_SIZE]);
    }

    public static void copyTo(InputStream src, OutputStream dest, byte[] buf) throws IOException {
        while (true) {
            int len = src.read(buf);
            if (len == -1)
                break;
            dest.write(buf, 0, len);
        }
    }

    public static InputStream wrapFromGZip(InputStream inputStream) throws IOException {
        return new GZIPInputStream(inputStream);
    }

    public static void closeQuietly(AutoCloseable closeable) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (Throwable ignored) {
        }
    }

    public static void closeQuietly(AutoCloseable closeable, Throwable exception) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (Throwable e) {
            exception.addSuppressed(e);
        }
    }
}
