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
package org.jackhuang.hmcl.theme;

import com.google.gson.*;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
@JsonSerializable
@NotNullByDefault
public record ThemePack(
        LocalizedText name,
        @Nullable LocalizedText author,
        @Nullable LocalizedText description,
        @Unmodifiable Map<String, Theme> themes,
        Path packFile
) {
    public static final int CURRENT_VERSION_MAJOR = 1;
    public static final int CURRENT_VERSION_MINOR = 0;

    public static ThemePack fromJson(Path packFile, JsonElement json) throws JsonParseException {
        if (!(json instanceof JsonObject jsonObject))
            throw new JsonParseException("Expected JSON object, got: " + json);

        String versionString;
        if (jsonObject.get("version") instanceof JsonPrimitive version) {
            versionString = version.getAsString();
            Pattern versionPattern = Pattern.compile("(?<major>[0-9]+)\\.(?<minor>[0-9]+)");

            Matcher matcher = versionPattern.matcher(versionString);
            if (!matcher.matches()) {
                throw new JsonParseException("Invalid theme version format: " + versionString);
            }

            int major;
            int minor;
            try {
                major = Integer.parseInt(matcher.group("major"));
                minor = Integer.parseInt(matcher.group("minor"));
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid theme version format: " + versionString, e);
            }

            if (major > CURRENT_VERSION_MAJOR)
                throw new JsonParseException("Unsupported theme version: " + version.getAsString());

            if (major == CURRENT_VERSION_MAJOR && minor > CURRENT_VERSION_MINOR)
                LOG.warning("Unsupported theme version: " + versionString);
        } else {
            versionString = null;
        }

        LocalizedText name;
        if (jsonObject.get("name") instanceof JsonPrimitive nameJson) {
            name = JsonUtils.GSON.fromJson(nameJson, LocalizedText.class);
        } else {
            throw new JsonParseException("Missing or invalid 'name' field in theme JSON");
        }

        @Nullable LocalizedText author = jsonObject.get("author") instanceof JsonPrimitive authorJson
                ? JsonUtils.GSON.fromJson(authorJson, LocalizedText.class)
                : null;

        @Nullable LocalizedText description = jsonObject.get("description") instanceof JsonPrimitive descriptionJson
                ? JsonUtils.GSON.fromJson(descriptionJson, LocalizedText.class)
                : null;

        Map<String, Theme> themes;
        if (jsonObject.get("themes") instanceof JsonArray themesJson) {
            themes = new LinkedHashMap<>();

            for (JsonElement jsonElement : themesJson.asList()) {
                if (jsonElement instanceof JsonObject themeObject) {
                    Theme theme = JsonUtils.GSON.fromJson(themeObject, Theme.class);
                    if (theme.id().isEmpty() && themesJson.size() != 1) {
                        throw new JsonParseException("Theme ID cannot be empty in 'themes' array");
                    }

                    if (themes.putIfAbsent(theme.id(), theme) != null) {
                        LOG.warning("Duplicate theme ID found in 'themes' array: " + theme.id());
                    }
                } else {
                    throw new JsonParseException("Invalid theme object in 'themes' array");
                }
            }

            themes = Collections.unmodifiableMap(themes);
        } else {
            themes = Map.of();
        }

        return new ThemePack(name, author, description, themes, packFile);
    }
}
