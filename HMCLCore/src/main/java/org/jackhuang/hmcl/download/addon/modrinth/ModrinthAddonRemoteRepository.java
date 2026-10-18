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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.addon.AddonRemoteRepository;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.concurrent.Semaphore;

@NotNullByDefault
public final class ModrinthAddonRemoteRepository extends AddonRemoteRepository {
    private static final Semaphore SEMAPHORE = new Semaphore(16);

    private static final String PREFIX = "https://api.modrinth.com";
    private static final String BASE = "https://modrinth.com";

    public ModrinthAddonRemoteRepository(DownloadProvider downloadProvider) {
        super(downloadProvider);
    }
}
