package com.sep490.backend.config;

/** Authorities used by the Vocabulary and Explore content-management APIs. */
public final class ContentPermissions {
    public static final String VOCABULARY_READ = "VOCABULARY_READ";
    public static final String VOCABULARY_WRITE = "VOCABULARY_WRITE";
    public static final String VOCABULARY_IMPORT = "VOCABULARY_IMPORT";
    public static final String VOCABULARY_REVIEW = "VOCABULARY_REVIEW";
    public static final String VOCABULARY_PUBLISH = "VOCABULARY_PUBLISH";

    public static final String EXPLORE_READ = "EXPLORE_READ";
    public static final String EXPLORE_WRITE = "EXPLORE_WRITE";
    public static final String EXPLORE_LINK_WRITE = "EXPLORE_LINK_WRITE";
    public static final String EXPLORE_REVIEW = "EXPLORE_REVIEW";
    public static final String EXPLORE_PUBLISH = "EXPLORE_PUBLISH";

    public static final String VOCABULARY_READ_ACCESS =
            "hasAnyRole('CONTENT_MANAGER','ADMIN') or hasAnyAuthority('VOCABULARY_READ','VOCABULARY_WRITE','VOCABULARY_REVIEW','VOCABULARY_PUBLISH')";
    public static final String VOCABULARY_WRITE_ACCESS =
            "hasRole('CONTENT_MANAGER') or hasAuthority('VOCABULARY_WRITE')";
    public static final String VOCABULARY_IMPORT_ACCESS =
            "hasRole('CONTENT_MANAGER') or hasAuthority('VOCABULARY_IMPORT')";
    public static final String VOCABULARY_REVIEW_ACCESS =
            "hasRole('ADMIN') or hasAnyAuthority('VOCABULARY_REVIEW','VOCABULARY_PUBLISH')";
    public static final String EXPLORE_READ_ACCESS =
            "hasAnyRole('CONTENT_MANAGER','ADMIN') or hasAnyAuthority('EXPLORE_READ','EXPLORE_WRITE','EXPLORE_LINK_WRITE','EXPLORE_REVIEW','EXPLORE_PUBLISH')";
    public static final String EXPLORE_WRITE_ACCESS =
            "hasRole('CONTENT_MANAGER') or hasAuthority('EXPLORE_WRITE')";
    public static final String EXPLORE_LINK_ACCESS =
            "hasRole('CONTENT_MANAGER') or hasAuthority('EXPLORE_LINK_WRITE')";
    public static final String EXPLORE_REVIEW_ACCESS =
            "hasRole('ADMIN') or hasAnyAuthority('EXPLORE_REVIEW','EXPLORE_PUBLISH')";

    private ContentPermissions() {
    }
}
