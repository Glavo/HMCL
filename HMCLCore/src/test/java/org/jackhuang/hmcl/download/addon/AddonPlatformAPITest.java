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
package org.jackhuang.hmcl.download.addon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jackhuang.hmcl.addon.AddonType;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.addon.curseforge.CurseForgeAPI;
import org.jackhuang.hmcl.download.addon.modrinth.ModrinthAPI;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// Exercises native API wire formats, candidate failures, and legacy adapters against a local server.
@NotNullByDefault
final class AddonPlatformAPITest {
    /// The server used instead of the public APIs.
    private final HttpServer server;

    /// The requests observed by the server, in arrival order.
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    /// Creates a server that serves representative native API responses.
    AddonPlatformAPITest() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /// Stops the server after each test, including failed assertions.
    @AfterEach
    void closeServer() {
        server.stop(0);
    }

    /// Verifies native CurseForge query options, pagination, nullable metadata, and large counts.
    @Test
    void curseForgeSearchPreservesNativeParametersAndMetadata() throws IOException {
        @Unmodifiable Map<String, String> parameters = Map.of("classId", "4546", "categoryIds", "[1,2]", "sortField", "4", "sortOrder", "asc", "searchFilter", "a+b & c", "index", "20", "pageSize", "10");
        var result = CurseForgeAPI.search(provider(""), 432, parameters);
        assertEquals("432", lastRequest().query().get("gameId"));
        parameters.forEach((key, value) -> assertEquals(value, lastRequest().query().get(key)));
        assertEquals(91, Objects.requireNonNull(result.pagination()).totalCount());
        assertEquals(20, result.pagination().index());
        var project = result.data().get(0);
        assertEquals(6000000000.0, project.downloadCount());
        assertNull(project.allowModDistribution());
        assertEquals(1001, Objects.requireNonNull(project.latestFilesIndexes()).get(0).fileId());
        assertThrows(UnsupportedOperationException.class, () -> result.data().clear());
        assertThrows(UnsupportedOperationException.class, () -> project.latestFiles().clear());
        assertNull(lastRequest().apiKey());
    }

    /// Verifies native file metadata and platform-specific text endpoints without URL synthesis.
    @Test
    void curseForgeFilesRetainNullUrlsAndRawRelations() throws IOException {
        var provider = provider("");
        var file = CurseForgeAPI.getModFile(provider, 42, 1001);
        assertNull(file.downloadUrl());
        assertEquals(5000000000L, file.fileLength());
        assertEquals(4294967295L, file.fileFingerprint());
        assertEquals(4, file.dependencies().get(0).relationType());
        assertEquals("<p>Description</p>", CurseForgeAPI.getModDescription(provider, 42));
        assertEquals("<p>Changes</p>", CurseForgeAPI.getModFileChangelog(provider, 42, 1001));
        assertNull(CurseForgeAPI.getModFileDownloadUrl(provider, 42, 1001));
        assertEquals("Test project", CurseForgeAPI.getMod(provider, 42).name());
        assertEquals("Test project", CurseForgeRemoteAddonRepository.getInstance().getAddonById(provider, "42").title());
    }

    /// Verifies class-only category lookup and the fingerprint POST response's nested map shape.
    @Test
    void curseForgeCategoriesAndFingerprintsPreservePlatformShapes() throws IOException {
        var provider = provider("");
        var categories = CurseForgeAPI.getCategories(provider, 432, 4546, true);
        assertEquals(Map.of("gameId", "432", "classId", "4546", "classesOnly", "true"), lastRequest().query());
        assertNull(categories.get(0).parentCategoryId());
        assertEquals(Boolean.TRUE, categories.get(0).isClass());
        var matches = CurseForgeAPI.getFingerprintMatches(provider, 432, List.of(4294967295L, 7L));
        assertEquals("POST", lastRequest().method());
        assertEquals(JsonParser.parseString("{\"fingerprints\":[4294967295,7]}"), JsonParser.parseString(lastRequest().body()));
        var partial = Objects.requireNonNull(matches.partialMatchFingerprints());
        assertEquals(List.of(7L), partial.get("42"));
        assertThrows(UnsupportedOperationException.class, () -> partial.get("42").add(9L));
        assertThrows(UnsupportedOperationException.class, partial::clear);
        assertEquals(List.of(9L), matches.unmatchedFingerprints());
    }

    /// Verifies that the legacy adapter follows pagination even when a page is smaller than requested.
    @Test
    void curseForgeLegacyVersionsFetchSubsequentPages() throws IOException {
        var versions = CurseForgeRemoteAddonRepository.getInstance().getRemoteVersionsById(provider(""), "42").toList();
        assertEquals(List.of("1001", "1002", "1003"), versions.stream().map(RemoteAddon.Version::versionId).toList());
        assertEquals(List.of("0", "2"), requests.stream().map(request -> request.query().get("index")).toList());
        assertEquals("https://edge.forgecdn.net/files/1/1/test.jar", versions.get(0).file().url());
        assertEquals(RemoteAddon.DependencyType.OPTIONAL, versions.get(0).dependencies().get(0).getType());
    }

    /// Verifies native facets and all version files, including primary flags and version-only dependencies.
    @Test
    void modrinthPreservesFacetsFilesAndDependencyReferences() throws IOException {
        var provider = provider("");
        String facets = "[[\"categories:fabric\",\"categories:quilt\"],[\"project_type:mod\"]]";
        var result = ModrinthAPI.search(provider, Map.of("query", "a+b & c", "facets", facets, "index", "relevance", "offset", "20", "limit", "10"));
        assertEquals(facets, lastRequest().query().get("facets"));
        assertEquals("a+b & c", lastRequest().query().get("query"));
        assertEquals(91, result.totalHits());
        assertEquals(20, result.offset());
        assertEquals(6000000000L, result.hits().get(0).downloads());
        var version = ModrinthAPI.getVersion(provider, "V1");
        assertEquals(2, version.files().size());
        assertFalse(version.files().get(0).primary());
        assertTrue(version.files().get(1).primary());
        assertEquals(5000000000L, version.files().get(0).size());
        assertEquals("D1", version.dependencies().get(0).versionId());
        assertNull(version.dependencies().get(0).projectId());
        assertEquals("dependency.jar", version.dependencies().get(0).fileName());
        assertThrows(UnsupportedOperationException.class, () -> version.files().clear());
        assertThrows(UnsupportedOperationException.class, () -> version.files().get(0).hashes().clear());
    }

    /// Verifies native version filters, explicit empty filters, hash algorithms, and slug escaping.
    @Test
    void modrinthUsesNativeFiltersAndEscapesPathSegments() throws IOException {
        var provider = provider("");
        ModrinthAPI.getProject(provider, "local-name + space");
        assertEquals("/v2/project/local-name%20%2B%20space", lastRequest().path());
        ModrinthAPI.getProjectVersions(provider, "P1", List.of("fabric", "quilt"), List.of(), false, true);
        assertEquals("[\"fabric\",\"quilt\"]", lastRequest().query().get("loaders"));
        assertEquals("[]", lastRequest().query().get("game_versions"));
        assertEquals("false", lastRequest().query().get("featured"));
        assertEquals("true", lastRequest().query().get("include_changelog"));
        ModrinthAPI.getProjectVersions(provider, "P1", null, null, null, false);
        assertEquals(Map.of("include_changelog", "false"), lastRequest().query());
        ModrinthAPI.getVersionFromHash(provider, "abc", ModrinthAPI.HashAlgorithm.SHA512);
        assertEquals(Map.of("algorithm", "sha512"), lastRequest().query());
        assertEquals("technology", ModrinthAPI.getCategories(provider).get(0).name());
    }

    /// Verifies legacy prefix stripping, category ordering, changelog access, and first-file selection.
    @Test
    void modrinthLegacyAdaptersKeepTheirDisplayBehavior() throws IOException {
        var provider = provider("");
        var repo = ModrinthRemoteAddonRepository.getInstance();
        assertEquals("Test project", repo.getAddonById(provider, "local-P1").title());
        assertEquals("/v2/project/P1", lastRequest().path());
        var search = repo.search(provider, AddonType.MOD, "1.21.1", null, 2, 10, "query", RemoteAddonRepository.SortType.RELEVANCY, RemoteAddonRepository.SortOrder.DESC);
        assertEquals(List.of("utility", "fabric"), search.results().findFirst().orElseThrow().categories());
        var version = repo.getRemoteVersionsById(provider, "local-P1").findFirst().orElseThrow();
        assertEquals("test.jar", version.file().filename());
        assertEquals(RemoteAddon.DependencyType.BROKEN, version.dependencies().get(0).getType());
        assertEquals("# Changes", repo.getAddonChangelog(provider, "P1", "V1"));
    }

    /// Verifies ordered fallback and distinguishes missing dependencies from mixed network failures.
    @Test
    void candidateFallbackDoesNotHideServiceFailures() throws IOException {
        assertEquals("P1", ModrinthAPI.getProject(provider("/failed", ""), "P1").id());
        assertEquals(42, CurseForgeAPI.getMod(provider("/failed", ""), 42).id());
        assertThrows(ResponseCodeException.class, () -> ModrinthAPI.getProject(provider("/missing"), "P1"));
        var failure = assertThrows(IOException.class, () -> CurseForgeAPI.getMod(provider("/missing", "/failed"), 42));
        assertEquals(2, failure.getSuppressed().length);
        var mr = ModrinthRemoteAddonRepository.getInstance();
        var cf = CurseForgeRemoteAddonRepository.getInstance();
        assertSame(RemoteAddon.BROKEN, mr.resolveDependency(provider("/missing", "/missing"), "P1"));
        assertSame(RemoteAddon.BROKEN, cf.resolveDependency(provider("/missing", "/missing"), "42"));
        assertThrows(IOException.class, () -> mr.resolveDependency(provider("/missing", "/failed"), "P1"));
        assertThrows(IOException.class, () -> cf.resolveDependency(provider("/missing", "/failed"), "42"));
    }

    /// Returns the most recently observed request after a synchronous API call.
    private Request lastRequest() {
        return requests.get(requests.size() - 1);
    }

    /// Creates a provider whose candidates point at this server with the supplied failure prefixes.
    private DownloadProvider provider(String... prefixes) {
        return new LocalProvider("http://127.0.0.1:" + server.getAddress().getPort(), List.of(prefixes));
    }

    /// Loads one native JSON fixture.
    private static String fixture(String name) throws IOException {
        try (var input = Objects.requireNonNull(AddonPlatformAPITest.class.getResourceAsStream(name + ".json"))) {
            return new String(input.readAllBytes(), UTF_8);
        }
    }

    /// Handles requests using fixed wire-format fixtures and deterministic error candidates.
    private void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        @Nullable String rawQuery = uri.getRawQuery();
        Map<String, String> query = rawQuery == null ? Map.of() : Arrays.stream(rawQuery.split("&"))
                .map(value -> value.split("=", 2)).collect(Collectors.toMap(value -> URLDecoder.decode(value[0], UTF_8), value -> URLDecoder.decode(value[1], UTF_8)));
        requests.add(new Request(uri.getRawPath(), query, exchange.getRequestMethod(),
                new String(exchange.getRequestBody().readAllBytes(), UTF_8), exchange.getRequestHeaders().getFirst("X-API-KEY")));
        String path = uri.getPath();
        int status = path.startsWith("/missing") ? 404 : path.startsWith("/failed") ? 503 : 200;
        String body = "{}";
        if (status == 200) {
            if (path.equals("/v1/mods/search")) {
                body = "{\"data\":[" + fixture("curseforge-project") + "],\"pagination\":{\"index\":20,\"pageSize\":10,\"resultCount\":1,\"totalCount\":91}}";
            } else if (path.equals("/v1/mods/42/files")) {
                JsonObject file = JsonParser.parseString(fixture("curseforge-file")).getAsJsonObject();
                if (query.get("index").equals("0")) {
                    String first = file.toString();
                    file.addProperty("id", 1002);
                    body = "{\"data\":[" + first + "," + file + "],\"pagination\":{\"index\":0,\"pageSize\":50,\"resultCount\":2,\"totalCount\":3}}";
                } else {
                    file.addProperty("id", 1003);
                    body = "{\"data\":[" + file + "],\"pagination\":{\"index\":2,\"pageSize\":50,\"resultCount\":1,\"totalCount\":3}}";
                }
            } else if (path.equals("/v1/mods/42/files/1001")) {
                body = "{\"data\":" + fixture("curseforge-file") + "}";
            } else if (path.endsWith("/description")) {
                body = "{\"data\":\"<p>Description</p>\"}";
            } else if (path.endsWith("/changelog")) {
                body = "{\"data\":\"<p>Changes</p>\"}";
            } else if (path.endsWith("/download-url")) {
                body = "{\"data\":null}";
            } else if (path.equals("/v1/mods/42")) {
                body = "{\"data\":" + fixture("curseforge-project") + "}";
            } else if (path.equals("/v1/categories")) {
                body = "{\"data\":[{\"id\":4546,\"gameId\":432,\"name\":\"Customization\",\"slug\":\"customization\",\"url\":\"https://www.curseforge.com/minecraft/customization\",\"isClass\":true}]}";
            } else if (path.equals("/v1/fingerprints/432")) {
                body = "{\"data\":{\"isCacheBuilt\":true,\"exactMatches\":[],\"exactFingerprints\":[4294967295],\"partialMatches\":[],\"partialMatchFingerprints\":{\"42\":[7]},\"installedFingerprints\":[],\"unmatchedFingerprints\":[9]}}";
            } else if (path.equals("/v2/search")) {
                body = fixture("modrinth-search");
            } else if (path.startsWith("/v2/project/")) {
                body = path.endsWith("/version") ? "[" + fixture("modrinth-version") + "]" : fixture("modrinth-project");
            } else if (path.startsWith("/v2/version/") || path.startsWith("/v2/version_file/")) {
                body = fixture("modrinth-version");
            } else if (path.equals("/v2/tag/category")) {
                body = "[{\"icon\":\"<svg/>\",\"name\":\"technology\",\"project_type\":\"mod\",\"header\":\"categories\"}]";
            } else {
                status = 404;
            }
        }
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    /// Captures one HTTP request observed by the local server.
    ///
    /// @param path the raw path
    /// @param query the decoded query parameters
    /// @param method the HTTP method
    /// @param body the UTF-8 request body
    /// @param apiKey the API key header, or null if absent
    @NotNullByDefault
    private record Request(String path, @Unmodifiable Map<String, String> query, String method, String body, @Nullable String apiKey) {
        /// Snapshots the query parameters.
        private Request {
            query = Map.copyOf(query);
        }
    }

    /// Rewrites API URLs to the local fixture server while preserving paths and queries.
    @NotNullByDefault
    private static final class LocalProvider extends DownloadProvider {
        /// The local server origin.
        private final String origin;

        /// Candidate prefixes in attempt order.
        private final @Unmodifiable List<String> prefixes;

        /// Creates a provider with the specified local candidate prefixes.
        private LocalProvider(String origin, List<String> prefixes) {
            this.origin = origin;
            this.prefixes = List.copyOf(prefixes);
        }

        @Override
        public DownloadCandidates getDownloadCandidates(String url) {
            URI uri = URI.create(url);
            String suffix = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            return DownloadCandidates.of(prefixes.stream().map(prefix -> origin + prefix + suffix).toArray(String[]::new));
        }

        @Override
        public DownloadCandidates getVersionListCandidates(String url) {
            return getDownloadCandidates(url);
        }
    }
}
