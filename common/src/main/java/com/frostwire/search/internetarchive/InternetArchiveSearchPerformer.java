/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.search.internetarchive;

import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class InternetArchiveSearchPerformer extends CrawlPagedWebSearchPerformer<InternetArchiveSearchResult> {
    private static final int MAX_RESULTS = 12;

    public InternetArchiveSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, MAX_RESULTS);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://"
                + getDomainName()
                + "/advancedsearch.php?q="
                + encodedKeywords
                + "&fl[]=avg_rating&fl[]=call_number&fl[]=collection&fl[]=contributor&fl[]=coverage&fl[]=creator&fl[]=date&fl[]=description&fl[]=downloads&fl[]=foldoutcount&fl[]=format&fl[]=headerImage&fl[]=identifier&fl[]=imagecount&fl[]=language&fl[]=licenseurl&fl[]=mediatype&fl[]=month&fl[]=num_reviews&fl[]=oai_updatedate&fl[]=publicdate&fl[]=publisher&fl[]=rights&fl[]=scanningcentre&fl[]=source&fl[]=title&fl[]=type&fl[]=volume&fl[]=week&fl[]=year&rows=50&page=1&output=json";
        //sort[]=downloads+desc&sort[]=createdate+desc
        //sort[]=avg_rating+desc&
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        List<SearchResult> result = new LinkedList<>();
        InternetArchiveItem[] items = JsonUtils.toObject(page, InternetArchiveItem[].class);
        for (InternetArchiveItem item : items) {
            if (!isStopped() && filter(item)) {
                InternetArchiveSearchResult sr = new InternetArchiveSearchResult(getDomainName(), item);
                result.add(sr);
            }
        }
        return result;
    }

    @Override
    protected String getCrawlUrl(InternetArchiveSearchResult sr) {
        return "https://" + getDomainName() + "/details/" + sr.getIdentifier() + "?output=json";
    }

    @Override
    protected List<? extends SearchResult> crawlResult(InternetArchiveSearchResult sr, byte[] data) throws Exception {
        List<InternetArchiveCrawledSearchResult> list = new LinkedList<>();
        String json = new String(data, StandardCharsets.UTF_8);
        List<InternetArchiveFile> files = readFiles(json);
        long totalSize = calcTotalSize(files);
        for (InternetArchiveFile file : files) {
            if (isStreamable(file.filename)) {
                list.add(new InternetArchiveCrawledStreamableSearchResult(sr, file));
            } else if (file.filename.endsWith(".torrent")) {
                list.add(new InternetArchiveTorrentSearchResult(sr, file, totalSize));
            } else {
                list.add(new InternetArchiveCrawledSearchResult(sr, file));
            }
        }
        return list;
    }

    private List<InternetArchiveFile> readFiles(String json) {
        List<InternetArchiveFile> result = new LinkedList<>();
        JsonElement element = JsonParser.parseString(json);
        JsonObject obj = element.getAsJsonObject();
        JsonObject files = obj.getAsJsonObject("files");
        Iterator<Map.Entry<String, JsonElement>> it = files.entrySet().iterator();
        while (it.hasNext() && !isStopped()) {
            Map.Entry<String, JsonElement> e = it.next();
            String name = e.getKey();
            String value = e.getValue().toString();
            InternetArchiveFile file = JsonUtils.toObject(value, InternetArchiveFile.class);
            if (filter(file)) {
                file.filename = cleanName(name);
                result.add(file);
            }
        }
        return result;
    }

    private String cleanName(String name) {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name;
    }

    private long calcTotalSize(List<InternetArchiveFile> files) {
        long size = 0;
        for (InternetArchiveFile f : files) {
            try {
                size += Long.parseLong(f.size);
            } catch (Throwable e) {
                // ignore
            }
        }
        return size;
    }

    private boolean filter(InternetArchiveItem item) {
        return !(item.collection != null && item.collection.contains("samples_only"));
    }

    private boolean filter(InternetArchiveFile file) {
        return !(file.format != null && file.format.equalsIgnoreCase("metadata"));
    }

    @Override
    public boolean isCrawler() {
        return true;
    }
}
