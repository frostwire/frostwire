/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BaseHttpDownloadIsolationStructureTest {

    @Test
    void downloadsUseIsolatedClientsAndCancelThemOnRemove() throws Exception {
        String source = read("src/main/java/com/frostwire/transfers/BaseHttpDownload.java");
        String compact = source.replaceAll("\\s+", "");

        assertTrue(compact.contains("HttpClientFactory.newInstance(HttpClientFactory.HttpContext.DOWNLOAD)"));
        assertTrue(compact.contains("client.cancel();"));
        assertTrue(compact.contains("complete||client.isCanceled()"));
        assertTrue(!compact.contains("HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD)"));
    }

    private static String read(String relativePath) throws IOException {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path[] candidates = {
            cwd.resolve("..").resolve("common").resolve(relativePath),
            cwd.resolve("common").resolve(relativePath),
            cwd.resolve(relativePath)
        };
        for (Path file : candidates) {
            if (Files.isRegularFile(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("missing " + relativePath + " from " + cwd);
    }
}
