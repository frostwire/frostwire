/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.bitsnoop;

import com.frostwire.util.Logger;
import com.frostwire.search.*;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;

import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class BitSnoopSearchPerformer extends TorrentRegexSearchPerformer<BitSnoopSearchResult> {
    private static Logger LOG = Logger.getLogger(BitSnoopSearchPerformer.class);
    private static final int MAX_RESULTS = 10;
    private static final String REGEX = "(?is)<span class=\"icon cat.*?</span> <a href=\"(.*?)\">.*?<div class=\"torInfo\"";
    private static final String HTML_REGEX = "(?is)"+
            ".*?No client needed.*?<a href=\"(?<magneturl>.*?)\" title=\"Magnet Link\" class=\"dlbtn dl_mag2\".*?" +
            "Help</a>, <a href=\"magnet:\\?xt=urn:btih:(?<infohash>[0-9a-fA-F]{40})&dn=(?<filename>.*?)\" onclick=\".*?Magnet</a>.*?" +
            "<a href=\"(?<torrenturl>.*?)\" title=\".*?\".*?" +
            "title=\"Torrent Size\"><strong>(?<size>.*?)</strong>.*?" +
            "title=\"Availability\"></span>(?<seeds>.*?)</span></td>.*?" +
            "<li>Added to index &#8212; (?<creationtime>.*?) \\(.{0,50}?\\)</li>.*?";
    private static final String SCRAPE_REGEX = "(?is)<td .*?<span class=\"filetype .*?</span> (?<filepath>.*?)</td><td align=\"right\"><span class=\"icon.*?\"></span>(?<filesize>.*?) (?<unit>[GBMK]+)</td>";
    private boolean isScrapingFile = false;
    private static final Pattern FILE_SCRAPE_PATTERN = Pattern.compile(SCRAPE_REGEX);

    public BitSnoopSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "http://" + getDomainName() + "/search/all/" + encodedKeywords + "/c/d/" + page + "/";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        return new BitSnoopTempSearchResult(getDomainName(), itemId);
    }

    @Override
    protected BitSnoopSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new BitSnoopSearchResult(sr.getDetailsUrl(), matcher);
    }

    @Override
    protected int preliminaryHtmlPrefixOffset(String html) {
        int offset = 0;
        final String HTML_CUE_POINT = "Search results for";
        try {
            offset = html.indexOf(HTML_CUE_POINT);
            if (offset == -1) {
                offset = 0;
            } else {
                offset += HTML_CUE_POINT.length();
            }
        } catch (Throwable t) {
        }
        return offset;
    }

    @Override
    protected int preliminaryHtmlSuffixOffset(String html) {
        int offset = html.length() - 1;
        try {
            offset = html.indexOf("<div id=\"pages\"");
            if (offset == -1) {
                //no pagination, few or no results found.
                offset = html.indexOf("Last queries:");
                if (offset == -1) {
                    offset = html.length() - 1;
                }
            }
        } catch (Throwable t) {
        }
        return offset;
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        // we save at least 21kb of memory for every crawl.
        if (!isScrapingFile) {
            //when super.crawlResult is called it invokes htmlPrefixOffset
            //but it initially needs to crawl for the parent search result
            //if we're not yet scraping, we use our preliminaryHtmlPrefixOffset instead.
            //This probably is a sign that TorrentRegexSearchPerformer needs a refactor
            //to support crawling for a parent result and for a child result on pages
            //that have both things.
            //Things that come to mind would be to differentiate between .crawlResult
            //and .scrapeFiles()
            //and then have reduction offset methods for scraping, such as
            //scrapePrefixOffset():int and scrapeSuffixOffset():int
            return preliminaryHtmlPrefixOffset(html);
        }

        int offset = 0;
        final String HTML_CUE_POINT = "Torrent Contents";
        try {
            offset = html.indexOf(HTML_CUE_POINT);
            if (offset == -1) {
                offset = 0;
            } else {
                offset += HTML_CUE_POINT.length();
            }
        } catch (Throwable t) {
        }
        return offset;
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        if (!isScrapingFile) {
            // preliminaryHtmlSuffixOffset is not called on crawlResult() but this
            // is the offset we need when going for the parent result.
            return preliminaryHtmlSuffixOffset(html);
        }

        int offset = html.length() - 1;
        try {
            offset = html.indexOf("Additional Information");
            if (offset == -1) {
                //no pagination, few or no results found.
                offset = html.indexOf("Last queries:");
                if (offset == -1) {
                    offset = html.length() - 1;
                }
            }
        } catch (Throwable t) {
        }
        return offset;
    }

    @Override
    protected List<? extends SearchResult> crawlResult(CrawlableSearchResult sr, byte[] data) throws Exception {
        if (!(sr instanceof BitSnoopTempSearchResult)) {
            return Collections.emptyList();
        }

        List<SearchResult> searchResults = new LinkedList<>();
        byte[] detailPageData = null;
        // sr is a temp result.
        if (!sr.isComplete()) {
            detailPageData = fetchBytes(sr.getDetailsUrl());
            searchResults.addAll(super.crawlResult(sr, detailPageData, false));

            if (searchResults == null || searchResults.isEmpty()) {
                return Collections.emptyList();
            }
        }

        final String fullHtml = new String(detailPageData, "UTF-8");
        final BitSnoopSearchResult parent = (BitSnoopSearchResult) searchResults.get(0);
        isScrapingFile = true;
        final String scrapeHtml = PerformersHelper.reduceHtml(fullHtml, htmlPrefixOffset(fullHtml), htmlSuffixOffset(fullHtml));
        final Matcher matcher = FILE_SCRAPE_PATTERN.matcher(scrapeHtml);
        while (matcher.find()) {
            try {
                String line = matcher.group();
                if (line != null && line.contains("\"filetype dir\"")) {
                    continue;
                }
                final String filePath = HtmlManipulator.replaceHtmlEntities(matcher.group("filepath"));
                final long fileSize = parseSize(matcher.group("filesize"), matcher.group("unit"));

                ScrapedTorrentFileSearchResult<BitSnoopSearchResult> scrapedResult =
                        new ScrapedTorrentFileSearchResult<BitSnoopSearchResult>(parent,
                                filePath,
                                fileSize,
                                null,//"https://torcache.net/",
                                null
                        );

                searchResults.add(scrapedResult);
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
            }
        }

        // TODO: aldenml - Add album logic here, can't reason with isScrapingFile mutable flag.

        isScrapingFile = false;
        return searchResults;
    }

    private long parseSize(String filesize, String unit) {
        filesize = filesize.replaceAll(",", "");
        double size = Double.parseDouble(filesize);

        if (UNIT_TO_BYTES.containsKey(unit)) {
            size = size * UNIT_TO_BYTES.get(unit);
        } else {
            size = -1;
        }
        return (long) size;
    }
}
