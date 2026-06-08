/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative regression for the Idope removal (2026-06-08). The idope.pics
 * backend was failing TLS 1.3 handshakes from any client using a
 * TLS-1.2-only stack (the local LibreSSL 3.3.6, JVMs on older JDKs, etc).
 * We removed Idope entirely from commons, desktop, and android.
 *
 * <p>This test fails if any of the following come back, so a future
 * contributor who adds Idope back must do it deliberately and update
 * this test:
 *
 * <ul>
 *   <li>The IdopeSearchPattern source file is restored in commons</li>
 *   <li>The IdopeSearchPatternTest is restored in desktop</li>
 *   <li>Any desktop/android source file references idope.pics, idope.hair,
 *       IdopeSearchPattern, SearchEngineID.IDOPE_ID,
 *       SearchEnginesSettings.IDOPE_SEARCH_ENABLED,
 *       PREF_KEY_SEARCH_USE_IDOPE, or createIdopeTorrentSearch</li>
 *   <li>The Android settings XML or any localized strings.xml still has
 *       a use_idope entry</li>
 * </ul>
 *
 * <p>If Idope comes back, the right path is: (1) re-add a working pattern
 * implementation, (2) restore the test, (3) wire it into
 * SearchEngine.getEngines() / Android's ALL_ENGINES, and (4) update
 * AndroidStringResourceParityTest's locale count to match the new strings.
 */
public class IdopeRemovalTest {

    private static final List<String> SOURCE_FILES = List.of(
            "../common/src/main/java/com/frostwire/search/SearchPerformerFactory.java",
            "../common/src/main/java/com/frostwire/util/Ssl.java",
            "../desktop/src/main/java/com/limegroup/gnutella/gui/search/SearchEngine.java",
            "../desktop/src/main/java/com/limegroup/gnutella/settings/SearchEnginesSettings.java",
            "../desktop/src/main/java/com/frostwire/mcp/desktop/adapters/SettingsAdapter.java",
            "../desktop/src/main/java/com/frostwire/mcp/desktop/tools/settings/SettingsSetTool.java",
            "../android/src/main/java/com/frostwire/android/core/Constants.java",
            "../android/src/main/java/com/frostwire/android/gui/SearchEngine.java",
            "../android/res/xml/settings_search_engines.xml"
    );

    private static final List<String> BANNED_SYMBOLS = List.of(
            "IdopeSearchPattern",
            "IdopeSearchListener",
            "IdopeResult",
            "IDOPE_ID",
            "IDOPE_SEARCH_ENABLED",
            "PREF_KEY_SEARCH_USE_IDOPE",
            "createIdopeTorrentSearch",
            "idope.pics",
            "idope.hair"
    );

    private static final Pattern IDOPE_STRING_ENTRY =
            Pattern.compile("<string\\s+name=\"use_idope\"");

    @Test
    public void idopePatternSourceFileDoesNotExist() {
        assertFalse(Files.exists(Path.of("../common/src/main/java/com/frostwire/search/idope/IdopeSearchPattern.java")),
                "IdopeSearchPattern.java must not be re-introduced without an updated plan");
        assertFalse(Files.exists(Path.of("../common/src/main/java/com/frostwire/search/idope")),
                "com.frostwire.search.idope package must not be re-introduced");
    }

    @Test
    public void idopeTestSourceFileDoesNotExist() {
        assertFalse(Files.exists(Path.of("src/test/java/com/frostwire/tests/IdopeSearchPatternTest.java")),
                "IdopeSearchPatternTest must not be re-introduced");
    }

    @Test
    public void noSourceFileReferencesBannedIdopeSymbols() throws IOException {
        for (String file : SOURCE_FILES) {
            String source = readUtf8(file);
            for (String banned : BANNED_SYMBOLS) {
                assertFalse(source.contains(banned),
                        file + " still references removed Idope symbol: " + banned);
            }
        }
    }

    @Test
    public void noLocalizedAndroidStringHasUseIdopeEntry() throws IOException {
        Path valuesDir = Path.of("../android/res");
        if (!Files.isDirectory(valuesDir)) {
            return; // android module not present in this checkout
        }
        try (var stream = Files.walk(valuesDir, 2)) {
            List<Path> stringFiles = stream
                    .filter(p -> p.getFileName().toString().equals("strings.xml"))
                    .filter(p -> p.toString().contains("values"))
                    .toList();
            assertFalse(stringFiles.isEmpty(), "no strings.xml files found under android/res");
            for (Path p : stringFiles) {
                String content = readUtf8(p.toString());
                Matcher m = IDOPE_STRING_ENTRY.matcher(content);
                assertFalse(m.find(),
                        p + " still defines <string name=\"use_idope\">; " +
                                "remove the use_idope entry from every locale");
            }
        }
    }

    @Test
    public void androidSettingsSearchEnginesXmlHasNoUseIdopeEntry() throws IOException {
        String content = readUtf8("../android/res/xml/settings_search_engines.xml");
        assertFalse(content.contains("use_idope"),
                "settings_search_engines.xml still has a frostwire.prefs.search.use_idope entry");
    }

    private static String readUtf8(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
