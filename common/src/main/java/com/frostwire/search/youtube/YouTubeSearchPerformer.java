/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.search.youtube;

import com.frostwire.search.CrawlRegexSearchPerformer;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.SearchResult;
import com.frostwire.search.youtube.YouTubeExtractor.LinkInfo;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.regex.Pattern;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static com.frostwire.search.youtube.YouTubeUtils.isDash;

/**
 * @author gubatron
 * @author aldenml
 */
public class YouTubeSearchPerformer extends CrawlRegexSearchPerformer<YouTubeSearchResult> {

    private static final String REGEX = "(?is)<h3 class=\"yt-lockup-title[ ]*\"><a href=\"(?<link>.*?)\".*? title=\"(?<title>.*?)\".*? Duration: (?<duration>.*?)\\.</span>.*?(by |byline\">)<a href=\"/user/(?<user>.*?)\"";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private static final int MAX_RESULTS = 15;

    public YouTubeSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, MAX_RESULTS, MAX_RESULTS);
    }

    @Override
    protected String getCrawlUrl(YouTubeSearchResult sr) {
        return null;
    }

    @Override
    protected List<? extends SearchResult> crawlResult(YouTubeSearchResult sr, byte[] data) throws Exception {
        List<YouTubeCrawledSearchResult> list = new LinkedList<YouTubeCrawledSearchResult>();

        String detailsUrl = sr.getDetailsUrl();
        List<LinkInfo> infos = new YouTubeExtractor().extract(detailsUrl, false);

        LinkInfo dashVideo = null;
        LinkInfo dashAudio = null;
        LinkInfo demuxVideo = null;

        LinkInfo minQuality = null;

        for (LinkInfo inf : infos) {
            if (!isDash(inf)) {
                if (inf.fmt == 18) {
                    minQuality = inf;
                }
                list.add(new YouTubeCrawledStreamableSearchResult(sr, inf, null, minQuality));
            } else {
                if (inf.fmt == 137) {// 1080p
                    dashVideo = inf;
                }
                if (inf.fmt == 141) {// 256k
                    dashAudio = inf;
                }
                if (inf.fmt == 140 && dashAudio == null) {// 128k
                    dashAudio = inf;
                }
                if (inf.fmt == 22 || inf.fmt == 84) {
                    demuxVideo = inf;
                }
            }
        }

        if (dashVideo != null && dashAudio != null) {
            list.add(new YouTubeCrawledSearchResult(sr, dashVideo, dashAudio));
        }

        if (dashAudio != null) {
            list.add(new YouTubeCrawledStreamableSearchResult(sr, null, dashAudio, minQuality));
        } else {
            if (demuxVideo != null) {
                list.add(new YouTubeCrawledStreamableSearchResult(sr, null, demuxVideo, minQuality));
            }
        }

        return list;
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return String.format(Locale.US, "https://"+getDomainName()+"/results?search_query=%s", encodedKeywords);
    }

    @Override
    public Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected int preliminaryHtmlPrefixOffset(String page) {
        return page.indexOf("<div class=\"yt-uix-hovercard-content\">");
    }

    @Override
    protected int preliminaryHtmlSuffixOffset(String page) {
        return page.indexOf("<div id=\"footer-container\"");
    }

    @Override
    public YouTubeSearchResult fromMatcher(SearchMatcher matcher) {
        String link = matcher.group("link");
        String title = HtmlManipulator.replaceHtmlEntities(matcher.group("title"));
        String duration = matcher.group("duration");
        String user = matcher.group("user");

        return new YouTubeSearchResult(link, title, duration, user);
    }

    @Override
    protected String fetchSearchPage(String url) throws IOException {
        return fetch(url, "PREF=hl=en&f4=4000000&f5=30&f1=50000000;", null);
    }
}
