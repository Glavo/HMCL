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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.MurmurHash2;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.NoCandidatesException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.zip.Checksum;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

/// Provides blocking access to CurseForge's read APIs and fingerprint matching.
///
/// Requests use the provider's download candidates in order. An I/O failure advances to
/// the next candidate; if all candidates fail, the single failure or an exception containing
/// all failures as suppressed exceptions is thrown. HTTP errors remain I/O failures;
/// missing projects are not replaced with launcher-specific sentinel values.
/// JSON decoding failures propagate without trying another candidate.
///
/// At most 16 requests made through this class execute concurrently. Waiting for a request
/// permit is uninterruptible. Response models preserve platform IDs and relation codes.
///
/// @see <a href="https://docs.curseforge.com/rest-api/">CurseForge REST API</a>
@NotNullByDefault
public final class CurseForgeAPI {
    /// The official API origin.
    public static final String API_BASE_URL = "https://api.curseforge.com";

    /// The website origin.
    public static final String WEBSITE_BASE_URL = "https://www.curseforge.com";

    /// The configured API key, read once from the system property or JAR manifest.
    public static final String API_KEY = System.getProperty("hmcl.curseforge.apikey", JarUtils.getAttribute("hmcl.curseforge.apikey", ""));

    /// Limits concurrent requests made by this API.
    private static final Semaphore SEMAPHORE = new Semaphore(16);

    /// The maximum number of attempts made for each candidate.
    private static final int DEFAULT_RETRY_COUNT = 3;

    /// Returns whether a nonempty API key is configured; this does not check its validity.
    public static boolean isAvailable() {
        return !API_KEY.isEmpty();
    }

    /// Searches projects and returns one page with the server's pagination metadata.
    ///
    /// @param provider the download candidate provider
    /// @param gameId the game ID, such as 432 for Minecraft
    /// @param parameters native search parameters, such as `classId`, `categoryIds`, `gameVersion`,
    ///                   `modLoaderType`, `searchFilter`, `sortField`, `sortOrder`, `index`, and `pageSize`;
    ///                   values must be unescaped strings, with array values encoded as JSON;
    ///                   `gameId` is supplied separately and overrides any map entry
    /// @return one page in server order, with an immutable result list
    /// @throws IOException if all request candidates fail
    public static Response<@Unmodifiable List<Mod>> search(DownloadProvider provider, int gameId, Map<String, String> parameters) throws IOException {
        var query = new LinkedHashMap<>(parameters);
        query.put("gameId", Integer.toString(gameId));
        return getList(provider, NetworkUtils.withQuery("/v1/mods/search", query), Mod.class);
    }

    /// Fetches a project by its numeric CurseForge ID.
    ///
    /// @param provider the download candidate provider
    /// @param modId the project ID
    /// @return the project metadata
    /// @throws IOException if all request candidates fail, including when the project is missing
    public static Mod getMod(DownloadProvider provider, int modId) throws IOException {
        return get(provider, "/v1/mods/" + modId, Mod.class);
    }

    /// Fetches the HTML description of a project.
    ///
    /// @param provider the download candidate provider
    /// @param modId the project ID
    /// @return the HTML description
    /// @throws IOException if all request candidates fail
    public static String getModDescription(DownloadProvider provider, int modId) throws IOException {
        return get(provider, "/v1/mods/" + modId + "/description", String.class);
    }

    /// Fetches a file without synthesizing a download URL when the server omits it.
    ///
    /// @param provider the download candidate provider
    /// @param modId the project ID
    /// @param fileId the file ID
    /// @return the file metadata, including raw dependency relation codes
    /// @throws IOException if all request candidates fail
    public static File getModFile(DownloadProvider provider, int modId, int fileId) throws IOException {
        return get(provider, "/v1/mods/" + modId + "/files/" + fileId, File.class);
    }

    /// Fetches one page of project files with native filtering and pagination parameters.
    ///
    /// @param provider the download candidate provider
    /// @param modId the project ID
    /// @param parameters unescaped native parameters such as `gameVersion`, `modLoaderType`,
    ///                   `gameVersionTypeId`, `index`, and `pageSize`; the map is not modified
    /// @return one page in server order, with an immutable result list
    /// @throws IOException if all request candidates fail
    public static Response<@Unmodifiable List<File>> getModFiles(DownloadProvider provider, int modId, Map<String, String> parameters) throws IOException {
        return getList(provider, NetworkUtils.withQuery("/v1/mods/" + modId + "/files", parameters), File.class);
    }

    /// Fetches the HTML changelog of a file.
    ///
    /// @param provider the download candidate provider
    /// @param modId the project ID
    /// @param fileId the file ID
    /// @return the HTML changelog
    /// @throws IOException if all request candidates fail
    public static String getModFileChangelog(DownloadProvider provider, int modId, int fileId) throws IOException {
        return get(provider, "/v1/mods/" + modId + "/files/" + fileId + "/changelog", String.class);
    }

    /// Fetches the download URL supplied by CurseForge.
    ///
    /// @param provider the download candidate provider
    /// @param modId the project ID
    /// @param fileId the file ID
    /// @return the download URL, or null if the response contains no URL
    /// @throws IOException if all request candidates fail
    public static @Nullable String getModFileDownloadUrl(DownloadProvider provider, int modId, int fileId) throws IOException {
        return get(provider, "/v1/mods/" + modId + "/files/" + fileId + "/download-url", String.class);
    }

    /// Fetches categories or project classes without rearranging them into a tree.
    ///
    /// @param provider the download candidate provider
    /// @param gameId the game ID
    /// @param classId the class filter, or null to omit it
    /// @param classesOnly whether to request only top-level classes
    /// @return an immutable list in server order
    /// @throws IOException if all request candidates fail
    public static @Unmodifiable List<Category> getCategories(DownloadProvider provider, int gameId, @Nullable Integer classId, boolean classesOnly) throws IOException {
        var query = new LinkedHashMap<String, String>();
        query.put("gameId", Integer.toString(gameId));
        if (classId != null) query.put("classId", classId.toString());
        query.put("classesOnly", Boolean.toString(classesOnly));
        return getList(provider, NetworkUtils.withQuery("/v1/categories", query), Category.class).data();
    }

    /// Matches fingerprints for a game, retaining exact, partial, and unmatched results.
    ///
    /// @param provider the download candidate provider
    /// @param gameId the game ID
    /// @param fingerprints unsigned 32-bit fingerprints represented as longs
    /// @return the server's matching results
    /// @throws IOException if all request candidates fail
    public static FingerprintMatches getFingerprintMatches(DownloadProvider provider, int gameId, List<Long> fingerprints) throws IOException {
        Response<FingerprintMatches> response = request(provider, "/v1/fingerprints/" + gameId,
                Map.of("fingerprints", List.copyOf(fingerprints)), Response.typeOf(FingerprintMatches.class));
        return response.data();
    }

    /// Fetches a response and returns its data field.
    private static <T extends @Nullable Object> T get(DownloadProvider provider, String path, Class<T> type) throws IOException {
        return request(provider, path, null, Response.typeOf(type)).data();
    }

    /// Fetches one response and snapshots its result list without discarding pagination.
    private static <T> Response<@Unmodifiable List<T>> getList(DownloadProvider provider, String path, Class<T> type) throws IOException {
        Response<List<T>> response = request(provider, path, null, Response.typeOf(listTypeOf(type)));
        return new Response<>(List.copyOf(response.data()), response.pagination());
    }

    /// Executes a GET request, or a JSON POST when a body is supplied, using candidate fallback.
    private static <T> T request(DownloadProvider provider, String path, @Nullable Object body, TypeToken<T> type) throws IOException {
        DownloadCandidates candidates = provider.getDownloadCandidates(API_BASE_URL + path);
        SEMAPHORE.acquireUninterruptibly();
        try {
            @Nullable IOException failure = null;
            for (DownloadCandidate candidate : candidates.getCandidates()) {
                try {
                    HttpRequest request = body == null ? HttpRequest.GET(candidate.displayUrl())
                            : HttpRequest.POST(candidate.displayUrl()).json(body);
                    if (candidate.displayUrl().startsWith(API_BASE_URL + "/") && !API_KEY.isEmpty()) {
                        request.header("X-API-KEY", API_KEY);
                    }
                    return request.retry(DEFAULT_RETRY_COUNT).getJson(type);
                } catch (IOException e) {
                    if (candidates.getCandidates().size() == 1) throw e;
                    if (failure == null) failure = new IOException("All CurseForge request candidates failed");
                    failure.addSuppressed(e);
                }
            }
            throw failure != null ? failure : new NoCandidatesException();
        } finally {
            SEMAPHORE.release();
        }
    }

    /// Contains the payload and optional pagination of a CurseForge response.
    ///
    /// @param data the payload; collection endpoints return immutable list snapshots
    /// @param pagination the pagination metadata, or null for responses without pagination
    @JsonSerializable
    @NotNullByDefault
    public record Response<T extends @Nullable Object>(T data, @Nullable Pagination pagination) {
        /// Returns the Gson type for a response containing the specified payload class.
        @SuppressWarnings("unchecked")
        public static <T extends @Nullable Object> TypeToken<Response<T>> typeOf(Class<T> type) {
            return (TypeToken<Response<T>>) TypeToken.getParameterized(Response.class, type);
        }

        /// Returns the Gson type for a response containing the specified payload type.
        @SuppressWarnings("unchecked")
        public static <T extends @Nullable Object> TypeToken<Response<T>> typeOf(TypeToken<T> type) {
            return (TypeToken<Response<T>>) TypeToken.getParameterized(Response.class, type.getType());
        }
    }

    /// Calculates the CurseForge fingerprint without retaining the filtered file in memory.
    ///
    /// The file is read twice, excluding bytes 0x09, 0x0A, 0x0D, and 0x20 from the hash.
    /// Callers must keep the file unchanged until this method returns. The channel opened
    /// by this method is closed on both success and failure.
    ///
    /// @param file the file to fingerprint
    /// @return the unsigned 32-bit MurmurHash2 value stored in a long
    /// @throws IOException if the file cannot be read or hashing fails
    public static long calculateFingerprint(Path file) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            long startPosition = channel.position();

            byte[] bufferArray = new byte[1024 * 1024];
            ByteBuffer buffer = ByteBuffer.wrap(bufferArray);

            long filteredLength = 0;
            while (channel.read(buffer) > 0) {
                int len = buffer.position();
                for (int i = 0; i < len; i++) {
                    byte b = bufferArray[i];
                    if (b != 0x9 && b != 0xa && b != 0xd && b != 0x20) {
                        filteredLength++;
                    }
                }
                buffer.clear();
            }

            channel.position(startPosition);

            Checksum hasher = MurmurHash2.hash32(filteredLength, 1);
            while (channel.read(buffer) > 0) {
                int len = buffer.position();

                int pos = 0;
                while (pos < len) {
                    byte b = bufferArray[pos];
                    if (b == 0x9 || b == 0xa || b == 0xd || b == 0x20) {
                        break;
                    }
                    pos++;
                }

                if (pos < len) {
                    int pos2 = pos + 1;
                    while (pos2 < len) {
                        byte b = bufferArray[pos2];
                        if (b != 0x9 && b != 0xa && b != 0xd && b != 0x20) {
                            bufferArray[pos++] = b;
                        }
                        pos2++;
                    }
                }

                hasher.update(bufferArray, 0, pos);
                buffer.clear();
            }
            return hasher.getValue();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException(e);
        }
    }

    /// Describes a single page of a CurseForge response.
    ///
    /// @param index the zero-based offset of the first result
    /// @param pageSize the requested page size
    /// @param resultCount the number of results in this response
    /// @param totalCount the total result count reported by the server
    @JsonSerializable
    @NotNullByDefault
    public record Pagination(
            int index,
            int pageSize,
            int resultCount,
            int totalCount) {
    }

    /// Describes a category or top-level project class without building a category tree.
    ///
    /// @param id the category ID
    /// @param gameId the owning game ID
    /// @param name the display name
    /// @param slug the website slug
    /// @param url the website URL
    /// @param iconUrl the icon URL, or null if absent
    /// @param dateModified the last modification time
    /// @param isClass whether this is a top-level class, or null if absent
    /// @param classId the containing class ID, or null if absent
    /// @param parentCategoryId the parent category ID, or null if absent
    /// @param displayIndex the display ordering index, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record Category(
            int id,
            int gameId,
            String name,
            String slug,
            String url,
            @Nullable String iconUrl,
            Instant dateModified,
            @Nullable Boolean isClass,
            @Nullable Integer classId,
            @Nullable Integer parentCategoryId,
            @Nullable Integer displayIndex) {
    }

    /// Describes a CurseForge project, retaining its platform-specific metadata.
    ///
    /// @param id the project ID
    /// @param gameId the game ID
    /// @param name the project name
    /// @param slug the website slug
    /// @param links the project links
    /// @param summary the short description
    /// @param status the raw project status code
    /// @param downloadCount the download count reported by the server
    /// @param isFeatured whether the project is featured
    /// @param primaryCategoryId the primary category ID
    /// @param categories the project categories, copied on construction
    /// @param classId the project class ID, or null if absent
    /// @param authors the authors, copied on construction
    /// @param logo the project logo, or null if absent
    /// @param screenshots the screenshots, copied on construction, or null if absent
    /// @param mainFileId the main file ID
    /// @param latestFiles the latest files, copied on construction
    /// @param latestFilesIndexes the latest file indexes, copied on construction, or null if absent
    /// @param dateCreated the project creation time
    /// @param dateModified the last modification time
    /// @param dateReleased the last release time
    /// @param allowModDistribution the distribution preference, or null if unspecified
    /// @param gamePopularityRank the popularity rank within the game
    /// @param isAvailable whether the project is available for search
    /// @param thumbsUpCount the number of thumbs-up votes
    @JsonSerializable
    @NotNullByDefault
    public record Mod(
            int id,
            int gameId,
            String name,
            String slug,
            Links links,
            String summary,
            int status,
            double downloadCount,
            boolean isFeatured,
            int primaryCategoryId,
            @Unmodifiable List<Category> categories,
            @Nullable Integer classId,
            @Unmodifiable List<Author> authors,
            @Nullable Asset logo,
            @Nullable @Unmodifiable List<Asset> screenshots,
            int mainFileId,
            @Unmodifiable List<File> latestFiles,
            @Nullable @Unmodifiable List<FileIndex> latestFilesIndexes,
            Instant dateCreated,
            Instant dateModified,
            Instant dateReleased,
            @Nullable Boolean allowModDistribution,
            int gamePopularityRank,
            boolean isAvailable,
            int thumbsUpCount) {
        /// Creates a value with immutable snapshots of its collections.
        public Mod {
            categories = List.copyOf(categories);
            authors = List.copyOf(authors);
            screenshots = screenshots == null ? null : List.copyOf(screenshots);
            latestFiles = List.copyOf(latestFiles);
            latestFilesIndexes = latestFilesIndexes == null ? null : List.copyOf(latestFilesIndexes);
        }
    }

    /// Contains the external links for a project.
    ///
    /// @param websiteUrl the project website, or null if absent
    /// @param wikiUrl the wiki URL, or null if absent
    /// @param issuesUrl the issue tracker URL, or null if absent
    /// @param sourceUrl the source repository URL, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record Links(
            @Nullable String websiteUrl,
            @Nullable String wikiUrl,
            @Nullable String issuesUrl,
            @Nullable String sourceUrl) {
    }

    /// Identifies a project author.
    ///
    /// @param id the author ID
    /// @param name the display name
    /// @param url the profile URL
    @JsonSerializable
    @NotNullByDefault
    public record Author(
            int id,
            String name,
            String url) {
    }

    /// Describes a project logo or screenshot.
    ///
    /// @param id the asset ID
    /// @param modId the project ID
    /// @param title the asset title
    /// @param description the asset description
    /// @param thumbnailUrl the thumbnail URL, or null if absent
    /// @param url the asset URL
    @JsonSerializable
    @NotNullByDefault
    public record Asset(
            int id,
            int modId,
            String title,
            String description,
            @Nullable String thumbnailUrl,
            String url) {
    }

    /// Describes a project file without synthesizing URLs or interpreting dependency relations.
    ///
    /// @param id the file ID
    /// @param gameId the game ID
    /// @param modId the project ID
    /// @param isAvailable whether the file is available
    /// @param displayName the display name
    /// @param fileName the file name
    /// @param releaseType the raw release type code
    /// @param fileStatus the raw file status code
    /// @param hashes the file hashes, copied on construction, or null if absent
    /// @param fileDate the upload time
    /// @param fileSizeOnDisk the size on disk in bytes, or null if absent
    /// @param fileLength the size in bytes
    /// @param downloadCount the download count
    /// @param downloadUrl the server-provided URL, or null if unavailable
    /// @param gameVersions the game version and loader labels, copied on construction
    /// @param dependencies the dependency relations, copied on construction
    /// @param alternateFileId the alternate file ID, or null if absent
    /// @param isServerPack whether this is a server pack, or null if absent
    /// @param serverPackFileId the server pack file ID, or null if absent
    /// @param parentProjectFileId the parent project file ID, or null if absent
    /// @param sortableGameVersions the sorting metadata, copied on construction, or null if absent
    /// @param exposeAsAlternative whether to expose the file as an alternative, or null if absent
    /// @param isEarlyAccessContent whether this is early-access content, or null if absent
    /// @param earlyAccessEndDate the early-access end time, or null if absent
    /// @param modules the file modules, copied on construction, or null if absent
    /// @param fileFingerprint the unsigned 32-bit fingerprint stored in a long
    @JsonSerializable
    @NotNullByDefault
    public record File(
            int id,
            int gameId,
            int modId,
            boolean isAvailable,
            String displayName,
            String fileName,
            int releaseType,
            int fileStatus,
            @Nullable @Unmodifiable List<FileHash> hashes,
            Instant fileDate,
            @Nullable Long fileSizeOnDisk,
            long fileLength,
            long downloadCount,
            @Nullable String downloadUrl,
            @Unmodifiable List<String> gameVersions,
            @Unmodifiable List<FileDependency> dependencies,
            @Nullable Integer alternateFileId,
            @Nullable Boolean isServerPack,
            @Nullable Integer serverPackFileId,
            @Nullable Integer parentProjectFileId,
            @Nullable @Unmodifiable List<SortableGameVersion> sortableGameVersions,
            @Nullable Boolean exposeAsAlternative,
            @Nullable Boolean isEarlyAccessContent,
            @Nullable Instant earlyAccessEndDate,
            @Nullable @Unmodifiable List<FileModule> modules,
            long fileFingerprint) {
        /// Creates a value with immutable snapshots of its collections.
        public File {
            sortableGameVersions = sortableGameVersions == null ? null : List.copyOf(sortableGameVersions);
            modules = modules == null ? null : List.copyOf(modules);
            hashes = hashes == null ? null : List.copyOf(hashes);
            gameVersions = List.copyOf(gameVersions);
            dependencies = List.copyOf(dependencies);
        }
    }

    /// Contains a checksum and its CurseForge algorithm code.
    ///
    /// @param value the checksum text, or null if absent
    /// @param algo the raw algorithm code; 1 denotes SHA-1 and 2 denotes MD5
    @JsonSerializable
    @NotNullByDefault
    public record FileHash(
            @Nullable String value,
            int algo) {
    }

    /// Describes a file relation without converting it to a shared dependency type.
    ///
    /// @param modId the related project ID
    /// @param relationType the raw CurseForge relation type code
    @JsonSerializable
    @NotNullByDefault
    public record FileDependency(
            int modId,
            int relationType) {
    }

    /// Indexes a file by game version and loader.
    ///
    /// @param gameVersion the game version
    /// @param fileId the file ID
    /// @param filename the file name
    /// @param releaseType the release type code
    /// @param gameVersionTypeId the game version type ID, or null if absent
    /// @param modLoader the loader code, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record FileIndex(
            String gameVersion,
            int fileId,
            String filename,
            int releaseType,
            @Nullable Integer gameVersionTypeId,
            @Nullable Integer modLoader) {
    }

    /// Contains all fingerprint matching results returned by CurseForge.
    ///
    /// @param isCacheBuilt whether the fingerprint cache is built
    /// @param exactMatches the exact matches, copied on construction, or null if absent
    /// @param exactFingerprints the exact fingerprints, copied on construction, or null if absent
    /// @param partialMatches the partial matches, copied on construction, or null if absent
    /// @param partialMatchFingerprints the partial fingerprints by server key, deeply copied on construction, or null if absent
    /// @param installedFingerprints the installed fingerprints, copied on construction, or null if absent
    /// @param unmatchedFingerprints the unmatched fingerprints, copied on construction, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record FingerprintMatches(
            boolean isCacheBuilt,
            @Nullable @Unmodifiable List<FingerprintMatch> exactMatches,
            @Nullable @Unmodifiable List<Long> exactFingerprints,
            @Nullable @Unmodifiable List<FingerprintMatch> partialMatches,
            @Nullable @Unmodifiable Map<String, @Unmodifiable List<Long>> partialMatchFingerprints,
            @Nullable @Unmodifiable List<Long> installedFingerprints,
            @Nullable @Unmodifiable List<Long> unmatchedFingerprints) {
        /// Creates a value with immutable snapshots of its collections.
        public FingerprintMatches {
            exactMatches = exactMatches == null ? null : List.copyOf(exactMatches);
            exactFingerprints = exactFingerprints == null ? null : List.copyOf(exactFingerprints);
            partialMatches = partialMatches == null ? null : List.copyOf(partialMatches);
            if (partialMatchFingerprints != null) {
                Map<String, @Unmodifiable List<Long>> copy = new HashMap<>();
                partialMatchFingerprints.forEach((key, value) -> copy.put(key, List.copyOf(value)));
                partialMatchFingerprints = Map.copyOf(copy);
            }
            installedFingerprints = installedFingerprints == null ? null : List.copyOf(installedFingerprints);
            unmatchedFingerprints = unmatchedFingerprints == null ? null : List.copyOf(unmatchedFingerprints);
        }
    }

    /// Describes a project file matched by fingerprint.
    ///
    /// @param id the project ID
    /// @param file the matched file
    /// @param latestFiles the latest files for the project, copied on construction
    @JsonSerializable
    @NotNullByDefault
    public record FingerprintMatch(
            int id,
            File file,
            @Unmodifiable List<File> latestFiles) {
        /// Creates a value with immutable snapshots of its collections.
        public FingerprintMatch {
            latestFiles = List.copyOf(latestFiles);
        }
    }

    /// Describes a module bundled in a file.
    ///
    /// @param name the module name
    /// @param fingerprint the module fingerprint
    @JsonSerializable
    @NotNullByDefault
    public record FileModule(
            String name,
            long fingerprint) {
    }

    /// Contains the server metadata used to sort game versions.
    ///
    /// @param gameVersionName the version display name
    /// @param gameVersionPadded the padded sorting key
    /// @param gameVersion the version identifier
    /// @param gameVersionReleaseDate the release date, or null if absent
    /// @param gameVersionTypeId the game-version type ID, or null if absent
    @JsonSerializable
    @NotNullByDefault
    public record SortableGameVersion(
            String gameVersionName,
            String gameVersionPadded,
            String gameVersion,
            @Nullable Instant gameVersionReleaseDate,
            @Nullable Integer gameVersionTypeId) {
    }

    /// Prevents instantiation.
    private CurseForgeAPI() {
    }
}
