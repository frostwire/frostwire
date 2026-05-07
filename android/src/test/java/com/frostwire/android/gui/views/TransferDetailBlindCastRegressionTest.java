/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Structural regression test: AbstractTransferDetailFragment must never blind-cast
 * the result of TransferManager.getBittorrentDownload() to UIBittorrentDownload,
 * because the map can still hold a TorrentFetcherDownload during the fetch phase.
 */
public class TransferDetailBlindCastRegressionTest {

    private static final String FORBIDDEN_PATTERN = "(UIBittorrentDownload) TransferManager.instance().getBittorrentDownload";
    private static final String EXPECTED_GUARD = "bittorrentDownload instanceof UIBittorrentDownload";
    private static final String SOURCE_FILE = "src/main/java/com/frostwire/android/gui/views/AbstractTransferDetailFragment.java";

    @Test
    public void ensureTorrentHandle_doesNotBlindCast_getBittorrentDownloadResult() throws IOException {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        Path sourcePath = projectRoot.resolve(SOURCE_FILE);
        if (!Files.exists(sourcePath)) {
            String classFile = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
            Path buildDir = Paths.get(classFile).normalize();
            projectRoot = buildDir.getParent().getParent().getParent().getParent();
            sourcePath = projectRoot.resolve(SOURCE_FILE);
        }
        String source = new String(Files.readAllBytes(sourcePath));
        assertFalse("AbstractTransferDetailFragment.ensureTorrentHandle must NOT blind-cast "
                + "TransferManager.getBittorrentDownload() to UIBittorrentDownload — "
                + "the map may contain TorrentFetcherDownload during fetch phase",
                source.contains(FORBIDDEN_PATTERN));
    }

    @Test
    public void ensureTorrentHandle_usesInstanceofGuard_beforeCast() throws IOException {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        Path sourcePath = projectRoot.resolve(SOURCE_FILE);
        if (!Files.exists(sourcePath)) {
            String classFile = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
            Path buildDir = Paths.get(classFile).normalize();
            projectRoot = buildDir.getParent().getParent().getParent().getParent();
            sourcePath = projectRoot.resolve(SOURCE_FILE);
        }
        String source = new String(Files.readAllBytes(sourcePath));
        assertTrue("AbstractTransferDetailFragment.ensureTorrentHandle must guard casts with instanceof "
                + "when handling getBittorrentDownload() results",
                source.contains(EXPECTED_GUARD));
    }
}
