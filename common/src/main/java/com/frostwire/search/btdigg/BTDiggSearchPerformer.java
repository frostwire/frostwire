package com.frostwire.search.btdigg;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

public class BTDiggSearchPerformer extends SimpleTorrentSearchPerformer {
    private final static Logger LOG = Logger.getLogger(BTDiggSearchPerformer.class);

    private static Pattern searchPattern;
    private boolean isDDOSProtectionActive;

    public BTDiggSearchPerformer(long token, String keywords, int timeout) {
        super("btdig.com", token, keywords, timeout, 1, 0);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        String searchUrl = "https://" + getDomainName() + "/search?q=" + encodedKeywords + "&p=0&order=2";
        return searchUrl;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        if (page == null || page.isEmpty() || page.contains("0 results found")) {
            return new ArrayList<>();
        }
        int startOffset = page.indexOf("class=\"torrent_name\"");
        if (startOffset == -1 ) {
            //captcha showed up
            isDDOSProtectionActive = true;
            return new ArrayList<>();
        }
        String reducedPage = page;

        if (startOffset > 0) {
            reducedPage = page.substring(startOffset);
        } else {
            LOG.warn("BTDiggSearchPerformer()::searchPage() could not reduce page");
        }

        if (searchPattern == null) {
            searchPattern = Pattern.compile("(?is)<a style=\"color:rgb\\(0, 0, 204\\);text-decoration:underline;font-size:150%\" href=\"(?<detailUrl>.*?)\">(?<displayName>.*?)</a>.*?" +
                    "<span class=\"torrent_size\" style=\"color:#666;padding-left:10px\">(?<size>.*?)</span>.*?" +
                    "<div class=\"torrent_magnet\".*?a href=\"(?<magnet>.*?)\" title=");
        }
        SearchMatcher matcher = new SearchMatcher(searchPattern.matcher(reducedPage));
        List<BTDiggSearchResult> results = new ArrayList<>();
        boolean matcherFound = false;
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                LOG.error("BTDiggSearchPerformer.searchPage() has failed.\n" + t.getMessage(), t);
                matcherFound = false;
            }
            if (matcherFound) {
                try {
                    BTDiggSearchResult sr = new BTDiggSearchResult(getDomainName(), matcher);
                    results.add(sr);
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            }
        } while (matcherFound && !isStopped());
        return results;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return isDDOSProtectionActive;
    }
}
