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

package com.frostwire.search.archiveorg;

import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class ArchiveorgSearchPerformer extends CrawlPagedWebSearchPerformer<ArchiveorgSearchResult> {

    private static final int MAX_RESULTS = 12;

    public ArchiveorgSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, MAX_RESULTS);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "http://"
                + getDomainName()
                + "/advancedsearch.php?q="
                + encodedKeywords
                + "&fl[]=avg_rating&fl[]=call_number&fl[]=collection&fl[]=contributor&fl[]=coverage&fl[]=creator&fl[]=date&fl[]=description&fl[]=downloads&fl[]=foldoutcount&fl[]=format&fl[]=headerImage&fl[]=identifier&fl[]=imagecount&fl[]=language&fl[]=licenseurl&fl[]=mediatype&fl[]=month&fl[]=num_reviews&fl[]=oai_updatedate&fl[]=publicdate&fl[]=publisher&fl[]=rights&fl[]=scanningcentre&fl[]=source&fl[]=title&fl[]=type&fl[]=volume&fl[]=week&fl[]=year&rows=50&page=1&indent=yes&output=json";
        //sort[]=downloads+desc&sort[]=createdate+desc
        //sort[]=avg_rating+desc&
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        List<SearchResult> result = new LinkedList<SearchResult>();

        ArchiveorgResponse response = JsonUtils.toObject(page, ArchiveorgResponse.class);

        for (ArchiveorgItem item : response.response.docs) {
            if (!isStopped()) {
                ArchiveorgSearchResult sr = new ArchiveorgSearchResult(getDomainName(), item);
                result.add(sr);
            }
        }

        return result;
    }

    @Override
    protected String getCrawlUrl(ArchiveorgSearchResult sr) {
        return "http://" + getDomainName() + "/details/" + sr.getIdentifier() + "?output=json";
    }

    @Override
    protected List<? extends SearchResult> crawlResult(ArchiveorgSearchResult sr, byte[] data) throws Exception {
        List<ArchiveorgCrawledSearchResult> list = new LinkedList<ArchiveorgCrawledSearchResult>();

        String json = new String(data, "UTF-8");

        List<ArchiveorgFile> files = readFiles(json);

        long totalSize = calcTotalSize(files);

        for (ArchiveorgFile file : files) {
            if (isStreamable(file.filename)) {
                list.add(new ArchiveorgCrawledStreamableSearchResult(sr, file));
            } else if (file.filename.endsWith(".torrent")) {
                list.add(new ArchiveorgTorrentSearchResult(sr, file, totalSize));
            } else {
                list.add(new ArchiveorgCrawledSearchResult(sr, file));
            }
        }

        return list;
    }

    private List<ArchiveorgFile> readFiles(String json) throws Exception {
        List<ArchiveorgFile> result = new LinkedList<>();

        JsonElement element = new JsonParser().parse(json);
        JsonObject obj = element.getAsJsonObject();
        JsonObject files = obj.getAsJsonObject("files");

        Iterator<Map.Entry<String, JsonElement>> it = files.entrySet().iterator();

        while (it.hasNext() && !isStopped()) {
            Map.Entry<String, JsonElement> e = it.next();
            String name = e.getKey();
            String value = e.getValue().toString();
            ArchiveorgFile file = JsonUtils.toObject(value, ArchiveorgFile.class);
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

    private long calcTotalSize(List<ArchiveorgFile> files) {
        long size = 0;

        for (ArchiveorgFile f : files) {
            try {
                size += Long.parseLong(f.size);
            } catch (Throwable e) {
                // ignore
            }
        }

        return size;
    }

    private boolean filter(ArchiveorgFile file) {
        if (file.format != null && file.format.equalsIgnoreCase("metadata")) {
            return false;
        }

        return true;
    }
}
