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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
public final class DistributionMetadata {
    /// The package type used when no package-managed distribution metadata exists.
    public static final String PACKAGE_TYPE_STANDALONE = "standalone";

    /// The package type used when a distribution metadata file exists but cannot be read safely.
    public static final String PACKAGE_TYPE_UNKNOWN = "unknown";

    /// The JSON schema supported by this distribution metadata class.
    public static final JsonSchema CURRENT_SCHEMA = new JsonSchema("distribution", new JsonSchema.Version(1, 0, 0));

    /// The JSON property name for the package manager or packaging format.
    private static final String PROPERTY_PACKAGE_TYPE = "packageType";

    /// The schema used by this distribution metadata file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private JsonSchema schema = CURRENT_SCHEMA;

    /// The package manager or packaging format that provides HMCL.
    @SerializedName(PROPERTY_PACKAGE_TYPE)
    private @Nullable String packageType = PACKAGE_TYPE_STANDALONE;

    /// The package name used by the package manager.
    @SerializedName("packageName")
    private @Nullable String packageName;

    /// The package version used by the package manager.
    @SerializedName("packageVersion")
    private @Nullable String packageVersion;

    /// The package maintainer name or identifier.
    @SerializedName("maintainer")
    private @Nullable String maintainer;

    /// Creates standalone distribution metadata.
    public DistributionMetadata() {
    }

    /// Creates standalone distribution metadata.
    public static DistributionMetadata standalone() {
        return new DistributionMetadata();
    }

    /// Creates unknown distribution metadata for invalid package-managed metadata.
    public static DistributionMetadata unknown() {
        DistributionMetadata metadata = new DistributionMetadata();
        metadata.setPackageType(PACKAGE_TYPE_UNKNOWN);
        return metadata;
    }

    /// Loads distribution metadata from the given JSON file.
    ///
    /// @param location the distribution metadata file location
    /// @return the loaded distribution metadata, standalone metadata when the file is absent,
    ///         or unknown metadata when the file exists but cannot be read safely
    public static DistributionMetadata load(Path location) {
        Objects.requireNonNull(location);

        if (!Files.exists(location)) {
            return standalone();
        }

        JsonObject jsonObject;
        try {
            jsonObject = JsonUtils.fromJsonFile(location, JsonObject.class);
        } catch (JsonParseException e) {
            LOG.warning("Malformed distribution metadata: " + location, e);
            return unknown();
        } catch (IOException e) {
            LOG.warning("Failed to read distribution metadata: " + location, e);
            return unknown();
        }

        if (jsonObject == null) {
            LOG.warning("Distribution metadata is empty: " + location);
            return unknown();
        }

        JsonSchema.CompatibilityResult schemaResult = JsonSchema.check(jsonObject, CURRENT_SCHEMA);
        logSchemaCompatibility(location, schemaResult);
        if (!schemaResult.readable()) {
            return unknown();
        }

        if (!hasValidPackageType(jsonObject)) {
            LOG.warning("Missing or invalid package type in distribution metadata: " + location);
            return unknown();
        }

        try {
            @Nullable DistributionMetadata metadata = JsonUtils.GSON.fromJson(jsonObject, DistributionMetadata.class);
            if (metadata == null) {
                LOG.warning("Distribution metadata deserialized to null: " + location);
                return unknown();
            }

            if (!schemaResult.preserveSchema() && !CURRENT_SCHEMA.equals(metadata.getSchema())) {
                metadata.setSchema(CURRENT_SCHEMA);
            }
            metadata.normalize();
            return metadata;
        } catch (JsonParseException e) {
            LOG.warning("Failed to parse distribution metadata: " + location, e);
            return unknown();
        }
    }

    /// Returns whether the JSON object contains a readable package type.
    private static boolean hasValidPackageType(JsonObject jsonObject) {
        JsonElement element = jsonObject.get(PROPERTY_PACKAGE_TYPE);
        return element instanceof JsonPrimitive primitive
                && primitive.isString()
                && !primitive.getAsString().isBlank();
    }

    /// Logs schema compatibility issues for a distribution metadata file.
    private static void logSchemaCompatibility(Path location, JsonSchema.CompatibilityResult schemaResult) {
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
    }

    /// Normalizes nullable or blank fields after deserialization.
    private void normalize() {
        if (packageType == null || packageType.isBlank()) {
            packageType = PACKAGE_TYPE_UNKNOWN;
        }
        if (packageName != null && packageName.isBlank()) {
            packageName = null;
        }
        if (packageVersion != null && packageVersion.isBlank()) {
            packageVersion = null;
        }
        if (maintainer != null && maintainer.isBlank()) {
            maintainer = null;
        }
    }

    /// Returns the schema used by this distribution metadata file.
    public JsonSchema getSchema() {
        return schema;
    }

    /// Sets the schema used by this distribution metadata file.
    ///
    /// @param schema the schema to store
    public void setSchema(JsonSchema schema) {
        this.schema = Objects.requireNonNull(schema);
    }

    /// Returns the package manager or packaging format that provides HMCL.
    public String getPackageType() {
        return packageType != null ? packageType : PACKAGE_TYPE_UNKNOWN;
    }

    /// Sets the package manager or packaging format that provides HMCL.
    ///
    /// @param packageType the package type to store
    public void setPackageType(String packageType) {
        this.packageType = Objects.requireNonNull(packageType);
        normalize();
    }

    /// Returns whether this metadata describes a standalone HMCL runtime.
    public boolean isStandalone() {
        return PACKAGE_TYPE_STANDALONE.equals(getPackageType());
    }

    /// Returns whether this metadata was loaded from invalid package-managed metadata.
    public boolean isUnknown() {
        return PACKAGE_TYPE_UNKNOWN.equals(getPackageType());
    }

    /// Returns the package name used by the package manager.
    public @Nullable String getPackageName() {
        return packageName;
    }

    /// Sets the package name used by the package manager.
    ///
    /// @param packageName the package name to store, or `null` when unspecified
    public void setPackageName(@Nullable String packageName) {
        this.packageName = packageName;
        normalize();
    }

    /// Returns the package version used by the package manager.
    public @Nullable String getPackageVersion() {
        return packageVersion;
    }

    /// Sets the package version used by the package manager.
    ///
    /// @param packageVersion the package version to store, or `null` when unspecified
    public void setPackageVersion(@Nullable String packageVersion) {
        this.packageVersion = packageVersion;
        normalize();
    }

    /// Returns the package maintainer name or identifier.
    public @Nullable String getMaintainer() {
        return maintainer;
    }

    /// Sets the package maintainer name or identifier.
    ///
    /// @param maintainer the maintainer to store, or `null` when unspecified
    public void setMaintainer(@Nullable String maintainer) {
        this.maintainer = maintainer;
        normalize();
    }
}
