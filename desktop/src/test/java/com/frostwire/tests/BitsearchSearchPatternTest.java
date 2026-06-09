/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.bitsearch.BitsearchSearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Bitsearch V2 Search Pattern Test
 *
 * <p>parseEnvelopeBuildsCompleteResults() is a pure unit test that
 * feeds a captured response body through BitsearchSearchPattern and
 * asserts the resulting CompositeFileSearchResult fields.
 *
 * <p>bitsearchSearchTest() is a live integration test against
 * https://bitsearch.eu/api/v1/search. It runs only when the JVM can
 * reach bitsearch.eu.
 *
 * <p>gradle test --tests "com.frostwire.tests.BitsearchSearchPatternTest"
 */
public final class BitsearchSearchPatternTest {
    private static final Logger LOG = Logger.getLogger(BitsearchSearchPatternTest.class);

    // Captured 2026-06-08 from a live GET /api/v1/search?q=ubuntu&limit=2.
    private static final String CAPTURED_RESPONSE =
            "{" +
            "\"success\":true," +
            "\"query\":\"ubuntu\"," +
            "\"results\":[" +
            "  {\"id\":\"5cb8afc48700981f3e5b00c4\"," +
            "   \"infohash\":\"D540FC48EB12F2833163EED6421D449DD8F1CE1F\"," +
            "   \"title\":\"ubuntu-19.04-desktop-amd64.iso\"," +
            "   \"size\":2097152000," +
            "   \"category\":1,\"subCategory\":7," +
            "   \"seeders\":28,\"leechers\":41," +
            "   \"downloads\":0,\"verified\":false," +
            "   \"updatedAt\":\"2026-06-08T19:45:43.908Z\"}," +
            "  {\"id\":\"63f864e1ae697358dc80e874\"," +
            "   \"infohash\":\"A7838B75C42B612DA3B6CC99BEED4ECB2D04CFF2\"," +
            "   \"title\":\"ubuntu-22.04.2-desktop-amd64.iso\"," +
            "   \"size\":4927586304," +
            "   \"category\":1,\"subCategory\":7," +
            "   \"seeders\":177,\"leechers\":331," +
            "   \"downloads\":0,\"verified\":false," +
            "   \"updatedAt\":\"2026-06-07T14:31:50.161Z\"}" +
            "]," +
            "\"pagination\":{\"page\":1,\"perPage\":2,\"total\":4153,\"totalPages\":2077,\"hasNext\":true,\"hasPrev\":false}," +
            "\"took\":1}";

    @Test
    public void parseEnvelopeBuildsCompleteResults() {
        BitsearchSearchPattern pattern = new BitsearchSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(CAPTURED_RESPONSE);

        assertNotNull(results);
        assertFalse(results.isEmpty(), "captured response should yield at least one result");
        assertEquals(2, results.size(), "captured response has exactly 2 entries");

        CompositeFileSearchResult first = (CompositeFileSearchResult) results.get(0);
        assertEquals("ubuntu-19.04-desktop-amd64.iso", first.getDisplayName());
        assertEquals("Bitsearch", first.getSource());
        assertEquals("D540FC48EB12F2833163EED6421D449DD8F1CE1F", first.getTorrentHash().orElse(null));
        assertEquals(2097152000L, first.getSize());
        assertTrue(first.getSeeds().isPresent());
        assertEquals(28, first.getSeeds().get());

        CompositeFileSearchResult second = (CompositeFileSearchResult) results.get(1);
        assertEquals("A7838B75C42B612DA3B6CC99BEED4ECB2D04CFF2", second.getTorrentHash().orElse(null));
        assertEquals(177, second.getSeeds().get());

        // Magnet link must contain the infohash and at least one of our
        // DefaultTrackers — that's the DRY-up from the previous commit.
        String magnet = first.getTorrentUrl().orElse(null);
        assertNotNull(magnet);
        assertTrue(magnet.contains("xt=urn:btih:D540FC48EB12F2833163EED6421D449DD8F1CE1F"),
                "magnet must include the infohash");
        assertTrue(magnet.contains("tracker.opentrackr.org") || magnet.contains("open.stealth.si"),
                "magnet must include at least one DefaultTrackers entry");
    }

    @Test
    public void parseResultsReturnsEmptyOnNullOrBlank() {
        BitsearchSearchPattern pattern = new BitsearchSearchPattern();
        assertTrue(pattern.parseResults(null).isEmpty());
        assertTrue(pattern.parseResults("").isEmpty());
        assertTrue(pattern.parseResults("   ").isEmpty());
        assertTrue(pattern.parseResults("not-json").isEmpty(),
                "malformed body should not throw, should return empty");
    }

    @Test
    public void parseResultsSkipsRecordsMissingRequiredFields() {
        // infohash missing on first record, title empty on second
        String body = "{\"success\":true,\"results\":[" +
                "{\"id\":\"a\",\"title\":\"foo\",\"infohash\":\"\",\"size\":1,\"seeders\":1,\"leechers\":0}," +
                "{\"id\":\"b\",\"title\":\"\",\"infohash\":\"ABCDEF1234567890ABCDEF1234567890ABCDEF12\",\"size\":1,\"seeders\":1,\"leechers\":0}," +
                "{\"id\":\"c\",\"title\":\"good\",\"infohash\":\"ABCDEF1234567890ABCDEF1234567890ABCDEF12\",\"size\":1,\"seeders\":1,\"leechers\":0}" +
                "]}";
        BitsearchSearchPattern pattern = new BitsearchSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(body);
        assertEquals(1, results.size(), "only the well-formed record should survive");
        assertEquals("good", results.get(0).getDisplayName());
    }

    @Test
    public void searchUrlTargetsBitsearchApiV1() {
        BitsearchSearchPattern pattern = new BitsearchSearchPattern();
        String url = pattern.getSearchUrl("ubuntu");
        assertTrue(url.startsWith("https://bitsearch.eu/api/v1/search?q="),
                "search URL must hit the v1 JSON endpoint, got: " + url);
        assertTrue(url.contains("limit="), "search URL should request a result limit");
    }

    @Test
    public void bitsearchSearchTest() {
        String searchTerm = "ubuntu";
        ISearchPerformer performer = SearchPerformerFactory.createSearchPerformer(
                1, searchTerm, new BitsearchSearchPattern(), null, 10000);

        BitsearchListener listener = new BitsearchListener();
        performer.setListener(listener);

        try {
            LOG.info("BitsearchSearchPatternTest: live search for '" + searchTerm + "'");
            performer.perform();
        } catch (Throwable t) {
            LOG.error("Search failed with exception", t);
            fail(t.getMessage());
            return;
        }

        if (!listener.failedMessages.isEmpty()) {
            LOG.error("Search validation failed: " + listener.getFailedMessages());
            fail(listener.getFailedMessages());
        }

        LOG.info("BitsearchSearchPatternTest: PASSED");
    }

    private static final class BitsearchListener implements SearchListener {
        final List<String> failedMessages = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.isEmpty()) {
                failedMessages.add("Bitsearch API returned no results — service may be down");
                return;
            }
            LOG.info("Got " + results.size() + " results");
            for (SearchResult result : results) {
                CompositeFileSearchResult sr = (CompositeFileSearchResult) result;
                LOG.info("  displayName: " + sr.getDisplayName());
                LOG.info("  size: " + sr.getSize());
                LOG.info("  source: " + sr.getSource());
                LOG.info("  hash: " + sr.getTorrentHash().orElse("NONE"));
                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedMessages.add("displayName is null or empty");
                }
                if (!"Bitsearch".equals(sr.getSource())) {
                    failedMessages.add("source should be 'bitsearch', got: " + sr.getSource());
                }
                if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                    failedMessages.add("detailsUrl is null or empty");
                }
                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedMessages.add("filename is null or empty");
                }
                if (!sr.isTorrent()) {
                    failedMessages.add("result should be a torrent");
                }
                if (sr.isPreliminary()) {
                    failedMessages.add("result should NOT be preliminary");
                }
                if (sr.getSize() <= 0) {
                    failedMessages.add("size should be > 0");
                }
                if (failedMessages.size() > 0) {
                    return;
                }
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            failedMessages.add("Search error: " + error.message());
        }

        @Override
        public void onStopped(long token) {
        }

        String getFailedMessages() {
            StringBuilder sb = new StringBuilder();
            for (String m : failedMessages) {
                sb.append(m).append('\n');
            }
            return sb.toString();
        }
    }
}
