/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.search.telluride;

import com.frostwire.search.AbstractSearchPerformer;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.Ssl;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.http.HttpClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * This search performer only launches the RPC backend on desktop since we don't include
 * a standalone Python VM for desktop.
 * In Android, a new Python VM is used for search and then some methods here are used, like getValidResults()
 */
public class TellurideSearchPerformer extends AbstractSearchPerformer {
    private static final Logger LOG = Logger.getLogger(TellurideSearchPerformer.class);
    private static Gson gson = null;
    private static Calendar calendar = null;
    private final CountDownLatch performerLatch;
    private final String url;
    private final TellurideSearchPerformerListener performerListener;
    private final File tellurideLauncher;
    private final int rpcPort;
    private final File torrentsDir;



    public TellurideSearchPerformer(long token,
                                    String _url,
                                    TellurideSearchPerformerListener _performerListener,
                                    File _tellurideLauncher,
                                    int _rpcPort,
                                    File _torrentsDir) {
        super(token);

        // Many of these could turn into a URL fix method.
        if (_url.contains("instagram.com/reel")) {
            _url = _url.replace("reel/", "p/");
        }
        url = _url;
        performerListener = _performerListener;
        tellurideLauncher = _tellurideLauncher;
        rpcPort = _rpcPort;
        torrentsDir = _torrentsDir;

        performerLatch = new CountDownLatch(1);
        if (gson == null) {
            gson = new GsonBuilder().create();
        }
        if (calendar == null) {
            calendar = Calendar.getInstance();
        }
    }

    @Override
    public void perform() {
        int seconds_to_wait_for_telluride_server = 15;
        while (!TellurideLauncher.SERVER_UP.get() && seconds_to_wait_for_telluride_server > 0) {
            LOG.info("perform(): waiting for Telluride Server to be up... (" + seconds_to_wait_for_telluride_server + " secs left to time out)");
            try {
                //noinspection BusyWait
                Thread.sleep(1000);
                seconds_to_wait_for_telluride_server--;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (seconds_to_wait_for_telluride_server == 0) {
            LOG.info("perform(): timed out waiting for telluride server to start. finished. Invoking SearchEngine.startTellurideRPCServer()");
            TellurideLauncher.startTellurideRPCServer(tellurideLauncher, rpcPort, torrentsDir);
            return;
        }

        try {
            HttpClient httpClient = HttpClientFactory.newInstance();
            int TELLURIDE_RPC_PORT = 47999;
            String queryUrl = String.format("http://127.0.0.1:%d/?url=%s",
                    TELLURIDE_RPC_PORT,
                    UrlUtils.encode(url));
            LOG.info("perform(): working on " + queryUrl);
            String tellurideJSON = httpClient.get(queryUrl);
            TellurideSearchPerformer.this.onMeta(tellurideJSON);
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
            TellurideSearchPerformer.this.onError(e.getMessage());
        }
        LOG.info("perform(): finished.");
    }

    @Override
    public void crawl(CrawlableSearchResult sr) {
        // TellurideSearchResults aren't crawleable, this won't be called, no crawl tasks will be spawned for each result.
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
                LOG.info("getValidResults format.url contains .m3u8");
                continue;
            }
            if (originalResultCount > 1 && format.height != 0 && format.width > format.height && format.width < 320) {
                // skip low quality horizontal videos
                LOG.info("getValidResults very low quality horizontal video, skipped");
                continue;
            }

            if (originalResultCount > 1 && format.height > format.width && format.height < 480) {
                // skip too low quality vertical videos
                LOG.info("getValidResults very low quality vertical video, skipped");
                continue;
            }

            String videoFormatParenthesis = "";
            if (result.webpage_url.contains("youtu")) {
                if (noCodec(format.acodec)) {
                    LOG.info("getValidResults acodec is null, skipped");
                    continue;
                }

                if (noCodec(format.vcodec) && noCodec(format.acodec)) {
                    LOG.info("getValidResults acodec + vcodec are null, skipped");
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
}
