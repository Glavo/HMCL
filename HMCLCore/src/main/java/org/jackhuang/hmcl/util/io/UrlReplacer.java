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
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Glavo
 */
public final class UrlReplacer {
    private static final String HTTP = "http";
    private static final String HTTPS = "https";


    private final Map<String, Map<String, Item>> replacements = new HashMap<>();

    {
        replacements.put(HTTP, new LinkedHashMap<>());
        replacements.put(HTTPS, new LinkedHashMap<>());
    }

    public void addHttpOrHttpsReplacement(@NotNull String host, @NotNull String replacement) {
        addHttpOrHttpsReplacement(host, null, replacement);
    }


    public void addHttpOrHttpsReplacement(@NotNull String host, @Nullable String pathPrefix, @NotNull String replacement) {
        var item = new Item(pathPrefix, replacement);
        replacements.get(HTTP).put(host, item);
        replacements.get(HTTPS).put(host, item);
    }

    public void addHttpsReplacement(@NotNull String host, @NotNull String replacement) {
        addHttpsReplacement(host, null, replacement);
    }

    public void addHttpsReplacement(@NotNull String host, @Nullable String pathPrefix, @NotNull String replacement) {
        replacements.get(HTTPS).put(host, new Item(pathPrefix, replacement));
    }

    public String replace(URI uri) {
        Map<String, Item> hostMap = replacements.get(uri.getScheme());
        if (hostMap == null)
            return uri.toString();

        Item item = hostMap.get(uri.getHost());
        if (item == null)
            return uri.toString();

        if (item.pathPrefix != null) {
            if (uri.getPath() == null || !uri.getPath().startsWith(item.pathPrefix))
                return uri.toString();

            return item.replacement + uri.getPath().substring(item.pathPrefix.length());
        } else {
            return item.replacement + uri.getPath();
        }
    }

    private static final class Item {
        private final @Nullable String pathPrefix;
        private final @NotNull String replacement;

        private Item(
                @Nullable String pathPrefix,
                @NotNull String replacement) {
            this.pathPrefix = pathPrefix;
            this.replacement = replacement;
        }
    }
}
