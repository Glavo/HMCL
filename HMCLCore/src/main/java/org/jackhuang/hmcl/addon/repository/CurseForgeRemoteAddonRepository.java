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
import org.jackhuang.hmcl.download.addon.curseforge.CurseForgeAPI;
import org.jackhuang.hmcl.download.addon.curseforge.CurseForgeProjectClass;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Adapts CurseForge's native API models to the launcher's repository interface.
@NotNullByDefault
public final class CurseForgeRemoteAddonRepository implements RemoteAddonRepository {
    /// The direct provider used by legacy methods without a provider parameter.
    private static final DownloadProvider DIRECT_PROVIDER = new DownloadProvider();

    /// The configured key retained for callers of the legacy repository API.
    public static final String API_KEY = CurseForgeAPI.API_KEY;

    /// Returns whether a CurseForge API key is configured.
    public static boolean isAvailable() {
        return CurseForgeAPI.isAvailable();
    }

    /// Creates an adapter for CurseForge projects.
    public CurseForgeRemoteAddonRepository() {
    }

    @Override
    public String getApiBaseUrl() {
        return CurseForgeAPI.API_BASE_URL;
    }

    @Override
    public String getBaseUrl() {
        return CurseForgeAPI.WEBSITE_BASE_URL;
    }

    /// Maps the legacy sort choices to CurseForge's search field codes.
    private static int toModsSearchSortField(SortType sort) {
        return switch (sort) {
            case RELEVANCY -> 1;
            case POPULARITY -> 2;
            case DATE_CREATED -> 11;
            case LAST_UPDATED -> 3;
            case TOTAL_DOWNLOADS -> 6;
        };
    }

    /// Searches the requested project class and adapts one page of results.
    @Override
    public SearchResult search(DownloadProvider downloadProvider, AddonType type, String gameVersion,
            @Nullable RemoteAddonRepository.Category category, int pageOffset, int pageSize,
            String searchFilter, SortType sortType, SortOrder sortOrder) throws IOException {
        int classId = toSectionId(type);
        if (classId < 0) return SearchResult.empty();
        var query = new LinkedHashMap<String, String>();
        query.put("classId", Integer.toString(classId));
        if (category != null && category.self() instanceof Category value && value.getId() != 0) {
            query.put("categoryId", Integer.toString(value.getId()));
        }
        query.put("gameVersion", gameVersion);
        query.put("searchFilter", searchFilter);
        query.put("sortField", Integer.toString(toModsSearchSortField(sortType)));
        query.put("sortOrder", sortOrder == SortOrder.ASC ? "asc" : "desc");
        query.put("index", Integer.toString(pageOffset * pageSize));
        query.put("pageSize", Integer.toString(pageSize));
        var response = CurseForgeAPI.search(downloadProvider, 432, query);
        @Nullable var pagination = response.pagination();
        int total = pagination != null ? pagination.totalCount() : response.data().size();
        return new SearchResult(response.data().stream().map(CurseAddon::new).map(CurseAddon::toAddon),
                (int) Math.ceil((double) Math.min(total, 10000) / pageSize));
    }

    /// Calculates a file fingerprint using the native API helper.
    static long calculateFingerprint(Path file) throws IOException {
        return CurseForgeAPI.calculateFingerprint(file);
    }

    /// Finds the first exact match for a local file, retaining the empty-file workaround.
    @Override
    public Optional<RemoteAddon.Version> getRemoteVersionByLocalFile(Path file) throws IOException {
        long hash = calculateFingerprint(file);
        if (hash == 811513880) return Optional.empty();
        var result = CurseForgeAPI.getFingerprintMatches(DIRECT_PROVIDER, 432, List.of(hash));
        @Nullable var matches = result.exactMatches();
        return matches == null || matches.isEmpty() ? Optional.empty() : Optional.of(toVersion(matches.get(0).file()));
    }

    @Override
    public RemoteAddon getAddonById(DownloadProvider downloadProvider, String id) throws IOException {
        return new CurseAddon(CurseForgeAPI.getMod(downloadProvider, Integer.parseInt(id))).toAddon();
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

    /// Returns whether a failure, or all of its suppressed candidate failures, indicates absence.
    private static boolean isNotFound(Throwable failure) {
        if (failure instanceof FileNotFoundException
                || failure instanceof ResponseCodeException response && response.getResponseCode() == 404) return true;
        Throwable[] failures = failure.getSuppressed();
        return failures.length > 0 && Arrays.stream(failures).allMatch(CurseForgeRemoteAddonRepository::isNotFound);
    }

    @Override
    public RemoteAddon.File getAddonFile(String projectId, String fileId) throws IOException {
        return toVersion(CurseForgeAPI.getModFile(DIRECT_PROVIDER, Integer.parseInt(projectId), Integer.parseInt(fileId))).file();
    }

    /// Fetches file pages up to the API's 10,000-result limit before returning the converted stream.
    @Override
    public Stream<RemoteAddon.Version> getRemoteVersionsById(DownloadProvider downloadProvider, String id) throws IOException {
        int modId = Integer.parseInt(id);
        List<CurseForgeAPI.File> files = new ArrayList<>();
        int index = 0;
        while (index < 10000) {
            var response = CurseForgeAPI.getModFiles(downloadProvider, modId,
                    Map.of("index", Integer.toString(index), "pageSize", Integer.toString(Math.min(50, 10000 - index))));
            if (response.data().isEmpty()) break;
            files.addAll(response.data());
            index += response.data().size();
            @Nullable var pagination = response.pagination();
            if (pagination != null ? index >= pagination.totalCount() : response.data().size() < 50) break;
        }
        return files.stream().map(CurseForgeRemoteAddonRepository::toVersion);
    }

    @Override
    public String getAddonChangelog(DownloadProvider downloadProvider, String addonId, String versionId) throws IOException {
        return CurseForgeAPI.getModFileChangelog(downloadProvider, Integer.parseInt(addonId), Integer.parseInt(versionId));
    }

    @Override
    public String getVersionPageUrl(RemoteAddon.Version version) throws IOException {
        var project = CurseForgeAPI.getMod(DIRECT_PROVIDER, Integer.parseInt(version.projectId()));
        for (var projectClass : CurseForgeProjectClass.values()) {
            if (Objects.equals(project.classId(), projectClass.id())) {
                return "%s/minecraft/%s/%s/files/%s".formatted(getBaseUrl(), projectClass.slug(), project.slug(), version.versionId());
            }
        }
        throw new IllegalArgumentException("Unsupported CurseForge class id [%s]".formatted(project.classId()));
    }

    @Override
    public Stream<RemoteAddonRepository.Category> getCategories(AddonType type) throws IOException {
        int classId = toSectionId(type);
        if (classId < 0) return Stream.empty();
        @Unmodifiable List<Category> categories = CurseForgeAPI.getCategories(DIRECT_PROVIDER, 432, null, false).stream()
                .map(Category::fromAPI).toList();
        return reorganizeCategories(categories, classId).stream().map(Category::toCategory);
    }

    /// Arranges freshly adapted categories into the legacy tree for the requested root.
    private static List<Category> reorganizeCategories(List<Category> categories, int rootId) {
        List<Category> result = new ArrayList<>();
        Map<Integer, Category> categoryMap = new HashMap<>();
        for (Category category : categories) categoryMap.put(category.getId(), category);
        for (Category category : categories) {
            if (category.getParentCategoryId() == rootId) {
                result.add(category);
            } else {
                @Nullable Category parent = categoryMap.get(category.getParentCategoryId());
                if (parent != null) parent.getSubcategories().add(category);
            }
        }
        return result;
    }

    /// The CurseForge class ID for bukkit plugin.
    public static final int SECTION_BUKKIT_PLUGIN = 5;
    /// The CurseForge class ID for mod.
    public static final int SECTION_MOD = 6;
    /// The CurseForge class ID for resource pack.
    public static final int SECTION_RESOURCE_PACK = 12;
    /// The CurseForge class ID for datapack.
    public static final int SECTION_DATAPACK = 6945;
    /// The CurseForge class ID for world.
    public static final int SECTION_WORLD = 17;
    /// The CurseForge class ID for modpack.
    public static final int SECTION_MODPACK = 4471;
    /// The CurseForge class ID for shader.
    public static final int SECTION_SHADER = 6552;
    /// The CurseForge class ID for customization.
    public static final int SECTION_CUSTOMIZATION = 4546;
    /// The CurseForge class ID for addons.
    public static final int SECTION_ADDONS = 4559; // For Pocket Edition
    /// The CurseForge class ID for unknown1.
    public static final int SECTION_UNKNOWN1 = 4944;
    /// The CurseForge class ID for unknown2.
    public static final int SECTION_UNKNOWN2 = 4979;
    /// The CurseForge class ID for unknown3.
    public static final int SECTION_UNKNOWN3 = 4984;

    /// The shared legacy adapter.
    private static final CurseForgeRemoteAddonRepository INSTANCE = new CurseForgeRemoteAddonRepository();

    /// Returns the shared legacy adapter.
    public static CurseForgeRemoteAddonRepository getInstance() {
        return INSTANCE;
    }

    /// Maps a class ID to a supported launcher content type, or null.
    @Nullable
    private static AddonType toAddonType(int classId) {
        return switch (classId) {
            case SECTION_MODPACK -> AddonType.MODPACK;
            case SECTION_RESOURCE_PACK -> AddonType.RESOURCE_PACK;
            case SECTION_WORLD -> AddonType.WORLD;
            case SECTION_CUSTOMIZATION -> AddonType.CUSTOMIZATION;
            case SECTION_SHADER -> AddonType.SHADER_PACK;
            case SECTION_MOD -> AddonType.MOD;
            default -> null;
        };
    }

    /// Returns the class ID for a launcher content type, or -1 if unsupported.
    private static int toSectionId(AddonType type) {
        return switch (type) {
            case MOD -> SECTION_MOD;
            case MODPACK -> SECTION_MODPACK;
            case RESOURCE_PACK -> SECTION_RESOURCE_PACK;
            case SHADER_PACK -> SECTION_SHADER;
            case WORLD -> SECTION_WORLD;
            case CUSTOMIZATION -> SECTION_CUSTOMIZATION;
            default -> -1;
        };
    }

    /// Adapts a native project to the legacy project loading interface.
    ///
    /// @param data the native project metadata
    public record CurseAddon(CurseForgeAPI.Mod data) implements RemoteAddon.IAddon {
        /// Returns the project class ID, or -1 if the server did not provide it.
        public int classId() {
            return data.classId() == null ? -1 : data.classId();
        }

        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            Set<Integer> dependencies = data.latestFiles().stream()
                    .flatMap(file -> file.dependencies().stream())
                    .filter(dependency -> dependency.relationType() == 3)
                    .map(CurseForgeAPI.FileDependency::modId).collect(Collectors.toSet());
            List<RemoteAddon> result = new ArrayList<>();
            for (int id : dependencies) result.add(repo.getAddonById(downloadProvider, Integer.toString(id)));
            return result;
        }

        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return repo.getRemoteVersionsById(downloadProvider, Integer.toString(data.id()));
        }

        /// Converts the native project to the legacy display model.
        public RemoteAddon toAddon() {
            String iconUrl = "";
            @Nullable var logo = data.logo();
            if (logo != null) {
                @Nullable String thumbnail = logo.thumbnailUrl();
                iconUrl = StringUtils.isNotBlank(thumbnail) ? thumbnail : logo.url();
            }
            return new RemoteAddon(data.slug(), "", data.name(), data.summary(),
                    data.categories().stream().map(category -> Integer.toString(category.id())).toList(),
                    Objects.requireNonNullElse(data.links().websiteUrl(), ""), iconUrl, this, toAddonType(classId()));
        }
    }

    /// Converts a native file using the legacy relation mapping and download-URL fallback.
    private static RemoteAddon.Version toVersion(CurseForgeAPI.File file) {
        RemoteAddon.VersionType versionType = switch (file.releaseType()) {
            case 2 -> RemoteAddon.VersionType.Beta;
            case 3 -> RemoteAddon.VersionType.Alpha;
            default -> RemoteAddon.VersionType.Release;
        };
        Map<String, String> hashes = new HashMap<>();
        if (file.hashes() != null) {
            for (var hash : file.hashes()) {
                if (hash.value() != null) {
                    if (hash.algo() == 1) hashes.put("sha1", hash.value());
                    if (hash.algo() == 2) hashes.put("md5", hash.value());
                }
            }
        }
        @Nullable String downloadUrl = file.downloadUrl();
        if (downloadUrl == null) {
            downloadUrl = "https://edge.forgecdn.net/files/%d/%d/%s".formatted(file.id() / 1000, file.id() % 1000, file.fileName());
        }
        return new RemoteAddon.Version(RemoteAddon.Source.CURSEFORGE,
                Integer.toString(file.id()), Integer.toString(file.modId()), file.displayName(), file.fileName(), file.fileDate(), versionType,
                new RemoteAddon.File(Map.copyOf(hashes), downloadUrl, file.fileName()),
                file.dependencies().stream().map(dependency -> {
                    RemoteAddon.DependencyType type = switch (dependency.relationType()) {
                        case 1, 6 -> RemoteAddon.DependencyType.EMBEDDED;
                        case 2, 4 -> RemoteAddon.DependencyType.OPTIONAL;
                        case 3 -> RemoteAddon.DependencyType.REQUIRED;
                        case 5 -> RemoteAddon.DependencyType.INCOMPATIBLE;
                        default -> throw new IllegalStateException("Unknown CurseForge relation type: " + dependency.relationType());
                    };
                    return RemoteAddon.Dependency.ofGeneral(type, RemoteAddon.Source.CURSEFORGE, Integer.toString(dependency.modId()));
                }).distinct().toList(),
                file.gameVersions().stream().filter(GameVersionNumber::isKnown).toList(),
                file.gameVersions().stream().filter(AddonLoader::mightBeLoader).map(AddonLoader::of).toList());
    }

    /// Holds the mutable category tree required by the legacy repository interface.
    public static final class Category {
        /// The immutable native category metadata.
        private final CurseForgeAPI.Category data;

        /// The mutable children assembled for this category tree.
        private final List<Category> subcategories = new ArrayList<>();

        /// Creates a tree node for a native category.
        private Category(CurseForgeAPI.Category data) {
            this.data = data;
        }

        /// Creates an independent tree node with no children.
        private static Category fromAPI(CurseForgeAPI.Category data) {
            return new Category(data);
        }

        /// Returns the category ID.
        public int getId() {
            return data.id();
        }

        /// Returns the category display name.
        public String getName() {
            return data.name();
        }

        /// Returns the parent ID, or zero if absent.
        public int getParentCategoryId() {
            return data.parentCategoryId() == null ? 0 : data.parentCategoryId();
        }

        /// Returns the mutable list used to assemble this node's children.
        public List<Category> getSubcategories() {
            return subcategories;
        }

        /// Converts this node and its descendants to the legacy category model.
        public RemoteAddonRepository.Category toCategory() {
            return new RemoteAddonRepository.Category(this, Integer.toString(getId()),
                    subcategories.stream().map(Category::toCategory).toList());
        }
    }
}
