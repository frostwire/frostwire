/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TellurideBuildTest {

    @Test
    void parseBannerReadsBuildNumber() {
        assertEquals(
                47,
                TellurideBuild.parseBanner("Telluride Cloud Video Downloader. Build 47"));
        assertNull(TellurideBuild.parseBanner(null));
        assertNull(TellurideBuild.parseBanner("no build here"));
    }

    @Test
    void parsePythonSourceReadsBuildAssignment() {
        assertEquals(47, TellurideBuild.parsePythonSource("BUILD = 47\n"));
        assertEquals(47, TellurideBuild.parsePythonSource("from yt_dlp.utils import YoutubeDLError\n\nBUILD = 47\n"));
        assertNull(TellurideBuild.parsePythonSource("BUILD_NUMBER = 47\n"));
    }
}
