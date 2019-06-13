/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.search.archiveorg;

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
public class ArchiveorgSearchPerformer extends CrawlPagedWebSearchPerformer<ArchiveorgSearchResult> {
    private static final int MAX_RESULTS = 12;

    public ArchiveorgSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, MAX_RESULTS);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://"
                + getDomainName()
                + "/advancedsearch.php?q="
                + encodedKeywords
                + "&fl[]=avg_rating&fl[]=call_number&fl[]=collection&fl[]=contributor&fl[]=coverage&fl[]=creator&fl[]=date&fl[]=description&fl[]=downloads&fl[]=foldoutcount&fl[]=format&fl[]=headerImage&fl[]=identifier&fl[]=imagecount&fl[]=language&fl[]=licenseurl&fl[]=mediatype&fl[]=month&fl[]=num_reviews&fl[]=oai_updatedate&fl[]=publicdate&fl[]=publisher&fl[]=rights&fl[]=scanningcentre&fl[]=source&fl[]=title&fl[]=type&fl[]=volume&fl[]=week&fl[]=year&rows=50&page=1&indent=yes&output=json";
        //sort[]=downloads+desc&sort[]=createdate+desc
        //sort[]=avg_rating+desc&
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        List<SearchResult> result = new LinkedList<>();
        ArchiveorgResponse response = JsonUtils.toObject(page, ArchiveorgResponse.class);
        for (ArchiveorgItem item : response.response.docs) {
            if (!isStopped() && filter(item)) {
                ArchiveorgSearchResult sr = new ArchiveorgSearchResult(getDomainName(), item);
                result.add(sr);
            }
        }
        return result;
    }

    @Override
    protected String getCrawlUrl(ArchiveorgSearchResult sr) {
        return "https://" + getDomainName() + "/details/" + sr.getIdentifier() + "?output=json";
    }

    @Override
    protected List<? extends SearchResult> crawlResult(ArchiveorgSearchResult sr, byte[] data) throws Exception {
        List<ArchiveorgCrawledSearchResult> list = new LinkedList<>();
        String json = new String(data, StandardCharsets.UTF_8);
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

    private List<ArchiveorgFile> readFiles(String json) {
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

    private boolean filter(ArchiveorgItem item) {
        return !(item.collection != null && item.collection.contains("samples_only"));
    }

    private boolean filter(ArchiveorgFile file) {
        return !(file.format != null && file.format.equalsIgnoreCase("metadata"));
    }
}
