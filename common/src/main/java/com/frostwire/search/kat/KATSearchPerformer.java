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

package com.frostwire.search.kat;

import com.frostwire.logging.Logger;
import com.frostwire.search.ScrapedTorrentFileSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentCrawlableSearchResult;
import com.frostwire.search.torrent.TorrentJsonSearchPerformer;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.JsonUtils;
import com.frostwire.regex.Pattern;

import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class KATSearchPerformer extends TorrentJsonSearchPerformer<KATItem, KATSearchResult> {

    private static final Logger LOG = Logger.getLogger(KATSearchPerformer.class);

    private static final Map<String, Integer> UNIT_TO_BYTES;

    static {
        UNIT_TO_BYTES = new HashMap<String, Integer>();
        UNIT_TO_BYTES.put("bytes", 1);
        UNIT_TO_BYTES.put("B", 1);
        UNIT_TO_BYTES.put("KB", 1024);
        UNIT_TO_BYTES.put("MB", 1024 * 1024);
        UNIT_TO_BYTES.put("GB", 1024 * 1024 * 1024);
    }

    private static final String FILES_REGEX = "(?is)<tr.*?<td class=\"torFileName\" title=\".*?\">(?<filename>.*?)</td>.*?<td class=\"torFileSize\">(?<size>.*?) <span>(?<unit>.*?)</span></td>.*?</tr>";
    private static final Pattern FILES_PATTERN = Pattern.compile(FILES_REGEX);

    public KATSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "http://" + getDomainName() + "/json.php?q=" + encodedKeywords;
    }

    @Override
    protected List<KATItem> parseJson(String json) {
        KATResponse response = JsonUtils.toObject(json, KATResponse.class);
        fixItems(response.list);
        return response.list;
    }

    @Override
    protected KATSearchResult fromItem(KATItem item) {
        return new KATSearchResult(item);
    }

    @Override
    protected List<? extends SearchResult> crawlResult(TorrentCrawlableSearchResult sr, byte[] data) throws Exception {
        if (!(sr instanceof KATSearchResult)) {
            return Collections.emptyList();
        }

        List<ScrapedTorrentFileSearchResult> result = new LinkedList<ScrapedTorrentFileSearchResult>();

        KATSearchResult ksr = (KATSearchResult) sr;
        String page = fetch("http://" + getDomainName() + "/torrents/getfiles/" + ksr.getHash() + "/?all=1");

        SearchMatcher matcher = SearchMatcher.from(FILES_PATTERN.matcher(page));

        while (matcher.find()) {
            try {
                String filename = HtmlManipulator.replaceHtmlEntities(matcher.group("filename"));
                String sizeStr = matcher.group("size");
                String unit = matcher.group("unit");

                double size = Double.parseDouble(sizeStr);

                if (UNIT_TO_BYTES.containsKey(unit)) {
                    size = size * UNIT_TO_BYTES.get(unit);
                } else {
                    size = -1;
                }

                result.add(new ScrapedTorrentFileSearchResult<KATSearchResult>(ksr, filename, (long) size, "https://torcache.net/", null));

            } catch (Throwable e) {
                LOG.warn("Error creating single file search result", e);
            }
        }

        /**LinkedList<SearchResult> temp = new LinkedList<SearchResult>();
        temp.addAll(result);
        temp.addAll(new AlbumCluster().detect(sr, result));
         */
        return result;
    }

    private void fixItems(List<KATItem> list) {
        if (list != null && list.size() > 0) {
            Iterator<KATItem> iterator = list.iterator();
            while (iterator.hasNext()) {
                KATItem next = iterator.next();

                if (next.verified == 0) {
                    iterator.remove();
                }

                if (next.torrentLink.contains("torcache.net") &&
                    next.torrentLink.contains(".torrent?title=")) {
                    next.torrentLink = next.torrentLink.substring(0, next.torrentLink.indexOf("?title"));
                }
            }
        }
    }
}
