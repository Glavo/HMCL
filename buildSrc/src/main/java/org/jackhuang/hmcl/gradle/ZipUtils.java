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
package org.jackhuang.hmcl.gradle;

import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @author Glavo
 */
public final class ZipUtils {

    public static void copyEntries(ZipFile input, ZipOutputStream output,
                                   SignatureBuilder signatureBuilder,
                                   UnaryOperator<String> nameMapper)
            throws IOException {
        if (nameMapper == null)
            nameMapper = UnaryOperator.identity();

        byte[] buffer = new byte[32 * 1024];

        for (ZipEntry entry : Collections.list(input.entries())) {
            String newName = nameMapper.apply(entry.getName());
            if (newName == null)
                continue;

            ZipEntry newEntry = new ZipEntry(newName);
            newEntry.setTime(entry.getTime());
            newEntry.setSize(entry.getSize());
            newEntry.setCrc(entry.getCrc());
            if (entry.getCompressedSize() >= entry.getSize()) {
                newEntry.setMethod(ZipEntry.STORED);
            }

            output.putNextEntry(newEntry);

            if (signatureBuilder != null) {
                signatureBuilder.md.reset();
            }

            try (InputStream inputStream = input.getInputStream(entry)) {
                int n;
                while ((n = inputStream.read(buffer)) > 0) {
                    output.write(buffer, 0, n);
                    if (signatureBuilder != null)
                        signatureBuilder.md.update(buffer, 0, n);
                }
            }

            if (signatureBuilder != null) {
                signatureBuilder.sha512.put(newName, signatureBuilder.md.digest());
                signatureBuilder.md.reset();
            }

            output.closeEntry();
        }
    }

    private ZipUtils() {
    }
}
