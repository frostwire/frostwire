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

package com.frostwire.search.bitsnoop;

import com.frostwire.logging.Logger;
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
    private static final String HTML_REGEX = "(?is).*?Help</a>, <a href=\"magnet:\\?xt=urn:btih:([0-9a-fA-F]{40})&dn=(.*?)\" onclick=\".*?Magnet</a>.*?<a href=\"(.*?)\" title=\".*?\" class=\"dlbtn.*?title=\"Torrent Size\"><strong>(.*?)</strong>.*?title=\"Availability\"></span>(.*?)</span></td>.*?<li>Added to index &#8212; (.*?) \\(.{0,50}?\\)</li>.*?";
    private static final String SCRAPE_REGEX = "(?is)<td .*?<span class=\"filetype .*?</span> (?<filepath>.*?)</td><td align=\"right\"><span class=\"icon.*?\"></span>(?<filesize>.*?) (?<unit>[GBMK]+)</td>";
    private boolean isScrapingFile = false;
    private static final Pattern FILE_SCRAPE_PATTERN = Pattern.compile(SCRAPE_REGEX);

    private static final Map<String, Integer> UNIT_TO_BYTES;

    static {
        UNIT_TO_BYTES = new HashMap<String, Integer>();
        UNIT_TO_BYTES.put("bytes", 1);
        UNIT_TO_BYTES.put("B", 1);
        UNIT_TO_BYTES.put("KB", 1024);
        UNIT_TO_BYTES.put("MB", 1024 * 1024);
        UNIT_TO_BYTES.put("GB", 1024 * 1024 * 1024);
    }

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

        List<SearchResult> searchResults = new LinkedList<SearchResult>();
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
