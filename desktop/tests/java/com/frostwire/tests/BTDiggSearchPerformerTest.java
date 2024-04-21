package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.btdigg.BTDiggSearchPerformer;
import com.frostwire.search.btdigg.BTDiggSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class BTDiggSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(BTDiggSearchPerformerTest.class);

    @Test
    public void testSearch() {
        BTDiggSearchPerformer searchPerformer = (BTDiggSearchPerformer) SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.BT_DIGG).getPerformer(1, "creative commons");
        List<BTDiggSearchResult> testResults = new ArrayList<>();
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                for (SearchResult result : results) {
                    BTDiggSearchResult bsr = (BTDiggSearchResult) result;
//                    LOG.info("[BTDiggSearchPerformerTest] displayname: " + bsr.getDisplayName());
//                    LOG.info("[BTDiggSearchPerformerTest] detailsUrl: " + bsr.getDetailsUrl());
//                    LOG.info("[BTDiggSearchPerformerTest] torrentUrl: " + bsr.getTorrentUrl());
//                    LOG.info("[BTDiggSearchPerformerTest] seeds: " + bsr.getSeeds());
//                    LOG.info("[BTDiggSearchPerformerTest] size: " + bsr.getSize());
                    LOG.info("[BTDiggSearchPerformerTest] infoHash: " + bsr.getHash());
//                    LOG.info("[BTDiggSearchPerformerTest] filename: " + bsr.getFilename());
//                    LOG.info("=====================================");
                    testResults.add((BTDiggSearchResult) result);
                }
            }

            @Override
            public void onError(long token, SearchError error) {
                LOG.error("[BTDiggSearchPerformerTest] error: " + error);
            }

            @Override
            public void onStopped(long token) {

            }
        });

        searchPerformer.perform();
        Assertions.assertTrue(!searchPerformer.isDDOSProtectionActive() && !testResults.isEmpty(), "[BTDiggSearchPerformerTest] No results found");
    }
}
