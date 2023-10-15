package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchResult;
import com.frostwire.search.torlock.TorLockTempSearchResult;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class TorLockSearchPerformerTest {
    @Test
    public void testSearch() {
        TorLockSearchPerformer searchPerformer = (TorLockSearchPerformer) SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.TORLOCK_ID).getPerformer(1, "free book");
        List<TorLockSearchResult> searchResults = new ArrayList<>();
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                for (SearchResult r : results) {
                    if (!(r instanceof TorLockSearchResult sr)) {
                        searchPerformer.crawl((TorLockTempSearchResult) r);
                        continue;
                    }
                    searchResults.add(sr);
                    System.out.println("[TorLockSearchPerformerTest] size: " + sr.getSize());
                    System.out.println("[TorLockSearchPerformerTest] hash: " + sr.getHash());
                    System.out.println("[TorLockSearchPerformerTest] ---------------------");
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
        Assertions.assertTrue(searchResults.size() > 0, "[TorLockSearchPerformerTest] No results found");
    }

}
