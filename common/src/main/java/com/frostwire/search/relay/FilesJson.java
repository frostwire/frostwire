/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

final class FilesJson {
    private FilesJson() {
    }

    static String minimal(int fileCount, long totalSize) {
        if (fileCount <= 0) {
            fileCount = 1;
        }
        long per = totalSize > 0 && fileCount > 0 ? totalSize / fileCount : 0;
        return "[" + singleEntry(fileCount == 1 ? null : "a.bin", per) + "]";
    }

    private static String singleEntry(String name, long size) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("{");
        sb.append("\"size\":").append(size);
        if (name != null) {
            sb.append(",\"path\":\"").append(name).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
