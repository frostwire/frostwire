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

    @Test
    public void desktopAdvancedSettingsExposeNewJlibtorrentSessionControls() throws Exception {
        String routerPane = readSource("../desktop/src/main/java/com/limegroup/gnutella/gui/options/panes/RouterConfigurationPaneItem.java");
        assertTrue(routerPane.contains("ConnectionSettings.NATPMP_GATEWAY"), routerPane);
        assertTrue(routerPane.contains("ConnectionSettings.NATPMP_LEASE_DURATION"), routerPane);
        assertTrue(routerPane.contains("settings.natpmpGateway"), routerPane);
        assertTrue(routerPane.contains("settings.natpmpLeaseDuration"), routerPane);

        String torrentConnectionPane = readSource("../desktop/src/main/java/com/limegroup/gnutella/gui/options/panes/TorrentConnectionPaneItem.java");
        assertTrue(torrentConnectionPane.contains("ConnectionSettings.ALLOW_MULTIPLE_CONNECTIONS_PER_PID"), torrentConnectionPane);
        assertTrue(torrentConnectionPane.contains("settings.allowMultipleConnectionsPerPid"), torrentConnectionPane);
    }

    private static void assertUsesPieceSizeForReq(String path) throws Exception {
        String source = readSource(path);

        assertTrue(source.contains(".pieceSizeForReq(0)"), path);
        assertFalse(source.contains(".pieceSize(0)"), path);
    }

    private static String readSource(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.ISO_8859_1);
    }
}
