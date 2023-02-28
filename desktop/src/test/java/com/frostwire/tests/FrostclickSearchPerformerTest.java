package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.frostclick.FrostClickSearchPerformer;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class FrostclickSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(FrostclickSearchPerformerTest.class);

    @Test
    public void testFrostClickSearchPerformer() {
        SearchPerformer searchPerformer = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.FROSTCLICK_ID).getPerformer(1, "https://www.youtube.com/watch?v=OtMYSeRrF8M");
        List<String> errors = new ArrayList<>();
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("onResults: " + results.size());
            }

            @Override
            public void onError(long token, SearchError error) {
                errors.add(error.toString());
            }

            @Override
            public void onStopped(long token) {
                LOG.info("onStopped");
            }
        });
        searchPerformer.perform();
        Assertions.assertTrue(((FrostClickSearchPerformer) searchPerformer).wasResponseOk(), "FrostClickSearchPerformer response was not as expected");
    }
}
