/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.search;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.search.torrent.TorrentItemSearchResult;
import org.apache.commons.io.FilenameUtils;

/**
 * When a SearchPerformer crawls a page, its
 * crawlResult(CrawlableSearchResult sr, byte[] data) method is invoked.
 * usually the 'data' holds the HTML with the main information of the torrent
 * and such page may contain information about the torrent's files (TorrentScrapedFileSearchResults)
 * or a link to another page holding those, in which case your implementation of crawlResult
 * would have to make a second request.
 * <p/>
 * This class is for you to use when modeling the torrent scraped file search results found.
 * You will initialize such scraped results by passing the main torrent results found by the
 * parent call to crawlResult.
 * <p/>
 * <p/>
 * In this HTML there might be information about the torrent files
 *
 * @author gubatron
 * @author aldenml
 */
public class ScrapedTorrentFileSearchResult<T extends AbstractTorrentSearchResult> extends AbstractCrawledSearchResult<T> implements TorrentItemSearchResult {

    private final String filePath;
    private final String filename;
    private final String displayName;
    private final String referrerUrl;
    private final String cookie;
    private final long size;

    public ScrapedTorrentFileSearchResult(T parent, String filePath, long fileSize, String referrerUrl, String cookie) {
        super(parent);
        this.filePath = filePath;
        this.filename = FilenameUtils.getName(this.filePath);
        this.displayName = FilenameUtils.getBaseName(this.filename);
        this.referrerUrl = referrerUrl;
        this.cookie = cookie;
        this.size = fileSize;
    }

    public ScrapedTorrentFileSearchResult(T parent, String filePath, long fileSize) {
        this(parent, filePath, fileSize, parent.getDetailsUrl(), null);
    }

    // all the data that must be scraped.

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    // all data that can be obtained from the parent

    @Override
    public String getTorrentUrl() {
        return parent.getTorrentUrl();
    }

    @Override
    public int getSeeds() {
        return parent.getSeeds();
    }

    @Override
    public String getHash() {
        return parent.getHash();
    }

    @Override
    public String getReferrerUrl() {
        return referrerUrl;
    }

    public String getCookie() {
        return cookie;
    }
}
