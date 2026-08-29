/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.telluride;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchListener;
import com.frostwire.util.Logger;
import com.frostwire.util.Ssl;
import com.frostwire.util.UrlUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * V2-compatible search performer for Telluride (cloud backup/streaming platform).
 * Uses non-HTTP transport (local process on desktop, Python VM RPC on Android).
 * Implements ISearchPerformer directly without extending legacy V1 base classes.
 *
 * This search performer only launches the RPC backend on desktop since we don't include
 * a standalone Python VM for desktop.
 * In Android, a new Python VM is used for search and then some methods here are used, like getValidResults()
 */
public class TellurideSearchPerformer implements ISearchPerformer {
    private static final Logger LOG = Logger.getLogger(TellurideSearchPerformer.class);
    private static Gson gson = null;
    private static Calendar calendar = null;

    private final long token;
    private final CountDownLatch performerLatch;
    private final String url;
    private final TellurideSearchPerformerListener performerListener;
    private final File tellurideLauncher;
    private final boolean playlistMode;

    protected boolean stopped;
    private SearchListener listener;

    public TellurideSearchPerformer(long token,
                                    String _url,
                                    TellurideSearchPerformerListener _performerListener,
                                    File _tellurideLauncher) {
        this(token, _url, _performerListener, _tellurideLauncher, false);
    }

    public TellurideSearchPerformer(long token,
                                    String _url,
                                    TellurideSearchPerformerListener _performerListener,
                                    File _tellurideLauncher,
                                    boolean _playlistMode) {
        this.token = token;

        if (_url.contains("instagram.com/reel")) {
            _url = _url.replace("reel/", "p/");
        }
        url = _url;
        performerListener = _performerListener;
        tellurideLauncher = _tellurideLauncher;
        playlistMode = _playlistMode;

        performerLatch = new CountDownLatch(1);
        if (gson == null) {
            gson = new GsonBuilder().create();
        }
        if (calendar == null) {
            calendar = Calendar.getInstance();
        }
    }


    public void perform() {
        stopped = false;
        TellurideLauncher.launch(tellurideLauncher,
                url,
                null,
                false,
                !playlistMode,
                playlistMode,
                false,
                new TellurideProcessListener(this));
        try {
            performerLatch.await();
        } catch (InterruptedException e) {
        }
        stopped = true;
    }

    @Override
    public void crawl(CrawlableSearchResult sr) {
        // TellurideSearchResults aren't crawleable, this won't be called, no crawl tasks will be spawned for each result.
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    public static List<TellurideSearchResult> getValidResults(String jsonMeta, Gson gson, TellurideSearchPerformerListener performerListener, long token, String debugUrl) {
        TellurideJSONResult result = gson.fromJson(jsonMeta, TellurideJSONResult.class);

        if (performerListener != null) {
            performerListener.onTellurideJSONResult(token, result);
        }

        // YouTube no longer offers muxed progressive MP4. Video is DASH
        // video-only (acodec=none) and audio is separate. Skip neither:
        // pick the best of each so a pasted watch URL shows video AND audio.
        ArrayList<TellurideSearchResult> results = new ArrayList<>();
        if (result.formats == null) {
            LOG.info("getValidResults formats are null, no valid search results for " + debugUrl);
            return results;
        }
        int originalResultCount = result.formats.size();
        TellurideJSONMediaFormat bestVideo = null;
        TellurideJSONMediaFormat bestAudio = null;
        for (TellurideJSONMediaFormat format : result.formats) {
            if (format == null || format.url == null || format.url.isEmpty()) {
                continue;
            }
            if (format.url.contains(".m3u8") || "mhtml".equals(format.ext)) {
                continue;
            }
            if (originalResultCount > 1 && format.height != 0 && format.width > format.height && format.width < 320) {
                continue;
            }
            if (originalResultCount > 1 && format.height > format.width && format.height < 480) {
                continue;
            }
            if (noCodec(format.vcodec) && noCodec(format.acodec)) {
                continue;
            }
            boolean audioOnly = !noCodec(format.acodec) && noCodec(format.vcodec);
            if (audioOnly) {
                if (bestAudio == null || format.filesize > bestAudio.filesize) {
                    bestAudio = format;
                }
            } else if (!noCodec(format.vcodec)) {
                if (bestVideo == null
                        || format.height > bestVideo.height
                        || (format.height == bestVideo.height && format.filesize > bestVideo.filesize)) {
                    bestVideo = format;
                }
            }
        }
        if (bestVideo != null) {
            results.add(toSearchResult(result, bestVideo));
        }
        if (bestAudio != null) {
            results.add(toSearchResult(result, bestAudio));
        }
        return results;
    }

    private static TellurideSearchResult toSearchResult(
            TellurideJSONResult result, TellurideJSONMediaFormat format) {
        String videoFormatParenthesis = "";
        if (!noCodec(format.acodec) && noCodec(format.vcodec)) {
            videoFormatParenthesis = "(audio)";
        } else if (!noCodec(format.vcodec)) {
            if (format.width != 0 && format.height != 0) {
                videoFormatParenthesis = "(" + format.width + "x" + format.height + ")";
            } else if (format.width == 0 && format.height != 0) {
                videoFormatParenthesis = "(" + format.height + "p)";
            }
        } else if (format.height > 240) {
            videoFormatParenthesis = "(" + format.height + "p)";
        }
        LOG.info("getValidResults acodec=" + format.acodec + ", vcodec=" + format.vcodec
                + ", ext=" + format.ext + ", url=" + format.url);
        String domainName = UrlUtils.extractDomainName(format.url);
        if (domainName != null) {
            Ssl.addValidDomain(domainName);
        }
        return new TellurideSearchResult(
                result.id,
                videoFormatParenthesis + " " + result.title,
                result.title + " " + videoFormatParenthesis + "." + format.ext,
                "Cloud:" + result.extractor,
                result.webpage_url,
                format.url,
                result.thumbnail,
                format.filesize,
                result.upload_date == null ? calendar.getTimeInMillis() : dateStringToTimestamp(result.upload_date),
                withFullRange(format.http_headers));
    }

    private static final Set<String> MEDIA_HEADER_ALLOWLIST = Set.of(
            "user-agent",
            "referer",
            "cookie",
            "origin",
            "accept",
            "accept-language",
            "authorization",
            "x-youtube-client-name",
            "x-youtube-client-version",
            "sec-fetch-mode");

    static Map<String, String> withFullRange(Map<String, String> headers) {
        Map<String, String> result = new HashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                if (MEDIA_HEADER_ALLOWLIST.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        result.put("Range", "bytes=0-");
        return result;
    }


public static List<TellurideSearchResult> getValidPlaylistResults(String jsonMeta, Gson gson, TellurideSearchPerformerListener performerListener, long token, String debugUrl) {
        TellurideJSONPlaylist playlist = gson.fromJson(jsonMeta, TellurideJSONPlaylist.class);
        ArrayList<TellurideSearchResult> results = new ArrayList<>();
        if (playlist.entries == null) {
            LOG.info("getValidPlaylistResults entries are null, no valid search results for " + debugUrl);
            return results;
        }
        for (TellurideJSONPlaylistEntry entry : playlist.entries) {
            String source = "Cloud:" + playlist.extractor;
            String detailsUrl = entry.webpage_url != null ? entry.webpage_url : entry.url;
            long creationTime = entry.upload_date != null ? dateStringToTimestamp(entry.upload_date) : calendar.getTimeInMillis();
            results.add(new TellurideSearchResult(
                    entry.id,
                    entry.title,
                    source,
                    detailsUrl,
                    entry.thumbnail,
                    creationTime));
        }
        return results;
    }

    //20200324
    private static long dateStringToTimestamp(String YYYY_MM_DD) {
        int YEAR = Integer.parseInt(YYYY_MM_DD.substring(0, 4));
        int MONTH = Integer.parseInt(YYYY_MM_DD.substring(4, 6));
        int DATE = Integer.parseInt(YYYY_MM_DD.substring(6));
        if (calendar == null) {
            calendar = Calendar.getInstance();
        }
        calendar.set(Calendar.YEAR, YEAR);
        calendar.set(Calendar.MONTH, MONTH - 1);
        calendar.set(Calendar.DAY_OF_MONTH, DATE);
        return calendar.getTimeInMillis();
    }

    private void onMeta(String json) {
        List<TellurideSearchResult> results = getValidResults(json, gson, performerListener, getToken(), url);
        onResults(results);

        if (performerListener != null) {
            performerListener.onSearchResults(getToken(), results);
        }
        performerLatch.countDown();
    }

    private void onPlaylistMeta(String json) {
        List<TellurideSearchResult> results = getValidPlaylistResults(json, gson, performerListener, getToken(), url);
        onResults(results);

        if (performerListener != null) {
            performerListener.onSearchResults(getToken(), results);
        }
        performerLatch.countDown();
    }

    private void onError(String errorMessage) {
        if (performerListener != null) {
            performerListener.onError(getToken(), errorMessage);
        }
        performerLatch.countDown();
    }

    @Override
    public long getToken() {
        return token;
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            if (listener != null) {
                listener.onStopped(token);
            }
        } catch (Throwable e) {
            LOG.warn("Error sending finished signal to listener: " + e.getMessage());
        }
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public SearchListener getListener() {
        return listener;
    }

    @Override
    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return false;
    }

    protected void onResults(List<? extends com.frostwire.search.SearchResult> results) {
        if (stopped) {
            return;
        }
        try {
            if (results == null) {
                results = new ArrayList<>();
            }
            if (listener != null) {
                listener.onResults(token, results);
            }
        } catch (Throwable e) {
            LOG.warn("Error sending results to listener: " + e.getMessage());
        }
    }

    private static boolean noCodec(String codec) {
        return codec == null || "none".equals(codec);
    }

    public static class TellurideJSONResult {
        public String id;
        public String ext;
        public String title;
        public String extractor;
        public String webpage_url;
        public String upload_date;
        public String thumbnail;
        public List<TellurideJSONMediaFormat> formats;
    }

    public static class TellurideJSONPlaylist {
        public String type;
        public String title;
        public String extractor;
        public List<TellurideJSONPlaylistEntry> entries;
    }

    public static class TellurideJSONPlaylistEntry {
        public String id;
        public String title;
        public String url;
        public String webpage_url;
        public String thumbnail;
        public long duration;
        public String upload_date;
        public long view_count;
        public String description;
    }

    public static class TellurideJSONMediaFormat {
        @SuppressWarnings("unused")
        public String format_id;
        public String url;
        public String ext;
        public String acodec;
        public long filesize;
        public String vcodec;
        public int height;
        public int width;
        public Map<String, String> http_headers;
    }

    private static class TellurideProcessListener extends TellurideAbstractListener {
        private final TellurideSearchPerformer performer;

        public TellurideProcessListener(TellurideSearchPerformer performer) {
            this.performer = performer;
        }

        @Override
        public void onMeta(String json) {
            if (json.contains("\"type\"") && json.contains("\"playlist\"")) {
                performer.onPlaylistMeta(json);
            } else {
                performer.onMeta(json);
            }
        }

        @Override
        public void onError(String errorMessage) {
            performer.onError(errorMessage);
        }
    }
}
