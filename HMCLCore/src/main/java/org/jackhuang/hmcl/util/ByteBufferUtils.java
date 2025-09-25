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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntFunction;

/// @author Glavo
public final class ByteBufferUtils {

    public static final IntFunction<ByteBuffer[]> BYTE_BUFFER_ARRAY_GENERATOR = ByteBuffer[]::new;

    public static long getRemaining(Iterable<ByteBuffer> buffers) {
        long remaining = 0;
        for (ByteBuffer buffer : buffers) {
            remaining += buffer.remaining();
        }
        return remaining;
    }

    private ByteBufferUtils() {
    }
}
