/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JlibtorrentFeatureAdoptionTest {

    @Test
    public void transferPiecesViewsUseV2AwarePieceSizeForRequests() throws Exception {
        assertUsesPieceSizeForReq("../desktop/src/main/java/com/frostwire/gui/components/transfers/TransferDetailPieces.java");
        assertUsesPieceSizeForReq("../android/src/main/java/com/frostwire/android/gui/fragments/TransferDetailPiecesFragment.java");
    }

    private static void assertUsesPieceSizeForReq(String path) throws Exception {
        String source = Files.readString(Path.of(path), StandardCharsets.ISO_8859_1);

        assertTrue(source.contains(".pieceSizeForReq(0)"), path);
        assertFalse(source.contains(".pieceSize(0)"), path);
    }
}
