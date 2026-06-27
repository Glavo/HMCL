/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for package-managed distribution metadata.
@NotNullByDefault
public final class DistributionMetadataTest {
    /// Tests that missing distribution metadata represents a standalone runtime.
    @Test
    public void loadsStandaloneWhenFileIsMissing() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            DistributionMetadata metadata = DistributionMetadata.load(fileSystem.getPath("/config/distribution.json"));

            assertTrue(metadata.isStandalone());
            assertEquals(DistributionMetadata.PACKAGE_TYPE_STANDALONE, metadata.getPackageType());
            assertEquals(DistributionMetadata.CURRENT_SCHEMA, metadata.getSchema());
        }
    }

    /// Tests loading package metadata provided by a package maintainer.
    @Test
    public void loadsPackageMetadata() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path location = fileSystem.getPath("/config/distribution.json");
            Files.createDirectories(location.getParent());
            Files.writeString(location, """
                    {
                      "$schema": "https://schemas.glavo.site/hmcl/distribution/1.0.0",
                      "packageType": "deb",
                      "packageName": "hmcl",
                      "packageVersion": "3.6.16",
                      "maintainer": "HMCL Debian Packagers"
                    }
                    """);

            DistributionMetadata metadata = DistributionMetadata.load(location);

            assertFalse(metadata.isStandalone());
            assertFalse(metadata.isUnknown());
            assertEquals(DistributionMetadata.CURRENT_SCHEMA, metadata.getSchema());
            assertEquals("deb", metadata.getPackageType());
            assertEquals("hmcl", metadata.getPackageName());
            assertEquals("3.6.16", metadata.getPackageVersion());
            assertEquals("HMCL Debian Packagers", metadata.getMaintainer());
        }
    }

    /// Tests that missing schema markers reject package-managed metadata.
    @Test
    public void returnsUnknownWhenSchemaIsMissing() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path location = fileSystem.getPath("/config/distribution.json");
            Files.createDirectories(location.getParent());
            Files.writeString(location, """
                    {
                      "packageType": "deb"
                    }
                    """);

            DistributionMetadata metadata = DistributionMetadata.load(location);

            assertTrue(metadata.isUnknown());
            assertEquals(DistributionMetadata.PACKAGE_TYPE_UNKNOWN, metadata.getPackageType());
        }
    }

    /// Tests that malformed JSON rejects package-managed metadata.
    @Test
    public void returnsUnknownWhenJsonIsMalformed() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path location = fileSystem.getPath("/config/distribution.json");
            Files.createDirectories(location.getParent());
            Files.writeString(location, "{");

            DistributionMetadata metadata = DistributionMetadata.load(location);

            assertTrue(metadata.isUnknown());
            assertEquals(DistributionMetadata.PACKAGE_TYPE_UNKNOWN, metadata.getPackageType());
        }
    }

    /// Tests that metadata without package type is treated as unknown.
    @Test
    public void returnsUnknownWhenPackageTypeIsMissing() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path location = fileSystem.getPath("/config/distribution.json");
            Files.createDirectories(location.getParent());
            Files.writeString(location, """
                    {
                      "$schema": "https://schemas.glavo.site/hmcl/distribution/1.0.0"
                    }
                    """);

            DistributionMetadata metadata = DistributionMetadata.load(location);

            assertTrue(metadata.isUnknown());
            assertEquals(DistributionMetadata.PACKAGE_TYPE_UNKNOWN, metadata.getPackageType());
        }
    }
}
