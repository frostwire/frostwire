package com.frostwire.tests;

import com.frostwire.search.*;
import com.frostwire.search.archiveorg.ArchiveorgCrawledSearchResult;
import com.frostwire.search.archiveorg.ArchiveorgSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ArchiveorgSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(ArchiveorgSearchPerformerTest.class);

    @Test
    public void testSearch() {
        List<ArchiveorgCrawledSearchResult> searchResults = new ArrayList<>();
        SearchPerformer searchPerformer = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.ARCHIVEORG_ID).getPerformer(1, "free book");
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("Results: " + results.size());
                for (SearchResult r : results) {
                    if (r instanceof ArchiveorgSearchResult) {
                        searchPerformer.crawl((CrawlableSearchResult) r);
                        continue;
                    }
                    if (!(r instanceof ArchiveorgCrawledSearchResult)) {
                        continue;
                    }
                    ArchiveorgCrawledSearchResult sr = (ArchiveorgCrawledSearchResult) r;
                    searchResults.add(sr);
                    LOG.info("[ArchiveorgSearchPerformerTest] display: " + sr.getDisplayName());
                    LOG.info("[ArchiveorgSearchPerformerTest] details: " + sr.getDetailsUrl());
                    LOG.info("[ArchiveorgSearchPerformerTest] source: " + sr.getSource());
                    LOG.info("[ArchiveorgSearchPerformerTest] size: " + sr.getSize());
                    LOG.info("[ArchiveorgSearchPerformerTest] thumbnail: " + sr.getThumbnailUrl());
                    LOG.info("[ArchiveorgSearchPerformerTest] filename: " + sr.getFilename());
                    LOG.info("[ArchiveorgSearchPerformerTest] download url: " + sr.getDownloadUrl());
                    LOG.info("[ArchiveorgSearchPerformerTest] ---------------------");
                }
            }

            @Override
            public void onError(long token, SearchError error) {

            }

            @Override
            public void onStopped(long token) {

            }
        });
        searchPerformer.perform();
        Assertions.assertTrue(searchResults.size() > 0, "[ArchiveorgSearchPerformerTest] No results found");
    }
}
