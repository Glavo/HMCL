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

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Coordinates announcement cache loading, remote refreshing, filtering, and dismissal.
@NotNullByDefault
public final class AnnouncementManager {
    /// The default remote announcement feed.
    public static final URI DEFAULT_URL = Metadata.CURRENT_DIRECTORY.resolve("accouncements.json").toUri();

    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(30);
    private static final Path CACHE_PATH = Metadata.HMCL_CURRENT_DIRECTORY.resolve("announcements.json");
    private static final ReadOnlyListWrapper<Announcement> BOARD_ANNOUNCEMENTS = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
    private static final ReadOnlyListWrapper<Announcement> POPUP_ANNOUNCEMENTS = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
    private static final Object LOCK = new Object();
    private static @Nullable AnnouncementCache cache;
    private static boolean initialized;
    private static boolean refreshRunning;

    private AnnouncementManager() {
    }

    /// Initializes the announcement system and starts a background refresh when enabled.
    @FXThread
    public static void init() {
        if (initialized) {
            return;
        }

        ConfigHolder.config().enableAnnouncementsProperty().addListener(observable -> {
            if (ConfigHolder.config().isEnableAnnouncements()) {
                refreshAsync();
            } else {
                updatePublishedAnnouncements(new AnnouncementCache());
            }
        });

        if (ConfigHolder.config().isEnableAnnouncements()) {
            refreshAsync();
        }
    }

    /// @return Active board announcements that should be rendered on the homepage.
    public static ReadOnlyListProperty<Announcement> boardAnnouncementsProperty() {
        return BOARD_ANNOUNCEMENTS.getReadOnlyProperty();
    }

    /// @return Active popup announcements that should be shown at startup.
    public static ReadOnlyListProperty<Announcement> popupAnnouncementsProperty() {
        return POPUP_ANNOUNCEMENTS.getReadOnlyProperty();
    }

    /// Marks an announcement as dismissed and persists the cache.
    ///
    /// @param announcement Announcement to dismiss.
    public static void dismiss(Announcement announcement) {
        String id = announcement.getId();
        if (StringUtils.isBlank(id) || !announcement.isDismissible()) {
            return;
        }

        synchronized (LOCK) {
            AnnouncementCache current = requireCache();
            current.getClosed().add(id);
            cleanupClosed(current);
            saveQuietly(current);
            updatePublishedAnnouncements(current);
        }
    }

    /// Starts an asynchronous refresh if no refresh is currently running.
    public static void refreshAsync() {
        synchronized (LOCK) {
            if (refreshRunning) {
                return;
            }
            refreshRunning = true;
        }

        Task.runAsync("refreshAnnouncements", Schedulers.io(), AnnouncementManager::refresh)
                .whenComplete(Schedulers.javafx(), exception -> {
                    synchronized (LOCK) {
                        refreshRunning = false;
                    }

                    if (exception != null) {
                        LOG.warning("Failed to refresh announcements", exception);
                    }
                })
                .start();
    }

    private static void refresh() throws IOException {
        AnnouncementCache current;
        synchronized (LOCK) {
            current = requireCache();
        }

        if (!Files.isRegularFile(CACHE_PATH)) {
            current = loadCache();
        }

        long now = System.currentTimeMillis();
        URI feedUri = getFeedUri();
        boolean shouldFetch = !NetworkUtils.isHttpUri(feedUri)
                || current.getAnnouncements().isEmpty()
                || now - current.getLastFetchAttemptTime() >= REFRESH_INTERVAL.toMillis();

        if (shouldFetch) {
            current.setLastFetchAttemptTime(now);
            try {
                FetchResult result = fetch(feedUri, current.getEtag());
                if (result.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    saveQuietly(current);
                } else if (result.statusCode == HttpURLConnection.HTTP_OK && result.body != null) {
                    List<Announcement> announcements = JsonUtils.fromNonNullJson(result.body, JsonUtils.listTypeOf(Announcement.class));
                    current.setAnnouncements(announcements.stream()
                            .filter(Announcement::isValid)
                            .collect(Collectors.toList()));
                    current.setEtag(result.etag);
                    current.setLastSuccessfulFetchTime(now);
                    cleanupClosed(current);
                    saveQuietly(current);
                }
            } catch (IOException | JsonParseException e) {
                LOG.warning("Failed to load remote announcements", e);
                saveQuietly(current);
            }
        } else {
            cleanupClosed(current);
        }

        synchronized (LOCK) {
            cache = current;
            updatePublishedAnnouncements(current);
        }
    }

    private static AnnouncementCache requireCache() {
        if (cache == null) {
            cache = loadCache();
        }
        return cache;
    }

    private static AnnouncementCache loadCache() {
        if (!Files.isRegularFile(CACHE_PATH)) {
            return new AnnouncementCache();
        }

        try {
            AnnouncementCache loaded = JsonUtils.fromJsonFile(CACHE_PATH, AnnouncementCache.class);
            if (loaded == null) {
                return new AnnouncementCache();
            }

            cleanupClosed(loaded);
            updatePublishedAnnouncements(loaded);
            return loaded;
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load announcement cache", e);
            return new AnnouncementCache();
        }
    }

    private static URI getFeedUri() {
        try {
            String urlOverride = System.getProperty("hmcl.announcements.url");
            if (StringUtils.isNotBlank(urlOverride))
                return WebURL.parseBrowserInputToURI(urlOverride);
        } catch (Exception e) {
            LOG.warning("Failed to parse announcement feed URL override", e);
        }

        return DEFAULT_URL;

    }

    private static FetchResult fetch(URI uri, @Nullable String etag) throws IOException {
        if (!NetworkUtils.isHttpUri(uri)) {
            return new FetchResult(HttpURLConnection.HTTP_OK, null, Files.readString(Path.of(uri)));
        }

        HttpURLConnection connection = NetworkUtils.createHttpConnection(uri);
        connection.setRequestProperty("Accept", "application/json");
        if (StringUtils.isNotBlank(etag)) {
            connection.setRequestProperty("If-None-Match", etag);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return new FetchResult(responseCode, connection.getHeaderField("ETag"), null);
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            return new FetchResult(responseCode, connection.getHeaderField("ETag"), null);
        }

        return new FetchResult(responseCode, connection.getHeaderField("ETag"), NetworkUtils.readFullyAsString(connection));
    }

    private static void cleanupClosed(AnnouncementCache cache) {
        Instant now = Instant.now();
        Set<String> validIds = cache.getAnnouncements().stream()
                .filter(announcement -> announcement.isActive(now))
                .map(Announcement::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        cache.getClosed().removeIf(Predicate.not(validIds::contains));
    }

    private static void saveQuietly(AnnouncementCache cache) {
        try {
            FileUtils.saveSafely(CACHE_PATH, JsonUtils.GSON.toJson(cache));
        } catch (IOException e) {
            LOG.warning("Failed to save announcement cache", e);
        }
    }

    private static void updatePublishedAnnouncements(AnnouncementCache cache) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updatePublishedAnnouncements(cache));
            return;
        }

        List<Announcement> active = getActiveAnnouncements(cache);
        BOARD_ANNOUNCEMENTS.setAll(active.stream()
                .filter(announcement -> announcement.hasTarget("board"))
                .toList());
        POPUP_ANNOUNCEMENTS.setAll(active.stream()
                .filter(announcement -> announcement.hasTarget("popup"))
                .toList());
    }

    private static @Unmodifiable List<Announcement> getActiveAnnouncements(AnnouncementCache cache) {
        Instant now = Instant.now();
        Set<String> closed = cache.getClosed();
        List<Announcement> initiallyActive = cache.getAnnouncements().stream()
                .filter(announcement -> announcement.isActive(now))
                .filter(announcement -> {
                    String id = announcement.getId();
                    return id != null && !closed.contains(id);
                })
                .sorted(AnnouncementManager::compareAnnouncements)
                .toList();

        Set<String> activeIds = initiallyActive.stream()
                .map(Announcement::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return initiallyActive.stream()
                .filter(announcement -> announcement.getParent() == null || activeIds.contains(announcement.getParent()))
                .toList();
    }

    private static int compareAnnouncements(Announcement first, Announcement second) {
        int severity = Integer.compare(severityRank(second), severityRank(first));
        if (severity != 0) {
            return severity;
        }

        int priority = Integer.compare(second.getPriority(), first.getPriority());
        if (priority != 0) {
            return priority;
        }

        int time = second.getSortTime().compareTo(first.getSortTime());
        if (time != 0) {
            return time;
        }

        return String.valueOf(first.getId()).compareTo(String.valueOf(second.getId()));
    }

    private static int severityRank(Announcement announcement) {
        return switch (announcement.getSeverity()) {
            case "critical" -> 2;
            case "warning" -> 1;
            default -> 0;
        };
    }

    private record FetchResult(int statusCode, @Nullable String etag, @Nullable String body) {
        /// Stores a remote fetch result.
        private FetchResult {
        }
    }
}
