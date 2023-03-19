/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.yt;


import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.PagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YouTube Search performer
 */
public class YTSearchPerformer extends PagedWebSearchPerformer {
    private final static Logger LOG = Logger.getLogger(YTSearchPerformer.class);

    private static Pattern jsonPattern;

    private final Map<String, Integer> unitsToSeconds = new HashMap<>();

    public YTSearchPerformer(long token, String keywords, int timeout, int pages) {
        super("www.youtube.com", token, keywords, timeout, pages);
        if (jsonPattern == null) {
            jsonPattern = Pattern.compile("(\"videoRenderer\":.*?\"searchVideoResultEntityKey\")");
        }
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        unitsToSeconds.put("second", 1);
        unitsToSeconds.put("minute", minute);
        unitsToSeconds.put("hour", hour);
        unitsToSeconds.put("day", day);
        unitsToSeconds.put("week", week);
        unitsToSeconds.put("month", month);
        unitsToSeconds.put("year", year);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/results?search_query=" + encodedKeywords;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        Matcher jsonMatcher = jsonPattern.matcher(page);
        List<YTSearchResult> results = new ArrayList<>();
        while (jsonMatcher.find()) {
            String json = jsonMatcher.group(1);
            json = json.replace("\"videoRenderer\":", "") + ":\"\"}";
            GsonBuilder gsonBuilder = new GsonBuilder();
            Gson gson = gsonBuilder.create();
            Video video = gson.fromJson(json, Video.class);

            String title = video.title.runs.get(0).text;
            String videoAge = video.publishedTimeText.simpleText;
            long creationTimeInMillis = parseCreationTimeInMillis(videoAge);
            String thumbnailUrl = video.thumbnail.thumbnails.get(0).url;
            String detailsUrl = "https://" + getDomainName() + "/watch?v=" + video.videoId;
            int viewCount = Integer.parseInt(video.viewCountText.simpleText.replace(",", "").replace(" views", ""));
            YTSearchResult searchResult = new YTSearchResult(title, detailsUrl, creationTimeInMillis, thumbnailUrl, viewCount);
            LOG.info("YTSearchPerformer() searchPage() searchResult: " + searchResult);
            results.add(searchResult);
        }
        return results;
    }

    private long parseCreationTimeInMillis(String creationString) {
        creationString = creationString.toLowerCase().replace("s", "").replace("ago", "");
        String[] parts = creationString.split(" ");
        int time = Integer.parseInt(parts[0]);
        String unit = parts[1];
        return System.currentTimeMillis() - (1000L * time * unitsToSeconds.get(unit));
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    public class Video {
        public String videoId;
        public Thumbnail thumbnail;
        public Title title;
        public PublishedTimeText publishedTimeText;
        public ViewCountText viewCountText;
    }

    public class Thumbnail {
        public List<ThumbnailDetails> thumbnails;
    }

    public class ThumbnailDetails {
        public String url;
    }

    public class Title {
        public List<Runs> runs;
    }

    public class Runs {
        public String text;
    }

    public class PublishedTimeText {
        public String simpleText;
    }

    public class ViewCountText {
        public String simpleText;
    }
}
