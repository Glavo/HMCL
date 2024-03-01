package org.jackhuang.hmcl.download;

/**
 * @author Glavo
 */
public interface VersionListProvider {

    String getVersionListURL();

    /**
     * the specific version list that this download provider provides. i.e. "fabric", "forge", "liteloader", "game", "optifine"
     *
     * @param id the id of specific version list that this download provider provides. i.e. "fabric", "forge", "liteloader", "game", "optifine"
     * @return the version list
     * @throws IllegalArgumentException if the version list does not exist
     */
    VersionList<?> getVersionListById(String id);
}
