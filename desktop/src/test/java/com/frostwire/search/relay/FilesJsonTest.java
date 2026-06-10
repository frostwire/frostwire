/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilesJsonTest {

    @Test
    void singleFileSkipsNameField() {
        String json = FilesJson.minimal(1, 1024);
        assertEquals("[{\"size\":1024}]", json);
    }

    @Test
    void multiFileIncludesNameField() {
        String json = FilesJson.minimal(2, 1000);
        assertEquals("[{\"size\":500,\"path\":\"a.bin\"}]", json);
    }

    @Test
    void zeroOrNegativeFileCountIsClampedToOne() {
        assertEquals("[{\"size\":0}]", FilesJson.minimal(0, 0));
        assertEquals("[{\"size\":0}]", FilesJson.minimal(-3, 0));
    }
}
