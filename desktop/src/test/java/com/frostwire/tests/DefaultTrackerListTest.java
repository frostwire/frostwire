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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for issue #1004: keeps the default tracker list in
 * TorrentUtil, CreateTorrentDialog, and UrlUtils aligned and free of
 * trackers that have been observed dead. The test reads the three
 * source files, extracts every udp:// announce URL, and asserts that:
 *
 *   1. Each file contains the curated alive-tracker set.
 *   2. No file references a known-dead tracker host.
 *   3. The three files agree on exactly the same set of trackers.
 *
 * The curated list is the union of trackers that responded to a
 * BEP 15 connect_request during a one-time probe on 2026-06-08.
 * If a tracker is observed dead, add it to DEAD_TRACKERS and remove
 * it from EXPECTED_TRACKERS.
 */
public class DefaultTrackerListTest {

    private static final List<String> FILES = List.of(
            "../desktop/src/main/java/com/frostwire/gui/bittorrent/TorrentUtil.java",
            "../desktop/src/main/java/com/frostwire/gui/bittorrent/CreateTorrentDialog.java",
            "../common/src/main/java/com/frostwire/util/UrlUtils.java"
    );

    private static final List<String> EXPECTED_TRACKERS = List.of(
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.publictracker.xyz:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://wepzone.net:6969/announce",
            "udp://uabits.today:6990/announce",
            "udp://tracker.wildkat.net:6969/announce",
            "udp://tracker.tryhackx.org:6969/announce",
            "udp://tracker.theoks.net:6969/announce",
            "udp://tracker.t-1.org:6969/announce",
            "udp://tracker.qu.ax:6969/announce",
            "udp://tracker.opentorrent.top:6969/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://tracker.corpscorp.online:80/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://tracker.auctor.tv:6969/announce"
    );

    private static final List<String> DEAD_TRACKERS = List.of(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.moeking.me:6969/announce",
            "udp://explodie.org:6969/announce",
            "udp://tracker.coppersurfer.tk:6969/announce",
            "udp://zer0day.ch:1337/announce",
            "udp://tracker.flatuslifir.is:6969/announce",
            "udp://tracker.bluefrog.pw:2710/announce"
    );

    private static final Pattern UDP_URL = Pattern.compile("udp://[A-Za-z0-9._\\-:]+/announce");

    @Test
    public void allThreeFilesExposeTheCuratedTrackerList() throws Exception {
        Set<String> expected = new LinkedHashSet<>(EXPECTED_TRACKERS);
        for (String file : FILES) {
            String source = readSource(file);
            for (String tracker : expected) {
                assertTrue(source.contains(tracker),
                        file + " is missing expected tracker: " + tracker);
            }
        }
    }

    @Test
    public void noFileReferencesAKnownDeadTracker() throws Exception {
        for (String file : FILES) {
            String source = readSource(file);
            for (String dead : DEAD_TRACKERS) {
                assertFalse(source.contains(dead),
                        file + " still references dead tracker: " + dead);
            }
        }
    }

    @Test
    public void allThreeFilesAgreeOnTheSameTrackerSet() throws Exception {
        Set<String> first = extractTrackers(readSource(FILES.get(0)));
        for (int i = 1; i < FILES.size(); i++) {
            Set<String> other = extractTrackers(readSource(FILES.get(i)));
            assertEquals(first, other,
                    "Tracker set in " + FILES.get(i) + " differs from " + FILES.get(0)
                            + "\n  only in first : " + difference(first, other)
                            + "\n  only in other : " + difference(other, first));
        }
        assertEquals(new LinkedHashSet<>(EXPECTED_TRACKERS), first,
                "Extracted trackers do not match the curated expected list");
    }

    @Test
    public void curatedListHasNoDuplicatesAndIsNonEmpty() {
        Set<String> unique = new TreeSet<>(EXPECTED_TRACKERS);
        assertEquals(EXPECTED_TRACKERS.size(), unique.size(),
                "EXPECTED_TRACKERS has duplicates: " + EXPECTED_TRACKERS);
        assertFalse(EXPECTED_TRACKERS.isEmpty(), "EXPECTED_TRACKERS is empty");
    }

    private static Set<String> extractTrackers(String source) {
        Matcher m = UDP_URL.matcher(source);
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> diff = new LinkedHashSet<>(a);
        diff.removeAll(b);
        return diff;
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.ISO_8859_1);
    }

    @SuppressWarnings("unused")
    private static List<String> asList(String... s) {
        return Arrays.asList(s);
    }
}
