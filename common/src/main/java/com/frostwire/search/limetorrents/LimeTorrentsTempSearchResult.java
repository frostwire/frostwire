package com.frostwire.search.limetorrents;

import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;

/**
 * Created by alejandroarturom on 26-08-16.
 */
public final class LimeTorrentsTempSearchResult extends AbstractSearchResult implements CrawlableSearchResult {

    private final String itemId;
    private final String detailsUrl;

   LimeTorrentsTempSearchResult(String domainName, String itemId) {
        this.itemId = itemId;
        this.detailsUrl = "https://" + domainName + "/torrent/" + itemId + ".html";
    }

    public String getItemId() {
        return itemId;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public boolean isComplete() {
        return false;
    }
}
