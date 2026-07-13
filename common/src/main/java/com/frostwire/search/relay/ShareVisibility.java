/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for {@link ShareVisibilityPolicy}.
 */
public final class ShareVisibility {

    private ShareVisibility() {
    }

    /**
     * Filter index rows through {@code policy}. Null policy = include all.
     */
    public static List<LocalSharedTorrent> filter(
            List<LocalSharedTorrent> rows, ShareVisibilityPolicy policy) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        if (policy == null || policy == ShareVisibilityPolicy.INCLUDE_ALL) {
            return rows;
        }
        List<LocalSharedTorrent> out = new ArrayList<>(rows.size());
        for (LocalSharedTorrent t : rows) {
            if (t == null) {
                continue;
            }
            String hash = t.infoHashHex();
            if (hash != null && policy.isVisible(hash)) {
                out.add(t);
            }
        }
        return out;
    }
}
