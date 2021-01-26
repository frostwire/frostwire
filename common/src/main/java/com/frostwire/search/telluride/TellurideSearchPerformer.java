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

public class TellurideSearchPerformer extends AbstractSearchPerformer {
    private static final Logger LOG = Logger.getLogger(TellurideSearchPerformer.class);
    private static Gson gson = null;
    private static Calendar calendar = null;
    private final CountDownLatch performanceLatch;

    private final String url;
    private final File tellurideLauncher;
    private final File saveDirectory;
    private final TellurideSearchPerformerListener performerListener;

    public TellurideSearchPerformer(long token,
                                    String _url,
                                    File _tellurideLauncher,
                                    File _saveDirectory,
                                    TellurideSearchPerformerListener _performerListener) {
        super(token);

        // Many of these could turn into a URL fix method.
        if (_url.contains("instagram.com/reel")) {
            _url = _url.replace("reel/", "p/");
        }

        url = _url;
        tellurideLauncher = _tellurideLauncher;
        saveDirectory = _saveDirectory;
        performerListener = _performerListener;
        performanceLatch = new CountDownLatch(1);
        if (gson == null) {
            gson = new GsonBuilder().create();
        }
        if (calendar == null) {
            calendar = Calendar.getInstance();
        }
    }

    @Override
    public void perform() {
        try {
            TellurideLauncher.launch(tellurideLauncher,
                    url,
                    saveDirectory,
                    false,
                    true,
                    false,
                    // MetaListener
                    new TellurideAbstractListener() {
                        @Override
                        public void onMeta(String json) {
                            TellurideSearchPerformer.this.onMeta(json);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            TellurideSearchPerformer.this.onError(errorMessage);
                        }
                    });
            LOG.info("perform(): working...");
            performanceLatch.await();
        } catch (IllegalArgumentException e) {
            if (performerListener != null) {
                performerListener.onTellurideBinaryNotFound(e);
            }
        } catch (InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
        LOG.info("perform(): finished.");
    }

    @Override
    public void crawl(CrawlableSearchResult sr) {
        // TellurideSearchResults aren't crawleable, this won't be called, no crawl tasks will be spawned for each result.
    }

    List<TellurideSearchResult> getValidResults(String jsonMeta) {
        TellurideJSONResult result = gson.fromJson(jsonMeta, TellurideJSONResult.class);

        if (performerListener != null) {
            performerListener.onTellurideJSONResult(getToken(), result);
        }

        // From these formats we pick the best video and best audio available to always return 2 search results.
        ArrayList<TellurideSearchResult> results = new ArrayList<>();
        if (result.formats == null) {
            LOG.info("formats are null, no valid search results for " + url);
            return results;
        }
        int originalResultCount = result.formats.size();
        for (TellurideJSONMediaFormat format : result.formats) {
            if (format.url.contains(".m3u8")) {
                // skip playlists for now
                // TODO: Implement .m3u8 telluride downloader
                continue;
            }
            if (originalResultCount > 1 && format.height != 0 && format.width > format.height && format.width < 320) {
                // skip low quality horizontal videos
                LOG.info("very low quality horizontal video, skipped");
                continue;
            }

            if (originalResultCount > 1 && format.height > format.width && format.height < 480) {
                // skip too low quality vertical videos
                LOG.info("very low quality vertical video, skipped");
                continue;
            }

            String videoFormatParenthesis = "";
            if (result.webpage_url.contains("youtu")) {
                if (noCodec(format.acodec)) {
                    LOG.info("acodec is null, skipped");
                    continue;
                }

                if (noCodec(format.vcodec) && noCodec(format.acodec)) {
                    LOG.info("acodec + vcodec are null, skipped");
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
            LOG.info("acodec=" + format.acodec + ", vcodec=" + format.vcodec + ", ext=" + result.ext + ", url=" + format.url);
            String domainName = UrlUtils.extractDomainName(format.url);
            if (domainName != null) {
                Ssl.addValidDomain(domainName);
            }

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
    private long dateStringToTimestamp(String YYYY_MM_DD) {
        int YEAR = Integer.parseInt(YYYY_MM_DD.substring(0, 4));
        int MONTH = Integer.parseInt(YYYY_MM_DD.substring(4, 6));
        int DATE = Integer.parseInt(YYYY_MM_DD.substring(6));
        calendar.set(Calendar.YEAR, YEAR);
        calendar.set(Calendar.MONTH, MONTH - 1);
        calendar.set(Calendar.DAY_OF_MONTH, DATE);
        return calendar.getTimeInMillis();
    }

    private void onMeta(String json) {
        List<TellurideSearchResult> results = getValidResults(json);
        onResults(results);

        if (performerListener != null) {
            performerListener.onSearchResults(getToken(), results);
        }
        // When a performer ends in the PerformTask, it's stopped (stopped=true) by the SearchManager
        // as it removes the task.
        // This latch is released so the PerformTask can finish.
        performanceLatch.countDown();
    }

    private void onError(String errorMessage) {
        if (performerListener != null) {
            performerListener.onError(getToken(), errorMessage);
        }
        performanceLatch.countDown();
    }

    private boolean noCodec(String codec) {
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
