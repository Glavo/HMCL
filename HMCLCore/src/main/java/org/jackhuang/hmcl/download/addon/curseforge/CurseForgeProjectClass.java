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
package org.jackhuang.hmcl.download.addon.curseforge;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies a top-level Minecraft project category on CurseForge.
///
/// The available project classes can be queried with:
/// ```bash
/// curl 'https://api.curseforge.com/v1/categories?gameId=432&classesOnly=true' -H "x-api-key: $CURSEFORGE_API_KEY"
/// ```
@NotNullByDefault
public enum CurseForgeProjectClass {
    /// Bukkit plugins.
    BUKKIT_PLUGINS(5, "bukkit-plugins", "Bukkit Plugins"),

    /// Minecraft worlds.
    WORLDS(17, "worlds", "Worlds"),

    /// Resource packs.
    RESOURCE_PACKS(12, "texture-packs", "Resource Packs"),

    /// Customization content, including configurations and scripts.
    CUSTOMIZATION(4546, "customization", "Customization"),

    /// Data packs.
    DATA_PACKS(6945, "data-packs", "Data Packs"),

    /// Addons listed in the Minecraft Addons section.
    ADDONS(4559, "mc-addons", "Addons"),

    /// Modpacks.
    MODPACKS(4471, "modpacks", "Modpacks"),

    /// Shader packs.
    SHADERS(6552, "shaders", "Shaders"),

    /// Mods.
    MODS(6, "mc-mods", "Mods");

    /// The category ID used as the API's `classId` parameter.
    private final int id;

    /// The category slug used in CurseForge website URLs.
    private final String slug;

    /// The English category name provided by CurseForge.
    private final String displayName;

    /// Creates a project class with its API ID, website slug, and display name.
    ///
    /// @param id the category ID used as the API's `classId` parameter
    /// @param slug the category slug used in CurseForge website URLs
    /// @param displayName the English category name provided by CurseForge
    CurseForgeProjectClass(int id, String slug, String displayName) {
        this.id = id;
        this.slug = slug;
        this.displayName = displayName;
    }

    /// Returns the category ID used as the API's `classId` parameter.
    ///
    /// @return the project class ID
    public int id() {
        return id;
    }

    /// Returns the category slug used in CurseForge website URLs.
    ///
    /// @return the project class slug
    public String slug() {
        return slug;
    }

    /// Returns the English category name provided by CurseForge.
    ///
    /// @return the project class display name
    public String displayName() {
        return displayName;
    }
}
