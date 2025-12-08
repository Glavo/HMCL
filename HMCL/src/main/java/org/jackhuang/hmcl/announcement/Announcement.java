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
package org.jackhuang.hmcl.announcement;

import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/// @author Glavo
@JsonSerializable
public record Announcement(
        @NotNull UUID id,
        @Nullable UUID parent,
        @NotNull LocalizedText title,
        @Nullable LocalizedText content,
        @Nullable LocalizedText link,
        @Nullable Severity severity,
        @Nullable Type type,
        int priority
) implements Comparable<Announcement> {

    @Override
    public int compareTo(@NotNull Announcement that) {
        int c = Integer.compare(this.priority, that.priority);
        if (c != 0)
            return c;

        return this.id.compareTo(that.id);
    }

    @JsonSerializable
    public enum Type {
        BOARD, POPUP
    }

    @JsonSerializable
    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
