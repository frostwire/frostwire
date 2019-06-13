/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), alejandroarturom
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.limetorrents;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

/**
 * @author alejandroarturom
 */
// TODO: this performer does not need to download a torrent to get the details
public class LimeTorrentsSearchPerformer extends TorrentRegexSearchPerformer<LimeTorrentsSearchResult> {
    private static final int MAX_RESULTS = 20;
    private static final String REGEX = "(?is)<a href=\"http://itorrents.org/torrent/(.*?).torrent?(.*?)\" rel=\"nofollow\" class=\"csprite_dl14\"></a><a href=\"(?<itemid>.*?).html?(.*?)\">.*?</a></div>.*?";
    private static final String HTML_REGEX =
            "(?is)<title>(?<filename>.*?)</title>.*?" +
                    "<span class=\"greenish\">Seeders : (?<seeds>\\d*?)</span>.*?" +
                    "<tr><td align=\"right\"><b>Hash</b> :</td><td>(?<torrentid>.*?)</td></tr>.*?" +
                    "<tr><td align=\"right\"><b>Added</b> :</td><td>(?<time>.*?) in.*?" +
                    "<tr><td align=\"right\"><b>Size</b> :</td><td>(?<filesize>.*?) (?<unit>[A-Z]+)</td></tr>.*?" +
                    "<a href=\"magnet:(?<magnet_part>.*?)\".*?></a>";

    public LimeTorrentsSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("0%20", "-");
        return "https://" + getDomainName() + "/search/all/" + transformedKeywords + "/seeds/1/";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group("itemid");
        String transformedId = itemId.replaceFirst("/", "");
        return new LimeTorrentsTempSearchResult(getDomainName(), transformedId);
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf("<div><h3>Latest Searches</h3>");
        return offset > 0 ? offset : 0;
    }

    @Override
    protected LimeTorrentsSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new LimeTorrentsSearchResult(sr.getDetailsUrl(), matcher);
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudflare");
    }
}
