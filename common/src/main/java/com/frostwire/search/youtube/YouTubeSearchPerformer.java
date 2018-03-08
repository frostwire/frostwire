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

package com.frostwire.search.youtube;

import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlRegexSearchPerformer;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.SearchResult;
import com.frostwire.search.youtube.YouTubeExtractor.LinkInfo;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static com.frostwire.search.youtube.YouTubeUtils.isDash;

/**
 * @author gubatron
 * @author aldenml
 */
public final class YouTubeSearchPerformer extends CrawlRegexSearchPerformer<YouTubeSearchResult> {

    private static final Logger LOG = Logger.getLogger(YouTubeSearchPerformer.class);

    private static final String REGEX = "(?is)<h3 class=\"yt-lockup-title.*?\"><a href=\"(?<link>/watch.*?)\".*? title=\"(?<title>.*?)\".*? Duration: (?<duration>.*?)\\.</span>.*?(by |byline.*?\">).*?<a href=\"/(user|channel)/(?<user>.*?)\"";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private static final int MAX_RESULTS = 20;

    // regex for secondary playlist
    private static final Pattern TITLE_SECONDARY_PATTERN = Pattern.compile("(?is)<h2 class=\"watch-card-title\"><a .*\">(?<title>.*?)</a></h2>");
    private static final Pattern LIST_SECONDARY_PATTERN = Pattern.compile("(?is)\"><a href=\"(?<link>/watch.*?)&.*?\" .*?\">(?<title>.*?)</a></td><td class=\"watch-card-data-col\">(?<duration>.*?)</td></tr><tr");

    public YouTubeSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 2, MAX_RESULTS, MAX_RESULTS);
    }

    @Override
    protected String getCrawlUrl(YouTubeSearchResult sr) {
        return null;
    }

    @Override
    protected List<? extends SearchResult> crawlResult(YouTubeSearchResult sr, byte[] data) throws Exception {
        List<SearchResult> list = new LinkedList<>();

        String detailsUrl = sr.getDetailsUrl();
        List<LinkInfo> infos = new YouTubeExtractor().extract(detailsUrl, false);

        LinkInfo dashVideo = null;
        LinkInfo dashAudio = null;
        LinkInfo demuxVideo = null;

        LinkInfo minQuality = null;

        for (LinkInfo inf : infos) {
            if (inf.fmt == 18) {
                // save this format for the heuristic of selecting audio
                // from dash or demuxing a video one
                demuxVideo = inf;
            }

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
            }
        }

        if (dashVideo != null && dashAudio != null) {
            list.add(new YouTubeCrawledSearchResult(sr, dashVideo, dashAudio));
        }

        LinkInfo infoAudio = selectFormatForAudio(sr, dashAudio, demuxVideo);
        if (infoAudio != null) {
            list.add(new YouTubeCrawledStreamableSearchResult(sr, null, infoAudio, minQuality));
        }

        YouTubePackageSearchResult packagedResult = new YouTubePackageSearchResult(sr, list);
        List<SearchResult> results = new LinkedList<>();
        results.add(packagedResult);
        results.addAll(list);
        return results;
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return String.format(Locale.US, "https://" + getDomainName() + "/results?search_query=%s", encodedKeywords);
    }

    @Override
    public Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected int preliminaryHtmlPrefixOffset(String page) {
        int offset = page.indexOf("<div class=\"yt-uix-hovercard-content\">");
        if (offset == -1) {
            offset = page.indexOf("class=\"num-results first-focus\"");
        }
        return offset;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        LinkedList<SearchResult> r = new LinkedList<>();
        r.addAll(super.searchPage(page));

        performSecondaryContent(page, r);

        return r;
    }

    @Override
    protected boolean isValidHtml(String html) {
        return true;
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

    // yes, mutable but private...performance and immutable from outside
    private void performSecondaryContent(String page, LinkedList<SearchResult> r) {
        try {
            String s1 = "earch-secondary-col-contents\">";
            String s2 = "<div id=\"ad_creative_1";
            int i1 = page.indexOf(s1);
            int i2 = page.indexOf(s2);
            if (i1 > 0 && i2 > 0 && i1 < i2) {
                page = page.substring(i1 + s1.length(), i2);

                SearchMatcher mTitle = SearchMatcher.from(TITLE_SECONDARY_PATTERN.matcher(page));
                if (mTitle.find()) {
                    String user = mTitle.group("title");
                    SearchMatcher mList = SearchMatcher.from(LIST_SECONDARY_PATTERN.matcher(page));

                    while (mList.find()) {
                        String link = mList.group("link");
                        String title = HtmlManipulator.replaceHtmlEntities(mList.group("title"));
                        String duration = mList.group("duration");
                        YouTubeSearchResult sr = new YouTubeSearchResult(link, title, duration, user);
                        if (!r.contains(sr)) {
                            r.add(sr);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warn("Error parsing secondary content", e);
        }
    }

    private LinkInfo selectFormatForAudio(YouTubeSearchResult sr,
        LinkInfo dashAudio, LinkInfo demuxVideo) {

        return demuxVideo;
        // NOTE: code commented just in case we need it later
        /*
        if (dashAudio == null)
            return demuxVideo;
        if (demuxVideo == null)
            return dashAudio;

        if (dashAudio.size == -1) {
            // can't calculate heuristic
            return dashAudio;
        }

        double bitRateSum = 0.5 + 96d / 1024d;
        bitRateSum = bitRateSum * 1024 * 1024; //Mbits to bits.
        long size = (long) (Math.ceil((bitRateSum * sr.getSize()) / 8));

        // the case that the video track is more or less still
        if (5 * dashAudio.size > size) {
            return demuxVideo;
        }

        // if the length is rather small, use demux anyway
        if (size < 40000000) {
            return demuxVideo;
        }

        return dashAudio;
        */
    }

    /*
    public static void main(String[] args) throws UnsupportedEncodingException {
        byte[] readAllBytes = new byte[0];
        try {
            readAllBytes = Files.readAllBytes(Paths.get("/Users/gubatron/Desktop/youtube.html"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String fileStr = new String(readAllBytes,"utf-8");

        Pattern pattern = Pattern.compile(REGEX);
        //Pattern pattern = Pattern.compile(HTML_REGEX);

        Matcher matcher = pattern.matcher(fileStr);

        int found = 0;
        while (matcher.find()) {
            found++;
            System.out.println("\nfound " + found);
            System.out.println("link: [" + matcher.group("link") + "]");
            System.out.println("===");

            SearchMatcher sm = new SearchMatcher(matcher);
            //
            // EztvSearchResult sr = new EztvSearchResult("http://someurl.com", sm);
        }
        System.out.println("-done-");
    }*/
}
