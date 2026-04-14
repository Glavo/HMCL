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
package org.jackhuang.hmcl.setting.property;

import com.google.gson.*;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Objects;

/// @author Glavo
@JsonSerializable
public record SettingGroup(@NotNull String name) {

    public SettingGroup {
        Objects.requireNonNull(name, "name");
    }

    public static final class Adapter
            implements JsonDeserializer<SettingGroup>, JsonSerializer<SettingGroup> {

        @Override
        public SettingGroup deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (json instanceof JsonPrimitive primitive) {
                return new SettingGroup(primitive.getAsString());
            }
            throw new JsonParseException("Expected JsonPrimitive, got " + json);
        }

        @Override
        public JsonElement serialize(SettingGroup src, Type typeOfSrc, JsonSerializationContext context) {
            return src != null ? new JsonPrimitive(src.name) : JsonNull.INSTANCE;
        }
    }
}
