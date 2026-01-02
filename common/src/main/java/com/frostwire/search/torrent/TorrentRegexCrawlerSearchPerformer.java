/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search.torrent;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.*;
import com.frostwire.util.Logger;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

/**
 * Use this search performer if you have to crawl search result links to get the data you need,
 * otherwise @see SimpleTorrentSearchPerformer to obtain everything directly from a search results page.
 *
 * The constructor receives 2 regex patterns, one for the search results page and one for the detail page.
 *
 * @author gubatron
 * @author aldenml
 */
public abstract class TorrentRegexCrawlerSearchPerformer<T extends CrawlableSearchResult> extends CrawlRegexSearchPerformer<CrawlableSearchResult> {
    private final static Logger LOG = Logger.getLogger(TorrentRegexCrawlerSearchPerformer.class);
    private final Pattern preliminarySearchResultsPattern;
    private final Pattern htmlDetailPagePattern;

    public TorrentRegexCrawlerSearchPerformer(String domainName, long token, String keywords, int timeout, int pages, int numCrawls, int regexMaxResults, String preliminarySearchResultsRegex, String htmlDetailPagePatternRegex) {
        super(domainName, token, keywords, timeout, pages, numCrawls, regexMaxResults);
        this.preliminarySearchResultsPattern = Pattern.compile(preliminarySearchResultsRegex);
        this.htmlDetailPagePattern = Pattern.compile(htmlDetailPagePatternRegex);
    }

    @Override
    public Pattern getPattern() {
        return preliminarySearchResultsPattern;
    }

    public Pattern getDetailsPattern() {
        return htmlDetailPagePattern;
    }

    @Override
    protected String getCrawlUrl(CrawlableSearchResult sr) {
        String crawlUrl;
        if (sr instanceof TorrentCrawlableSearchResult) {
            crawlUrl = ((TorrentCrawlableSearchResult) sr).getTorrentUrl();
        } else {
            crawlUrl = sr.getDetailsUrl();
        }
        return crawlUrl;
    }

    @Override
    protected List<? extends SearchResult> crawlResult(CrawlableSearchResult sr, byte[] data) throws Exception {
        return crawlResult(sr, data, false);
    }

    protected List<? extends SearchResult> crawlResult(CrawlableSearchResult sr, byte[] data, boolean detectAlbums) throws Exception {
        List<SearchResult> list = new LinkedList<>();
        if (data == null) {
            return list;
        }
        if (sr instanceof TorrentCrawlableSearchResult) {
            //in case we fetched a torrent's info (magnet, or the .torrent itself) to obtain 
            list.addAll(PerformersHelper.crawlTorrentInfo(this, (TorrentCrawlableSearchResult) sr, data, detectAlbums));
        } else {
            String unreducedHtml = new String(data, StandardCharsets.UTF_8);
            if (!isValidHtml(unreducedHtml)) {
                LOG.warn("invalid html from " + sr.getClass().getSimpleName());
                return list;
            }
            String html = PerformersHelper.reduceHtml(unreducedHtml, htmlPrefixOffset(unreducedHtml), htmlSuffixOffset(unreducedHtml));
            if (html != null) {
                Matcher matcher = htmlDetailPagePattern.matcher(html);
                try {
                    // BOOKMARK: this is a good spot to put a break point in-order to test your search performer's regex
                    if (matcher.find()) {
                        T searchResult = fromHtmlMatcher(sr, SearchMatcher.from(matcher));
                        if (searchResult != null) {
                            list.add(searchResult);
                        }
                    } else {
                        LOG.error("TorrentRegexSearchPerformer.crawlSearchResult(" + sr.getClass().getPackage().getName() + "): Update Necessary: Search broken.\n(please notify dev-team on twitter @frostwire or write to contact@frostwire.com if you keep seeing this message.)\n" +
                                "pattern: " + htmlDetailPagePattern.toString() + "\n" +
                                sr.getDetailsUrl() + "\n\n");
                        // comment this when in production
                        //LOG.info("================================================================\n\n"+html);
                    }
                } catch (Throwable e) {
                    throw new Exception("URL:" + sr.getDetailsUrl() + " (" + e.getMessage() + ")", e);
                }
            } else {
                LOG.error("Update Necessary: HTML could not be reduced for optimal search. Search broken for " + sr.getClass().getPackage().getName() + " (please notify dev-team on twitter @frostwire or write to contact@frostwire.com if you keep seeing this message.)");
            }
        }
        return list;
    }

    /**
     * Sometimes the TORRENT_DETAILS_PAGE_REGEX has to work on too big of an HTML file.
     * In order to minimize the chance for long backtracking times we can
     * override this methods to specify what offsets of the HTML file our
     * SEARCH_RESULTS_REGEX should be focusing on.
     */
    protected int htmlPrefixOffset(String html) {
        return 0;
    }

    /**
     * Sometimes the TORRENT_DETAILS_PAGE_REGEX has to work on too big of an HTML file.
     * In order to minimize the chance for long backtracking times we can
     * override this methods to specify what offsets of the HTML file our
     * SEARCH_RESULTS_REGEX should be focusing on.
     */
    protected int htmlSuffixOffset(String html) {
        return html.length();
    }

    protected abstract T fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher);
}
