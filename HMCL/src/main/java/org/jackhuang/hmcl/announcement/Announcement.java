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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.CompatibilityRule;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// A remote announcement entry loaded from the public announcements feed.
///
/// The JSON structure is:
///
/// ```json
/// {
///   "id": "019976b2-ad49-7451-b090-83b66c532cdd",
///   "title": {
///     "default": "Announcement title",
///     "zh": "Announcement title in Chinese"
///   },
///   "content": {
///     "type": "html",
///     "value": {
///       "default": "<p>Announcement body</p>",
///       "zh": "<p>Announcement body in Chinese</p>"
///     }
///   },
///   "targets": ["board", "popup"],
///   "categories": ["general"],
///   "priority": 0,
///   "severity": "info",
///   "parent": "019976b2-ad49-7451-b090-83b66c532cdc",
///   "startsAt": "2026-01-01T00:00:00Z",
///   "expiresAt": "2027-01-01T00:00:00Z",
///   "rules": [
///     {
///       "action": "allow",
///       "os": {
///         "name": "windows"
///       },
///       "features": {
///         "hmcl_channel_stable": true
///       }
///     }
///   ],
///   "dismissible": true,
///   "showOnce": true,
///   "ackRequired": true
/// }
/// ```
///
/// `title` and `content.value` use [LocalizedText], so they may be either a plain string or an object keyed by locale.
/// `content.type` is a tagged payload discriminator: `html` renders `content.value` with `HTMLRenderer`, while `link`
/// treats `content.value` as an external URL. A single announcement has exactly one `content` payload, so inline HTML
/// and external links cannot be configured at the same level.
///
/// `id` is a UUID value represented as a UUID string in JSON. Production feeds should generate UUID v7 values on the
/// server side, but the client accepts any UUID that can be parsed by the configured JSON adapter. `parent`, when
/// present, must refer to another announcement ID.
///
/// `targets` replaces the legacy single `type` field when an announcement should appear on multiple surfaces. The
/// supported targets are `board` for the homepage announcement area and `popup` for startup dialogs. `categories`
/// likewise replaces the legacy single `category` field when an announcement belongs to multiple user-controllable
/// groups.
///
/// `rules` uses [CompatibilityRule] to describe environment restrictions. Operating system restrictions use the
/// existing `os` object. HMCL build channels are exposed as boolean features named `hmcl_channel_stable`,
/// `hmcl_channel_dev`, and `hmcl_channel_nightly`.
@NotNullByDefault
@JsonSerializable
public final class Announcement {
    /// Unique announcement UUID.
    @SerializedName("id")
    private @Nullable UUID id;

    /// Localized announcement title.
    @SerializedName("title")
    private @Nullable LocalizedText title;

    /// Localized announcement content.
    @SerializedName("content")
    private @Nullable Content content;

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

    /// Optional parent announcement UUID.
    @SerializedName("parent")
    private @Nullable UUID parent;

    /// Single announcement category used by the category visibility settings.
    @SerializedName("category")
    private @Nullable String category;

    /// Multiple announcement categories used by the category visibility settings.
    @SerializedName("categories")
    private @Nullable List<String> categories;

    /// Optional ISO-8601 instant when the announcement starts being active.
    @SerializedName("startsAt")
    private @Nullable String startsAt;

    /// Optional ISO-8601 instant when the announcement stops being active.
    @SerializedName("expiresAt")
    private @Nullable String expiresAt;

    /// Optional compatibility rules that restrict where this announcement applies.
    @SerializedName("rules")
    private @Nullable List<CompatibilityRule> rules;

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
    public @Nullable UUID getId() {
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
    public @Nullable UUID getParent() {
        return parent;
    }

    /// @return The normalized categories that classify this announcement.
    public @Unmodifiable List<String> getCategories() {
        if (categories != null && !categories.isEmpty()) {
            return categories.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .toList();
        }

        if (StringUtils.isNotBlank(category)) {
            return List.of(category.toLowerCase(Locale.ROOT));
        }

        return List.of(AnnouncementManager.CATEGORY_GENERAL);
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
        String localized = title == null ? null : title.getText(I18n.getLocale().getCandidateLocales());
        return StringUtils.isBlank(localized) ? String.valueOf(id) : localized;
    }

    /// @return The localized HTML content, or `null` when the announcement uses a link.
    public @Nullable String getLocalizedContent() {
        return content != null && content.isHtml() ? content.getLocalizedValue() : null;
    }

    /// @return The localized external content link, or `null` when inline content is used.
    public @Nullable String getLocalizedLink() {
        return content != null && content.isLink() ? content.getLocalizedValue() : null;
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
        if (id == null || StringUtils.isBlank(getLocalizedTitle())) {
            return false;
        }

        return content != null && content.isValid() && (hasTarget("board") || hasTarget("popup"));
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

        return CompatibilityRule.appliesToCurrentEnvironment(rules, getCompatibilityFeatures());
    }

    /// @return The instant used for secondary sorting.
    public Instant getSortTime() {
        Instant start = parseInstant(startsAt);
        return start == null ? Instant.EPOCH : start;
    }

    /// @return Feature flags supplied to [CompatibilityRule] evaluation.
    private static @Unmodifiable Map<String, Boolean> getCompatibilityFeatures() {
        return Map.of(
                "hmcl_channel_stable", Metadata.isStable(),
                "hmcl_channel_dev", Metadata.isDev(),
                "hmcl_channel_nightly", Metadata.isNightly()
        );
    }

    /// Parses an optional ISO-8601 instant.
    ///
    /// @param value Nullable instant string.
    /// @return Parsed instant, or `null` when the value is blank or malformed.
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

    /// @return The set of currently known severity values.
    public static @Unmodifiable Set<String> knownSeverities() {
        return Set.of("info", "warning", "critical");
    }

    /// A mutually exclusive announcement content payload.
    @NotNullByDefault
    @JsonSerializable
    public static final class Content {
        /// Content kind, such as `html` or `link`.
        @SerializedName("type")
        private @Nullable String type;

        /// Localized content value.
        @SerializedName("value")
        private @Nullable LocalizedText value;

        /// @return Whether this payload stores inline HTML.
        boolean isHtml() {
            return "html".equals(getNormalizedType());
        }

        /// @return Whether this payload stores an external link.
        boolean isLink() {
            return "link".equals(getNormalizedType());
        }

        /// @return The localized payload value, or `null` when unavailable.
        private @Nullable String getLocalizedValue() {
            return value == null ? null : value.getText(I18n.getLocale().getCandidateLocales());
        }

        /// @return Whether this payload has a known type and a non-blank localized value.
        private boolean isValid() {
            return (isHtml() || isLink()) && StringUtils.isNotBlank(getLocalizedValue());
        }

        /// @return The normalized content type.
        private String getNormalizedType() {
            return StringUtils.isBlank(type) ? "" : type.toLowerCase(Locale.ROOT);
        }
    }
}
