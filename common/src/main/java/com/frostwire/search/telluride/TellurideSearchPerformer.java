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
import java.util.List;
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

    protected boolean stopped;
    private SearchListener listener;

    public TellurideSearchPerformer(long token,
                                    String _url,
                                    TellurideSearchPerformerListener _performerListener,
                                    File _tellurideLauncher) {
        this.token = token;

        // Many of these could turn into a URL fix method.
        if (_url.contains("instagram.com/reel")) {
            _url = _url.replace("reel/", "p/");
        }
        url = _url;
        performerListener = _performerListener;
        tellurideLauncher = _tellurideLauncher;

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
                null, // saveDirectory
                false, // audioOnly
                true, // metaOnly
                false, // verboseOutput
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

        // From these formats we pick the best video and best audio available to always return 2 search results.
        ArrayList<TellurideSearchResult> results = new ArrayList<>();
        if (result.formats == null) {
            LOG.info("getValidResults formats are null, no valid search results for " + debugUrl);
            return results;
        }
        int originalResultCount = result.formats.size();
        for (TellurideJSONMediaFormat format : result.formats) {
            if (format.url.contains(".m3u8")) {
                // skip playlists for now
                // TODO: Implement .m3u8 telluride downloader
                //LOG.info("getValidResults format.url contains .m3u8");
                continue;
            }
            if (originalResultCount > 1 && format.height != 0 && format.width > format.height && format.width < 320) {
                // skip low quality horizontal videos
                //LOG.info("getValidResults very low quality horizontal video, skipped");
                continue;
            }

            if (originalResultCount > 1 && format.height > format.width && format.height < 480) {
                // skip too low quality vertical videos
                //LOG.info("getValidResults very low quality vertical video, skipped");
                continue;
            }

            String videoFormatParenthesis = "";
            if (result.webpage_url.contains("youtu")) {
                if (noCodec(format.acodec)) {
                    //LOG.info("getValidResults acodec is null, skipped");
                    continue;
                }

                if (noCodec(format.vcodec) && noCodec(format.acodec)) {
                    //LOG.info("getValidResults acodec + vcodec are null, skipped");
                    continue;
                }
            }

            if (!noCodec(format.acodec) && noCodec(format.vcodec)) {
                videoFormatParenthesis = "(audio)";
            } else if (!noCodec(format.vcodec)) {
                if (format.width != 0 && format.height != 0) {
                    videoFormatParenthesis = "(" + format.width + "x" + format.height + ")";
                } else if (format.width == 0 && format.height != 0) {
                    videoFormatParenthesis = "(" + format.height + "p)";
                }
            } else if (noCodec(format.acodec) && noCodec(format.vcodec) && format.height > 240) {
                videoFormatParenthesis = "(" + format.height + "p)";
            }
            LOG.info("getValidResults acodec=" + format.acodec + ", vcodec=" + format.vcodec + ", ext=" + result.ext + ", url=" + format.url);
            String domainName = UrlUtils.extractDomainName(format.url);
            if (domainName != null) {
                Ssl.addValidDomain(domainName);
            }
            LOG.info("TellurideSearchPerformer.getValidResults format.url added: " + format.url);
            results.add(new TellurideSearchResult(
                    result.id,
                    videoFormatParenthesis + " " + result.title,
                    result.title + " " + videoFormatParenthesis + "." + format.ext,
                    "Cloud:" + result.extractor,
                    result.webpage_url,
                    format.url,
                    result.thumbnail,
                    format.filesize,
                    result.upload_date == null ? calendar.getTimeInMillis() : dateStringToTimestamp(result.upload_date)));
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
        // When a performer ends in the PerformTask, it's stopped (stopped=true) by the SearchManager
        // as it removes the task.
        // This latch is released so the PerformTask can finish.
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
    }

    private static class TellurideProcessListener extends TellurideAbstractListener {
        private final TellurideSearchPerformer performer;

        public TellurideProcessListener(TellurideSearchPerformer performer) {
            this.performer = performer;
        }

        @Override
        public void onMeta(String json) {
            performer.onMeta(json);
        }

        @Override
        public void onError(String errorMessage) {
            performer.onError(errorMessage);
        }
    }
}
