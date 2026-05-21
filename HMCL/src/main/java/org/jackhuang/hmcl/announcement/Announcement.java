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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// A remote announcement entry loaded from the public announcements feed.
@NotNullByDefault
public final class Announcement {
    /// Unique announcement identifier.
    @SerializedName("id")
    private @Nullable String id;

    /// Localized announcement title.
    @SerializedName("title")
    private @Nullable JsonElement title;

    /// Localized HTML body. This field is mutually exclusive with [#link].
    @SerializedName("content")
    private @Nullable JsonElement content;

    /// Localized external body link. This field is mutually exclusive with [#content].
    @SerializedName("link")
    private @Nullable JsonElement link;

    /// Single display target kept for compatibility with the original design.
    @SerializedName("type")
    private @Nullable String type;

    /// Multiple display targets used by the improved schema.
    @SerializedName("targets")
    private @Nullable List<String> targets;

    /// Sorting priority. Higher values are shown earlier.
    @SerializedName("priority")
    private int priority;

    /// Visual importance of the announcement.
    @SerializedName("severity")
    private @Nullable String severity;

    /// Optional parent announcement ID.
    @SerializedName("parent")
    private @Nullable String parent;

    /// Optional ISO-8601 instant when the announcement starts being active.
    @SerializedName("startsAt")
    private @Nullable String startsAt;

    /// Optional ISO-8601 instant when the announcement stops being active.
    @SerializedName("expiresAt")
    private @Nullable String expiresAt;

    /// Optional minimum HMCL version.
    @SerializedName("minVersion")
    private @Nullable String minVersion;

    /// Optional maximum HMCL version.
    @SerializedName("maxVersion")
    private @Nullable String maxVersion;

    /// Optional operating systems allowed to receive this announcement.
    @SerializedName("platforms")
    private @Nullable List<String> platforms;

    /// Optional HMCL build channels allowed to receive this announcement.
    @SerializedName("channels")
    private @Nullable List<String> channels;

    /// Whether users may dismiss this announcement.
    @SerializedName("dismissible")
    private @Nullable Boolean dismissible;

    /// Whether a popup announcement should be marked closed after the first confirmation.
    @SerializedName("showOnce")
    private @Nullable Boolean showOnce;

    /// Whether the popup requires explicit acknowledgement.
    @SerializedName("ackRequired")
    private @Nullable Boolean ackRequired;

    /// @return The unique announcement identifier.
    public @Nullable String getId() {
        return id;
    }

    /// @return The sorting priority.
    public int getPriority() {
        return priority;
    }

    /// @return The normalized severity.
    public String getSeverity() {
        return StringUtils.isBlank(severity) ? "info" : severity.toLowerCase(Locale.ROOT);
    }

    /// @return The parent announcement ID, or `null` when there is no parent.
    public @Nullable String getParent() {
        return parent;
    }

    /// @return Whether users may dismiss this announcement.
    public boolean isDismissible() {
        return dismissible == null || dismissible;
    }

    /// @return Whether this popup should be closed after first confirmation.
    public boolean isShowOnce() {
        return showOnce == null || showOnce;
    }

    /// @return Whether the popup requires explicit acknowledgement.
    public boolean isAckRequired() {
        return ackRequired == null || ackRequired;
    }

    /// @return The localized title, or the announcement ID if no title is available.
    public String getLocalizedTitle() {
        String localized = localize(title);
        return StringUtils.isBlank(localized) ? String.valueOf(id) : localized;
    }

    /// @return The localized HTML content, or `null` when the announcement uses a link.
    public @Nullable String getLocalizedContent() {
        return localize(content);
    }

    /// @return The localized external content link, or `null` when inline content is used.
    public @Nullable String getLocalizedLink() {
        return localize(link);
    }

    /// @return Whether this announcement targets the given surface.
    public boolean hasTarget(String target) {
        List<String> targets = this.targets;
        if (targets != null && targets.stream().anyMatch(target::equalsIgnoreCase)) {
            return true;
        }

        return type != null && type.equalsIgnoreCase(target);
    }

    /// @return Whether this announcement is structurally valid enough to display.
    public boolean isValid() {
        if (StringUtils.isBlank(id) || StringUtils.isBlank(getLocalizedTitle())) {
            return false;
        }

        boolean hasContent = StringUtils.isNotBlank(getLocalizedContent());
        boolean hasLink = StringUtils.isNotBlank(getLocalizedLink());
        return hasContent != hasLink && (hasTarget("board") || hasTarget("popup"));
    }

    /// @return Whether this announcement should be considered active now.
    public boolean isActive(Instant now) {
        Instant start = parseInstant(startsAt);
        if (start != null && now.isBefore(start)) {
            return false;
        }

        Instant expiration = parseInstant(expiresAt);
        if (expiration != null && !now.isBefore(expiration)) {
            return false;
        }

        return isVersionAllowed()
                && isPlatformAllowed()
                && isChannelAllowed();
    }

    /// @return The instant used for secondary sorting.
    public Instant getSortTime() {
        Instant start = parseInstant(startsAt);
        return start == null ? Instant.EPOCH : start;
    }

    private boolean isVersionAllowed() {
        VersionNumber current = VersionNumber.asVersion(Metadata.VERSION);
        if (StringUtils.isNotBlank(minVersion) && current.compareTo(VersionNumber.asVersion(minVersion)) < 0) {
            return false;
        }

        return StringUtils.isBlank(maxVersion) || current.compareTo(VersionNumber.asVersion(maxVersion)) <= 0;
    }

    private boolean isPlatformAllowed() {
        return containsIgnoreCaseOrEmpty(platforms, OperatingSystem.CURRENT_OS.getCheckedName());
    }

    private boolean isChannelAllowed() {
        return containsIgnoreCaseOrEmpty(channels, Metadata.BUILD_CHANNEL);
    }

    private static boolean containsIgnoreCaseOrEmpty(@Nullable List<String> values, String expected) {
        return values == null || values.isEmpty() || values.stream().anyMatch(expected::equalsIgnoreCase);
    }

    private static @Nullable Instant parseInstant(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static @Nullable String localize(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }

        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        Locale locale = Locale.getDefault();
        for (String key : localeKeys(locale)) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }

        for (var entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }

        return null;
    }

    private static @Unmodifiable List<String> localeKeys(Locale locale) {
        String languageTag = locale.toLanguageTag();
        String underscoreTag = languageTag.replace('-', '_');
        String language = locale.getLanguage();

        if (StringUtils.isBlank(language)) {
            return List.of("default");
        }

        return List.of(languageTag, underscoreTag, language, "default");
    }

    /// @return The set of currently known severity values.
    public static @Unmodifiable Set<String> knownSeverities() {
        return Set.of("info", "warning", "critical");
    }
}
