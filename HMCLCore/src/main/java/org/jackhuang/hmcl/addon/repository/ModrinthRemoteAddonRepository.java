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
package org.jackhuang.hmcl.addon.repository;

import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.AddonType;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.addon.modrinth.ModrinthAPI;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.PriorityComparator;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Adapts Modrinth's native API models to the launcher's repository interface.
@NotNullByDefault
public final class ModrinthRemoteAddonRepository implements RemoteAddonRepository {

    /// The shared legacy adapter.
    private static final ModrinthRemoteAddonRepository INSTANCE = new ModrinthRemoteAddonRepository();

    /// Returns the shared legacy adapter.
    public static ModrinthRemoteAddonRepository getInstance() {
        return INSTANCE;
    }

    /// Orders loader categories after other display categories.
    private static final Comparator<String> TAG_COMPARATOR = PriorityComparator.of(
            List.of("babric",
                    "bta-babric",
                    "bukkit",
                    "bungeecord",
                    "canvas",
                    "datapack",
                    "fabric",
                    "folia",
                    "forge",
                    "geyser",
                    "iris",
                    "java-agent",
                    "legacy-fabric",
                    "liteloader",
                    "minecraft",
                    "modloader",
                    "mrpack",
                    "neoforge",
                    "nilloader",
                    "optifine",
                    "ornith",
                    "paper",
                    "purpur",
                    "quilt",
                    "rift",
                    "spigot",
                    "sponge",
                    "vanilla",
                    "velocity",
                    "waterfall"),
            Comparator.naturalOrder(),
            false
    );

    /// Maps a native project type to a supported launcher content type, or null.
    @Nullable
    private static AddonType toAddonType(String projectType) {
        return switch (projectType) {
            case "modpack" -> AddonType.MODPACK;
            case "resourcepack" -> AddonType.RESOURCE_PACK;
            case "shader" -> AddonType.SHADER_PACK;
            case "mod" -> AddonType.MOD;
            default -> null;
        };
    }

    /// Returns the native project type, or null if unsupported.
    private static @Nullable String toProjectType(AddonType type) {
        return switch (type) {
            case MOD -> "mod";
            case MODPACK -> "modpack";
            case RESOURCE_PACK -> "resourcepack";
            case SHADER_PACK -> "shader";
            default -> null;
        };
    }

    /// The direct provider used by legacy methods without a provider parameter.
    private static final DownloadProvider DIRECT_PROVIDER = new DownloadProvider();


    /// Creates the shared adapter.
    private ModrinthRemoteAddonRepository() {
    }

    @Override
    public String getApiBaseUrl() {
        return ModrinthAPI.API_BASE_URL;
    }

    @Override
    public String getBaseUrl() {
        return ModrinthAPI.WEBSITE_BASE_URL;
    }

    /// Maps the legacy sort choices to native search indexes.
    private static String convertSortType(SortType sortType) {
        return switch (sortType) {
            case RELEVANCY -> "relevance";
            case POPULARITY -> "follows";
            case DATE_CREATED -> "newest";
            case LAST_UPDATED -> "updated";
            case TOTAL_DOWNLOADS -> "downloads";
        };
    }

    /// Returns the legacy display ordering, or an empty list if no categories were supplied.
    @Unmodifiable
    private static List<String> sortDisplayCategories(@Nullable List<String> displayCategories) {
        return displayCategories != null && !displayCategories.isEmpty()
                ? displayCategories.stream().sorted(TAG_COMPARATOR).toList()
                : List.of();
    }

    /// Searches using legacy filters while leaving native facet support in the API.
    @Override
    public SearchResult search(DownloadProvider downloadProvider, AddonType type, String gameVersion,
            @Nullable RemoteAddonRepository.Category category, int pageOffset, int pageSize,
            String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
        @Nullable String projectType = toProjectType(type);
        if (projectType == null) return SearchResult.empty();
        List<List<String>> facets = new ArrayList<>();
        facets.add(List.of("project_type:" + projectType));
        if (StringUtils.isNotBlank(gameVersion)) facets.add(List.of("versions:" + gameVersion));
        if (category != null && StringUtils.isNotBlank(category.id())) facets.add(List.of("categories:" + category.id()));
        var result = ModrinthAPI.search(downloadProvider, Map.of(
                "query", searchFilter,
                "facets", JsonUtils.UGLY_GSON.toJson(facets),
                "offset", Integer.toString(pageOffset * pageSize),
                "limit", Integer.toString(pageSize),
                "index", convertSortType(sort)));
        return new SearchResult(result.hits().stream().map(ProjectSearchResult::new).map(ProjectSearchResult::toAddon),
                (int) Math.ceil((double) result.totalHits() / pageSize));
    }

    @Override
    public Optional<RemoteAddon.Version> getRemoteVersionByLocalFile(Path file) throws IOException {
        return getRemoteVersionBySHA1(DigestUtils.digestToString("SHA-1", file));
    }

    /// Looks up a version by SHA-1, returning empty when no file matches or the version contains no files.
    ///
    /// @param sha1 the hexadecimal SHA-1 file digest
    /// @return the converted version, or empty when no usable version is found
    /// @throws IOException if the lookup fails for a reason other than a missing file
    public Optional<RemoteAddon.Version> getRemoteVersionBySHA1(String sha1) throws IOException {
        try {
            return toVersion(ModrinthAPI.getVersionFromHash(DIRECT_PROVIDER, sha1, ModrinthAPI.HashAlgorithm.SHA1));
        } catch (IOException e) {
            if (isNotFound(e)) return Optional.empty();
            throw e;
        }
    }

    @Override
    public RemoteAddon getAddonById(DownloadProvider downloadProvider, String id) throws IOException {
        return new Project(ModrinthAPI.getProject(downloadProvider, StringUtils.removePrefix(id, "local-"))).toAddon();
    }

    /// Resolves a dependency, returning the legacy missing marker only if every candidate reports absence.
    @Override
    public RemoteAddon resolveDependency(DownloadProvider downloadProvider, String id) throws IOException {
        try {
            return getAddonById(downloadProvider, id);
        } catch (IOException e) {
            if (isNotFound(e)) return RemoteAddon.BROKEN;
            throw e;
        }
    }

    /// Returns whether a failure, or all its suppressed candidate failures, indicates absence.
    private static boolean isNotFound(Throwable failure) {
        if (failure instanceof FileNotFoundException || failure instanceof NoSuchFileException
                || failure instanceof ResponseCodeException response && response.getResponseCode() == 404) return true;
        Throwable[] failures = failure.getSuppressed();
        return failures.length > 0 && Arrays.stream(failures).allMatch(ModrinthRemoteAddonRepository::isNotFound);
    }

    @Override
    public RemoteAddon.File getAddonFile(String projectId, String fileId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RemoteAddon.Version> getRemoteVersionsById(DownloadProvider downloadProvider, String id) throws IOException {
        return ModrinthAPI.getProjectVersions(downloadProvider, StringUtils.removePrefix(id, "local-"), null, null, null, false)
                .stream().map(ModrinthRemoteAddonRepository::toVersion).flatMap(Optional::stream);
    }

    @Override
    public @Nullable String getAddonChangelog(DownloadProvider downloadProvider, String addonId, String versionId) throws IOException {
        return ModrinthAPI.getVersion(downloadProvider, versionId).changelog();
    }

    @Override
    public String getVersionPageUrl(RemoteAddon.Version version) {
        return "%s/project/%s/version/%s".formatted(getBaseUrl(), version.projectId(), version.versionId());
    }

    @Override
    public Stream<RemoteAddonRepository.Category> getCategories(AddonType type) throws IOException {
        @Nullable String projectType = toProjectType(type);
        if (projectType == null) return Stream.empty();
        return ModrinthAPI.getCategories(DIRECT_PROVIDER).stream()
                .filter(category -> category.projectType().equals(projectType))
                .map(category -> new RemoteAddonRepository.Category(category, category.name(), List.of()));
    }

    /// Loads the dependencies used by any published version of a project.
    private static List<RemoteAddon> loadProjectDependencies(RemoteAddonRepository repo, DownloadProvider provider, String projectId) throws IOException {
        Set<RemoteAddon.Dependency> dependencies = repo.getRemoteVersionsById(provider, projectId)
                .flatMap(version -> version.dependencies().stream()).collect(Collectors.toSet());
        List<RemoteAddon> addons = new ArrayList<>();
        for (var dependency : dependencies) addons.add(dependency.load(provider));
        return addons;
    }

    /// Adapts a native project to the legacy project loading interface.
    ///
    /// @param data the native project metadata
    public record Project(ModrinthAPI.Project data) implements RemoteAddon.IAddon {
        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return loadProjectDependencies(repo, downloadProvider, data.id());
        }

        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return repo.getRemoteVersionsById(downloadProvider, data.id());
        }

        /// Converts the project to the legacy display model.
        public RemoteAddon toAddon() {
            return new RemoteAddon(Objects.requireNonNullElse(data.slug(), data.id()), "", data.title(), data.description(),
                    data.categories(), "%s/%s/%s".formatted(ModrinthAPI.WEBSITE_BASE_URL, data.projectType(), data.id()),
                    Objects.requireNonNullElse(data.iconUrl(), ""), this, toAddonType(data.projectType()));
        }
    }

    /// Adapts a native search hit to the legacy project loading interface.
    ///
    /// @param data the native search hit
    public record ProjectSearchResult(ModrinthAPI.SearchHit data) implements RemoteAddon.IAddon {
        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return loadProjectDependencies(repo, downloadProvider, data.projectId());
        }

        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return repo.getRemoteVersionsById(downloadProvider, data.projectId());
        }

        /// Converts the search hit to the legacy display model, preserving category display ordering.
        public RemoteAddon toAddon() {
            return new RemoteAddon(Objects.requireNonNullElse(data.slug(), data.projectId()), data.author(), data.title(), data.description(),
                    sortDisplayCategories(data.displayCategories()),
                    "%s/%s/%s".formatted(ModrinthAPI.WEBSITE_BASE_URL, data.projectType(), data.projectId()),
                    Objects.requireNonNullElse(data.iconUrl(), ""), this, toAddonType(data.projectType()));
        }
    }

    /// Converts a native version using the legacy first-file and project-dependency rules.
    private static Optional<RemoteAddon.Version> toVersion(ModrinthAPI.Version version) {
        if (version.files().isEmpty()) return Optional.empty();
        RemoteAddon.VersionType type = switch (version.versionType()) {
            case "beta" -> RemoteAddon.VersionType.Beta;
            case "alpha" -> RemoteAddon.VersionType.Alpha;
            default -> RemoteAddon.VersionType.Release;
        };
        var file = version.files().get(0);
        return Optional.of(new RemoteAddon.Version(RemoteAddon.Source.MODRINTH, version.id(), version.projectId(),
                version.name(), version.versionNumber(), version.datePublished(), type,
                new RemoteAddon.File(file.hashes(), file.url(), file.filename()),
                version.dependencies().stream().map(dependency -> {
                    if (dependency.projectId() == null) return RemoteAddon.Dependency.ofBroken();
                    RemoteAddon.DependencyType dependencyType = switch (dependency.dependencyType()) {
                        case "required" -> RemoteAddon.DependencyType.REQUIRED;
                        case "optional" -> RemoteAddon.DependencyType.OPTIONAL;
                        case "embedded" -> RemoteAddon.DependencyType.EMBEDDED;
                        case "incompatible" -> RemoteAddon.DependencyType.INCOMPATIBLE;
                        default -> throw new IllegalStateException("Unknown Modrinth dependency type: " + dependency.dependencyType());
                    };
                    return RemoteAddon.Dependency.ofGeneral(dependencyType, RemoteAddon.Source.MODRINTH, dependency.projectId());
                }).toList(), version.gameVersions(), version.loaders().stream().map(AddonLoader::of).toList()));
    }
}
