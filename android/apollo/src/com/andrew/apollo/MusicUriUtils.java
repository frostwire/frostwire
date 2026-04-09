/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import com.frostwire.util.UrlUtils;

/**
 * Pure-Java URI helpers for the Apollo music player.
 * No Android framework dependencies — safe to test on pure JVM.
 */
public final class MusicUriUtils {

    private MusicUriUtils() {}

    /**
     * Resolves a raw path string into a well-formed URI string:
     * - content://, http://, https://, file:// URIs are returned as-is
     * - bare file paths (including URL-encoded ones) get a "file://" prefix
     *   after decoding percent-encoded characters via {@link UrlUtils#decode}
     * - On any decoding error the original path is prefixed with "file://"
     */
    public static String resolveUriString(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("content://") || path.startsWith("http://")
                || path.startsWith("https://") || path.startsWith("file://")) {
            return path;
        }
        try {
            return "file://" + UrlUtils.decode(path);
        } catch (Throwable t) {
            return "file://" + path;
        }
    }
}
