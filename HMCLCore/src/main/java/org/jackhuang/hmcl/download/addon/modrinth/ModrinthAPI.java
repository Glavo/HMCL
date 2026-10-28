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
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.NoCandidatesException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

/// Provides blocking access to Modrinth's v2 project, version, search, and category APIs.
///
/// Requests try the provider's version-list candidates in order. If all candidates fail,
/// the single I/O failure or an exception containing all failures as suppressed exceptions
/// is thrown. HTTP 404 remains an I/O failure. JSON decoding failures propagate without
/// trying another candidate. At most 16 requests execute concurrently; waiting for a
/// request permit is uninterruptible.
///
/// Project identifiers are passed to Modrinth as IDs or slugs; launcher-specific prefixes
/// are not interpreted. Version results retain all files and dependency references.
///
/// @see <a href="https://docs.modrinth.com/api/">Modrinth API</a>
@NotNullByDefault
public final class ModrinthAPI {
    /// The official API origin.
    public static final String API_BASE_URL = "https://api.modrinth.com";

    /// The website origin.
    public static final String WEBSITE_BASE_URL = "https://modrinth.com";

    /// Limits concurrent requests made by this API.
    private static final Semaphore SEMAPHORE = new Semaphore(16);

    /// Searches projects using native Modrinth search parameters.
    ///
    /// @param provider the download candidate provider
    /// @param parameters unescaped query values, including `query`, `facets`, `index`, `offset`,
    ///                   and `limit`; `facets` uses Modrinth's JSON array-of-arrays syntax,
    ///                   preserving OR within each group and AND between groups; the map is not modified
    /// @return one page in server order, including the total hit count
    /// @throws IOException if all request candidates fail
    public static SearchResult search(DownloadProvider provider, Map<String, String> parameters) throws IOException {
        return get(provider, NetworkUtils.withQuery("/v2/search", parameters), SearchResult.class);
    }

    /// Fetches a project by its Modrinth ID or slug.
    ///
    /// @param provider the download candidate provider
    /// @param idOrSlug the project ID or slug
    /// @return the project metadata, including its Markdown body
    /// @throws IOException if all request candidates fail
    public static Project getProject(DownloadProvider provider, String idOrSlug) throws IOException {
        return get(provider, "/v2/project/" + encodeSegment(idOrSlug), Project.class);
    }

    /// Fetches a version by its Modrinth version ID.
    ///
    /// @param provider the download candidate provider
    /// @param versionId the version ID
    /// @return the complete version, including its changelog and all files
    /// @throws IOException if all request candidates fail
    public static Version getVersion(DownloadProvider provider, String versionId) throws IOException {
        return get(provider, "/v2/version/" + encodeSegment(versionId), Version.class);
    }

    /// Fetches project versions with Modrinth's loader, game-version, and featured filters.
    ///
    /// @param provider the download candidate provider
    /// @param idOrSlug the project ID or slug
    /// @param loaders the loader filter, or null to omit it; an empty list is sent as `[]`
    /// @param gameVersions the game-version filter, or null to omit it; an empty list is sent as `[]`
    /// @param featured the featured filter, or null to omit it
    /// @param includeChangelog whether to request changelogs with the version list
    /// @return an immutable list in server order, including versions without downloadable files
    /// @throws IOException if all request candidates fail
    public static @Unmodifiable List<Version> getProjectVersions(DownloadProvider provider, String idOrSlug,
            @Nullable List<String> loaders, @Nullable List<String> gameVersions,
            @Nullable Boolean featured, boolean includeChangelog) throws IOException {
        var query = new LinkedHashMap<String, String>();
        if (loaders != null) query.put("loaders", JsonUtils.UGLY_GSON.toJson(loaders));
        if (gameVersions != null) query.put("game_versions", JsonUtils.UGLY_GSON.toJson(gameVersions));
        if (featured != null) query.put("featured", featured.toString());
        query.put("include_changelog", Boolean.toString(includeChangelog));
        return List.copyOf(request(provider, NetworkUtils.withQuery("/v2/project/" + encodeSegment(idOrSlug) + "/version", query), listTypeOf(Version.class)));
    }

    /// Fetches a version containing a file with the supplied hash.
    ///
    /// @param provider the download candidate provider
    /// @param hash the hexadecimal file hash
    /// @param algorithm the hash algorithm
    /// @return the matching version with all its files
    /// @throws IOException if all request candidates fail, including when no version matches
    public static Version getVersionFromHash(DownloadProvider provider, String hash, HashAlgorithm algorithm) throws IOException {
        return get(provider, NetworkUtils.withQuery("/v2/version_file/" + encodeSegment(hash),
                Map.of("algorithm", algorithm.value())), Version.class);
    }

    /// Fetches all category tags without filtering them to a shared set of project types.
    ///
    /// @param provider the download candidate provider
    /// @return an immutable list of category tags in server order
    /// @throws IOException if all request candidates fail
    public static @Unmodifiable List<Category> getCategories(DownloadProvider provider) throws IOException {
        return List.copyOf(request(provider, "/v2/tag/category", listTypeOf(Category.class)));
    }

    /// Identifies an algorithm accepted by Modrinth's version-file lookup.
    @NotNullByDefault
    public enum HashAlgorithm {
        /// SHA-1.
        SHA1("sha1"),
        /// SHA-512.
        SHA512("sha512");

        /// The query parameter value.
        private final String value;

        /// Creates an algorithm with its API value.
        HashAlgorithm(String value) {
            this.value = value;
        }

        /// Returns the query parameter value.
        public String value() {
            return value;
        }
    }

    /// Encodes a single path segment without treating spaces as form separators.
    private static String encodeSegment(String value) {
        return NetworkUtils.encodeURL(value).replace("+", "%20");
    }

    /// Fetches a response of the supplied class.
    private static <T> T get(DownloadProvider provider, String path, Class<T> type) throws IOException {
        return request(provider, path, TypeToken.get(type));
    }

    /// Fetches a response, retaining all candidate I/O failures when none succeeds.
    private static <T> T request(DownloadProvider provider, String path, TypeToken<T> type) throws IOException {
        DownloadCandidates candidates = provider.getVersionListCandidates(API_BASE_URL + path);
        SEMAPHORE.acquireUninterruptibly();
        try {
            @Nullable IOException failure = null;
            for (DownloadCandidate candidate : candidates.getCandidates()) {
                try {
                    return HttpRequest.GET(candidate.displayUrl()).getJson(type);
                } catch (IOException e) {
                    if (candidates.getCandidates().size() == 1) throw e;
                    if (failure == null) failure = new IOException("All Modrinth request candidates failed");
                    failure.addSuppressed(e);
                }
            }
            throw failure != null ? failure : new NoCandidatesException();
        } finally {
            SEMAPHORE.release();
        }
    }

    /// Contains one page of Modrinth search results in server order.
    ///
    /// @param hits the search hits, copied on construction
    /// @param offset the zero-based result offset
    /// @param limit the result limit
    /// @param totalHits the total number of matches
    @JsonSerializable
    @NotNullByDefault
    public record SearchResult(
            @Unmodifiable List<SearchHit> hits,
            int offset,
            int limit,
            @SerializedName("total_hits")
            int totalHits) {
        /// Creates a value with immutable snapshots of its collections.
        public SearchResult {
            hits = List.copyOf(hits);
        }
    }

    /// Describes a Modrinth search hit without reducing its category or version information.
    ///
    /// @param projectId the project ID
    /// @param slug the website slug, or null if absent
    /// @param title the project title
    /// @param description the short description
    /// @param author the author name
    /// @param projectType the project type
    /// @param categories the searchable categories, copied on construction
    /// @param displayCategories the display categories, copied on construction, or null if absent
    /// @param versions the supported game versions, copied on construction
    /// @param downloads the total downloads
    /// @param follows the follower count
    /// @param iconUrl the icon URL, or null if absent
    /// @param dateCreated the creation time
    /// @param dateModified the last update time
    /// @param latestVersion the latest version ID, or null if absent
    /// @param clientSide the client support requirement, or null if absent
    /// @param serverSide the server support requirement, or null if absent
    /// @param license the license identifier, or null if absent
    /// @param gallery the gallery URLs, copied on construction, or null if absent
    /// @param featuredGallery the featured gallery URL, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record SearchHit(
            @SerializedName("project_id")
            String projectId,
            @Nullable String slug,
            String title,
            String description,
            String author,
            @SerializedName("project_type")
            String projectType,
            @Unmodifiable List<String> categories,
            @SerializedName("display_categories")
            @Nullable @Unmodifiable List<String> displayCategories,
            @Unmodifiable List<String> versions,
            long downloads,
            int follows,
            @SerializedName("icon_url")
            @Nullable String iconUrl,
            @SerializedName("date_created")
            Instant dateCreated,
            @SerializedName("date_modified")
            Instant dateModified,
            @SerializedName("latest_version")
            @Nullable String latestVersion,
            @SerializedName("client_side")
            @Nullable String clientSide,
            @SerializedName("server_side")
            @Nullable String serverSide,
            @Nullable String license,
            @Nullable @Unmodifiable List<String> gallery,
            @SerializedName("featured_gallery")
            @Nullable String featuredGallery) {
        /// Creates a value with immutable snapshots of its collections.
        public SearchHit {
            categories = List.copyOf(categories);
            displayCategories = displayCategories == null ? null : List.copyOf(displayCategories);
            versions = List.copyOf(versions);
            gallery = gallery == null ? null : List.copyOf(gallery);
        }
    }

    /// Describes a Modrinth v2 project independently of launcher project interfaces.
    ///
    /// @param id the base62 project ID
    /// @param slug the website slug, or null if absent
    /// @param title the project title
    /// @param description the short description
    /// @param body the Markdown description
    /// @param projectType the project type
    /// @param team the owning team ID
    /// @param status the status, or null if absent
    /// @param clientSide the client support requirement, or null if absent
    /// @param serverSide the server support requirement, or null if absent
    /// @param categories the featured categories, copied on construction
    /// @param additionalCategories the additional categories, copied on construction, or null if absent
    /// @param versions the published version IDs, copied on construction
    /// @param gameVersions the game versions, copied on construction, or null if absent
    /// @param loaders the loaders, copied on construction, or null if absent
    /// @param downloads the download count
    /// @param followers the follower count
    /// @param iconUrl the icon URL, or null if absent
    /// @param published the creation time
    /// @param updated the latest update time
    /// @param license the license, or null if absent
    /// @param issuesUrl the issue tracker URL, or null if absent
    /// @param sourceUrl the source repository URL, or null if absent
    /// @param wikiUrl the wiki URL, or null if absent
    /// @param discordUrl the Discord URL, or null if absent
    /// @param gallery the gallery images, copied on construction, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record Project(
            String id,
            @Nullable String slug,
            String title,
            String description,
            String body,
            @SerializedName("project_type")
            String projectType,
            String team,
            @Nullable String status,
            @SerializedName("client_side")
            @Nullable String clientSide,
            @SerializedName("server_side")
            @Nullable String serverSide,
            @Unmodifiable List<String> categories,
            @SerializedName("additional_categories")
            @Nullable @Unmodifiable List<String> additionalCategories,
            @Unmodifiable List<String> versions,
            @SerializedName("game_versions")
            @Nullable @Unmodifiable List<String> gameVersions,
            @Nullable @Unmodifiable List<String> loaders,
            long downloads,
            int followers,
            @SerializedName("icon_url")
            @Nullable String iconUrl,
            Instant published,
            Instant updated,
            @Nullable License license,
            @SerializedName("issues_url")
            @Nullable String issuesUrl,
            @SerializedName("source_url")
            @Nullable String sourceUrl,
            @SerializedName("wiki_url")
            @Nullable String wikiUrl,
            @SerializedName("discord_url")
            @Nullable String discordUrl,
            @Nullable @Unmodifiable List<GalleryImage> gallery) {
        /// Creates a value with immutable snapshots of its collections.
        public Project {
            categories = List.copyOf(categories);
            additionalCategories = additionalCategories == null ? null : List.copyOf(additionalCategories);
            versions = List.copyOf(versions);
            gameVersions = gameVersions == null ? null : List.copyOf(gameVersions);
            loaders = loaders == null ? null : List.copyOf(loaders);
            gallery = gallery == null ? null : List.copyOf(gallery);
        }
    }

    /// Describes the license attached to a project.
    ///
    /// @param id the license identifier
    /// @param name the license name
    /// @param url the license URL, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record License(
            String id,
            String name,
            @Nullable String url) {
    }

    /// Describes a project gallery image.
    ///
    /// @param url the image URL
    /// @param featured whether the image is featured
    /// @param title the title, or null if absent
    /// @param description the description, or null if absent
    /// @param created the upload time
    /// @param ordering the display ordering value
    @JsonSerializable
    @NotNullByDefault
    public record GalleryImage(
            String url,
            boolean featured,
            @Nullable String title,
            @Nullable String description,
            Instant created,
            int ordering) {
    }

    /// Describes a Modrinth version, retaining all files and dependency references.
    ///
    /// @param id the version ID
    /// @param projectId the owning project ID
    /// @param authorId the publishing author ID
    /// @param name the display name
    /// @param versionNumber the version number
    /// @param changelog the Markdown changelog, or null if absent or omitted
    /// @param changelogUrl the changelog URL, or null if absent
    /// @param datePublished the publication time
    /// @param downloads the download count
    /// @param versionType the release channel
    /// @param featured whether the version is featured
    /// @param status the version status, or null if absent
    /// @param files all files in server order, copied on construction
    /// @param dependencies all dependency references, copied on construction
    /// @param gameVersions the supported game versions, copied on construction
    /// @param loaders the supported loaders, copied on construction
    @JsonSerializable
    @NotNullByDefault
    public record Version(
            String id,
            @SerializedName("project_id")
            String projectId,
            @SerializedName("author_id")
            String authorId,
            String name,
            @SerializedName("version_number")
            String versionNumber,
            @Nullable String changelog,
            @SerializedName("changelog_url")
            @Nullable String changelogUrl,
            @SerializedName("date_published")
            Instant datePublished,
            long downloads,
            @SerializedName("version_type")
            String versionType,
            boolean featured,
            @Nullable String status,
            @Unmodifiable List<VersionFile> files,
            @Unmodifiable List<Dependency> dependencies,
            @SerializedName("game_versions")
            @Unmodifiable List<String> gameVersions,
            @Unmodifiable List<String> loaders) {
        /// Creates a value with immutable snapshots of its collections.
        public Version {
            files = List.copyOf(files);
            dependencies = List.copyOf(dependencies);
            gameVersions = List.copyOf(gameVersions);
            loaders = List.copyOf(loaders);
        }
    }

    /// Describes a Modrinth dependency on a project, version, or file name.
    ///
    /// @param versionId the target version ID, or null if unspecified
    /// @param projectId the target project ID, or null if unspecified
    /// @param fileName the target file name, or null if unspecified
    /// @param dependencyType the platform dependency type
    @JsonSerializable
    @NotNullByDefault
    public record Dependency(
            @SerializedName("version_id")
            @Nullable String versionId,
            @SerializedName("project_id")
            @Nullable String projectId,
            @SerializedName("file_name")
            @Nullable String fileName,
            @SerializedName("dependency_type")
            String dependencyType) {
    }

    /// Describes one file of a Modrinth version.
    ///
    /// @param hashes the checksums by algorithm, copied on construction
    /// @param url the download URL
    /// @param filename the file name
    /// @param primary whether this is the primary file
    /// @param size the size in bytes
    /// @param fileType the resource pack format designation, or null if unspecified
    @JsonSerializable
    @NotNullByDefault
    public record VersionFile(
            @Unmodifiable Map<String, String> hashes,
            String url,
            String filename,
            boolean primary,
            long size,
            @SerializedName("file_type")
            @Nullable String fileType) {
        /// Creates a value with immutable snapshots of its collections.
        public VersionFile {
            hashes = Map.copyOf(hashes);
        }
    }

    /// Describes a Modrinth category tag.
    ///
    /// @param icon the SVG icon
    /// @param name the category name
    /// @param projectType the associated project type
    /// @param header the display group, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record Category(
            String icon,
            String name,
            @SerializedName("project_type")
            String projectType,
            @Nullable String header) {
    }

    /// Prevents instantiation.
    private ModrinthAPI() {
    }
}
