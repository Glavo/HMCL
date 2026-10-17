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
package org.jackhuang.hmcl.download.addon.modrinth;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.addon.AddonRemoteProject;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;

/// @param id The ID of the project, encoded as a base62 string.
/// @param team The ID of the team that has ownership of this project.
/// @param title The title or name of the project.
/// @param description A short sentence summarizing the project, no more than a sentence or two.
/// @param body A long form description of the project.
/// @param status The status of the project. Allowed values: approved, archived,
///               rejected, draft, unlisted, processing, withheld, scheduled, private, unknown.
/// @param projectType The project type of the project. Allowed values: mod, modpack, resourcepack shader.
/// @param categories A list of the featured categories that the project has.
/// @param environment All the environments that versions of this project support.
/// @param versions A list of the version IDs of the project.
/// @param published The date the project was created.
/// @param updated The date the latest version of the project was created.
/// @param downloads The total number of downloads of the project.
/// @param slug The slug of a project, used for vanity URLs. Regex: `^[\w!@$()`.+,"\-']{3,64}$`.
///
/// @see <a href="https://docs.modrinth.com/api/operations/getproject/">Get a project - Modrinth Document</a>
@JsonSerializable
@NotNullByDefault
public record ModrinthAddonRemoteProject(
        String id,
        String team,
        String title,
        String description,
        String body,
        String status,
        @SerializedName("project_type")
        String projectType,
        @Unmodifiable List<String> categories,
        @SerializedName("additional_categories")
        @Unmodifiable List<String> additionalCategories,
        String environment,
        @Unmodifiable List<String> versions,
        Instant published,
        Instant updated,
        int downloads,
        @Nullable String slug,
        @SerializedName("icon_url")
        @Nullable String iconUrl) implements AddonRemoteProject  {
}
