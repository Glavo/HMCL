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
package org.jackhuang.hmcl.util.io;

import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Glavo
 */
public final class MemoryOutputStream extends OutputStream {

    private static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private byte[] buf;
    private int count;

    public MemoryOutputStream() {
        this(32);
    }

    public MemoryOutputStream(int size) {
        if (size < 0)
            throw new IllegalArgumentException("Negative initial size: " + size);

        buf = new byte[size];
    }

    private void ensureCapacity(int minCapacity) {
        int oldCapacity = buf.length;
        if (minCapacity <= oldCapacity)
            return;

        int minGrowth = minCapacity - oldCapacity;
        int prefLength = oldCapacity + Math.max(minGrowth, oldCapacity);
        int newCapacity = prefLength > 0 && prefLength <= SOFT_MAX_ARRAY_LENGTH
                ? prefLength
                : Math.max(minCapacity, SOFT_MAX_ARRAY_LENGTH);
        buf = Arrays.copyOf(buf, newCapacity);
    }

    public int size() {
        return count;
    }

    public byte[] getArray() {
        return buf;
    }

    @Override
    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count++] = (byte) b;
    }

    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    public void copyFrom(InputStream inputStream) throws IOException {
        int available = inputStream.available();
        if (available > 0) {
            ensureCapacity((int) Math.min((long) count + available, SOFT_MAX_ARRAY_LENGTH));
        }

        while (true) {
            int maxRead = buf.length - count;

            if (maxRead > 0) {
                int n = inputStream.read(buf, count, maxRead);
                if (n == -1)
                    return;

                if (n > maxRead)
                    throw new IOException("Unreachable: The bytes read exceed the remaining capacity of the array");

                count += n;
            } else {
                int b = inputStream.read();
                if (b < 0)
                    return;

                ensureCapacity(count + 1);
                buf[count++] = (byte) b;
            }
        }
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    public byte[] toByteArrayNoCopy() {
        return buf.length == count ? buf : Arrays.copyOf(buf, count);
    }

    @Override
    public String toString() {
        return new String(buf, 0, count, StandardCharsets.UTF_8);
    }

    public String toString(Charset charset) {
        return new String(buf, 0, count, charset);
    }

    public ByteArrayInputStream toInputStream() {
        return new ByteArrayInputStream(buf, 0, count);
    }
}
