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

import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.LowerCaseEnumTypeAdapterFactory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Describes the distribution package that provides the current HMCL runtime.
///
/// The JSON representation is read from `config/distribution.json` under
/// [org.jackhuang.hmcl.Metadata#HMCL_DISTRIBUTION_HOME]. This file is intended to be
/// managed by package maintainers and is not written back by HMCL.
@NotNullByDefault
@JsonSerializable
public record DistributionMetadata(
        @SerializedName(JsonSchema.PROPERTY_SCHEMA)
        JsonSchema schema,
        @Nullable PackageType packageType,
        @Nullable String packageName,
        @Nullable String packageVersion,
        boolean updateManagedExternally
) {
    /// The JSON schema supported by this distribution metadata class.
    public static final JsonSchema CURRENT_SCHEMA = new JsonSchema("distribution", new JsonSchema.Version(1, 0, 0));

    public static final DistributionMetadata DEFAULT = new DistributionMetadata(
            CURRENT_SCHEMA,
            null,
            null,
            null,
            false
    );

    public static @Nullable DistributionMetadata load(Path location) {
        if (!Files.isRegularFile(location))
            return null;

        try {
            JsonObject object = JsonUtils.fromJsonFile(location, JsonObject.class);
            if (object == null)
                return null;

            JsonSchema.CompatibilityResult schemaResult = JsonSchema.check(object, CURRENT_SCHEMA);
            switch (schemaResult.status()) {
                case MISSING -> LOG.warning("Missing schema in distribution metadata: " + location);
                case INVALID -> LOG.warning("Invalid schema in distribution metadata: "
                        + location + ", Actual: " + schemaResult.invalidValue());
                case UNPARSEABLE -> LOG.warning("Unparseable schema in distribution metadata: "
                        + location + ", Actual: " + schemaResult.actual());
                case UNEXPECTED_ID -> LOG.warning("Unexpected distribution metadata schema. Expected: "
                        + CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case UNSUPPORTED_MAJOR -> LOG.warning("Unsupported distribution metadata schema. Expected: "
                        + CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case READ_ONLY_PRESERVE_SCHEMA -> LOG.warning("Newer distribution metadata schema. Expected: "
                        + CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case READ_WRITE, READ_WRITE_PRESERVE_SCHEMA -> {
                }
            }

            if (!schemaResult.readable()) {
                return null;
            }

            return JsonUtils.GSON.fromJson(object, DistributionMetadata.class);
        } catch (Exception e) {
            LOG.warning("Failed to load distribution metadata: " + location, e);
            return null;
        }
    }

    @Override
    public PackageType packageType() {
        return Objects.requireNonNullElse(packageType, PackageType.STANDALONE);
    }

    @JsonAdapter(LowerCaseEnumTypeAdapterFactory.class)
    public enum PackageType {
        STANDALONE("Standalone"),
        DEB("Deb");

        private final String displayName;

        PackageType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
