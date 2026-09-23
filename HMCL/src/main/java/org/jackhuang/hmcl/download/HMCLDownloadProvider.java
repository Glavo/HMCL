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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// @author Glavo
@NotNullByDefault
public final class HMCLDownloadProvider extends DownloadProvider {
    private static final String BMCLAPI_ROOT = System.getProperty("hmcl.bmclapi.override", "https://bmclapi2.bangbang93.com");

    private volatile DownloadSource versionListSource = DownloadSource.DEFAULT;
    private volatile DownloadSource fileSource = DownloadSource.DEFAULT;

    private List<DownloadCandidate> getVersionListCandidates(
            String defaultUrl, String bmclapiUrl
    ) {
        DownloadSource source = this.versionListSource;
        if (LocaleUtils.IS_CHINA_MAINLAND) {
            return switch (source) {
                case DEFAULT, OFFICIAL -> List.of(
                        DownloadCandidate.of(defaultUrl), DownloadCandidate.of(bmclapiUrl)
                );
                case MIRROR -> List.of(
                        DownloadCandidate.of(bmclapiUrl), DownloadCandidate.of(defaultUrl)
                );
            };
        } else {
            return switch (source) {
                case DEFAULT, OFFICIAL -> List.of(DownloadCandidate.of(defaultUrl));
                case MIRROR -> List.of(
                        DownloadCandidate.of(bmclapiUrl), DownloadCandidate.of(defaultUrl)
                );
            };
        }
    }

    @Override
    protected Task<? extends SortedSet<? extends ComponentRemoteVersion>> fetchVersionsAsync(GameComponentType type, @Nullable GameVersionNumber gameVersion) {
        switch (type) {
            case GAME -> {
                List<DownloadCandidate> candidates = getVersionListCandidates(
                        GameRemoteVersion.VERSION_MANIFEST_URL,
                        BMCLAPI_ROOT + "/mc/game/version_manifest.json"
                );

                return GameRemoteVersion.fetchAsync(candidates);
            }
        }

        return super.fetchVersionsAsync(type, gameVersion);
    }
}

