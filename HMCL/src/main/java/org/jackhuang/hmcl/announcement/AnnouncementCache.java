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
package org.jackhuang.hmcl.announcement;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Local cache persisted in `.hmcl/announcements.json`.
@NotNullByDefault
final class AnnouncementCache {
    /// Last time when HMCL attempted to refresh announcements.
    @SerializedName("lastFetchAttemptTime")
    private long lastFetchAttemptTime;

    /// Last time when HMCL successfully received a fresh announcement list.
    @SerializedName("lastSuccessfulFetchTime")
    private long lastSuccessfulFetchTime;

    /// Last modified timestamp returned by the announcement feed server.
    @SerializedName("lastModifiedTime")
    private long lastModifiedTime;

    /// Announcement IDs dismissed by the user.
    @SerializedName("closed")
    private @Nullable Set<UUID> closed;

    /// Last announcement list received from the server.
    @SerializedName("announcements")
    private @Nullable List<Announcement> announcements;

    /// @return Last fetch attempt timestamp in milliseconds since epoch.
    long getLastFetchAttemptTime() {
        return lastFetchAttemptTime;
    }

    /// @param lastFetchAttemptTime Last fetch attempt timestamp in milliseconds since epoch.
    void setLastFetchAttemptTime(long lastFetchAttemptTime) {
        this.lastFetchAttemptTime = lastFetchAttemptTime;
    }

    /// @param lastSuccessfulFetchTime Last successful fetch timestamp in milliseconds since epoch.
    void setLastSuccessfulFetchTime(long lastSuccessfulFetchTime) {
        this.lastSuccessfulFetchTime = lastSuccessfulFetchTime;
    }

    /// @return Last modified timestamp in milliseconds since epoch.
    long getLastModifiedTime() {
        return lastModifiedTime;
    }

    /// @param lastModifiedTime Last modified timestamp in milliseconds since epoch.
    void setLastModifiedTime(long lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    /// @return Mutable dismissed announcement IDs.
    Set<UUID> getClosed() {
        if (closed == null) {
            closed = new HashSet<>();
        }
        return closed;
    }

    /// @return Mutable cached announcements.
    List<Announcement> getAnnouncements() {
        if (announcements == null) {
            announcements = new ArrayList<>();
        }
        return announcements;
    }

    /// @param announcements The latest announcement list.
    void setAnnouncements(List<Announcement> announcements) {
        this.announcements = new ArrayList<>(announcements);
    }
}
