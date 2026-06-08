/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.frostwire.bittorrent.DefaultTrackers;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #1004: keep {@link DefaultTrackers} honest.
 *
 * <p>The announce list is the single source of truth used by
 * {@code TorrentUtil}, {@code CreateTorrentDialog}, and the magnet URL
 * builder in {@code UrlUtils}. This test fails if the list is empty,
 * contains duplicates, introduces a known-dead host, or ships an
 * ill-formed UDP announce URL. It also verifies that
 * {@link DefaultTrackers#MAGNET_URL_PARAMETERS} is the well-formed
 * {@code "tr=<url>&"} projection of the underlying list — so a
 * caller can rely on the two constants being consistent.
 *
 * <p>Each entry was probed with a BEP 15 connect_request on 2026-06-08
 * and responded within 3s. When a tracker goes dead, add it to
 * {@link #DEAD_TRACKERS} (so future regressions are caught) and remove
 * it from {@link DefaultTrackers#ANNOUNCE_URLS}.
 */
public class DefaultTrackerListTest {

    private static final Pattern UDP_ANNOUNCE_URL = Pattern.compile(
            "udp://[A-Za-z0-9.\\-]+(?::\\d{1,5})?/announce");

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

    @Test
    public void announceListIsNonEmptyAndHasNoDuplicates() {
        List<String> urls = DefaultTrackers.ANNOUNCE_URLS;
        assertFalse(urls.isEmpty(), "DefaultTrackers.ANNOUNCE_URLS is empty");
        Set<String> unique = new TreeSet<>(urls);
        assertEquals(urls.size(), unique.size(),
                "DefaultTrackers.ANNOUNCE_URLS has duplicates: " + urls);
    }

    @Test
    public void everyAnnounceUrlMatchesExpectedFormat() {
        for (String url : DefaultTrackers.ANNOUNCE_URLS) {
            assertTrue(UDP_ANNOUNCE_URL.matcher(url).matches(),
                    "Malformed announce URL: " + url);
        }
    }

    @Test
    public void announceListDoesNotContainKnownDeadTrackers() {
        Set<String> alive = new LinkedHashSet<>(DefaultTrackers.ANNOUNCE_URLS);
        for (String dead : DEAD_TRACKERS) {
            assertFalse(alive.contains(dead),
                    "DefaultTrackers.ANNOUNCE_URLS reintroduced a known-dead tracker: " + dead);
        }
    }

    @Test
    public void magnetUrlParametersIsDerivedFromAnnounceUrls() {
        StringBuilder expected = new StringBuilder();
        for (String url : DefaultTrackers.ANNOUNCE_URLS) {
            expected.append("tr=").append(url).append('&');
        }
        assertEquals(expected.toString(), DefaultTrackers.MAGNET_URL_PARAMETERS,
                "MAGNET_URL_PARAMETERS must be the tr=...& projection of ANNOUNCE_URLS");
    }

    @Test
    public void magnetUrlParametersContainsEveryAnnounceUrlExactlyOnce() {
        for (String url : DefaultTrackers.ANNOUNCE_URLS) {
            String needle = "tr=" + url + "&";
            int first = DefaultTrackers.MAGNET_URL_PARAMETERS.indexOf(needle);
            assertTrue(first >= 0,
                    "MAGNET_URL_PARAMETERS missing entry for " + url);
            int second = DefaultTrackers.MAGNET_URL_PARAMETERS.indexOf(needle, first + 1);
            assertEquals(-1, second,
                    "MAGNET_URL_PARAMETERS contains duplicate entry for " + url);
        }
    }
}
