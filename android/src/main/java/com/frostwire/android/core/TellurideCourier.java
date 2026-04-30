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

package com.frostwire.android.core;

import androidx.annotation.NonNull;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchError;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public final class TellurideCourier {
    private static final Logger LOG = Logger.getLogger(TellurideCourier.class);
    private static volatile Gson gson = null;
    private static volatile TellurideCourierCallback lastKnownCallback = null;

    public static void abortCurrentQuery() {
        if (lastKnownCallback == null) {
            return;
        }

        lastKnownCallback.abort();
    }

    // runs on SEARCH_PERFORMER HandlerThread
    public static <T extends AbstractListAdapter<? super TellurideSearchResult>> void queryPage(String url, TellurideCourierCallback<T> callback) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, () -> queryPage(url, callback));
            return;
        }
        if (callback != null) {
            lastKnownCallback = callback;
        }
        SystemUtils.ensureBackgroundThreadOrCrash("TellurideCourier::queryPage");
        boolean error = false;
        long a = System.currentTimeMillis();
        if (!Python.isStarted()) {
            Engine.startPython();
        }
        Python python = Engine.getPythonInstance();
        long b = System.currentTimeMillis();
        long pythonInstanceFetchTime = b - a;
        if (callback != null && callback.aborted()) {
            lastKnownCallback = null;
            LOG.info("TellurideCourier::queryPage aborted by TellurideCourierCallback (stage 1)");
            return;
        }
        LOG.info("TellurideCourier::queryPage - Got Python instance in " + pythonInstanceFetchTime + " ms");
        if (python == null) {
            LOG.error("TellurideCourier::queryPage could not get Python instance");
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        PyObject telluride_module = null;
        try {
            telluride_module = python.getModule("telluride");
        } catch (Throwable t) {
            LOG.error("TellurideCourier::queryPage failed to get telluride module", t);
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        if (telluride_module == null) {
            LOG.error("TellurideCourier::queryPage telluride module is null");
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        if (callback != null && callback.aborted()) {
            lastKnownCallback = null;
            LOG.info("TellurideCourier::queryPage aborted by TellurideCourierCallback (stage 2)");
            return;
        }
        PyObject query_video_result = null;
        try {
            query_video_result = telluride_module.callAttr("query_video", url);
        } catch (Throwable t) {
            LOG.error("TellurideCourier::queryPage failed to call query_video", t);
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        if (query_video_result == null && callback != null) {
            callback.onResults((List<TellurideSearchResult>) null, true);
            lastKnownCallback = null;
            return;
        }
        if (query_video_result == null) {
            return;
        }
        String json_query_video_result = query_video_result.toString();
        if (callback != null && callback.aborted()) {
            lastKnownCallback = null;
            LOG.info("TellurideCourier::queryPage aborted by TellurideCourierCallback (stage 3)");
            return;
        }

        if (gson == null) {
            gson = new GsonBuilder().create();
        }

        List<TellurideSearchResult> validResults = TellurideSearchPerformer.getValidResults(json_query_video_result, gson, null, -1, url);
        LOG.info("TellurideCourier::queryPage: TellurideSearchPerformer.getValidResults() -> " + validResults.size());

        if (callback != null && !callback.aborted()) {
            callback.onResults(validResults, error);
            lastKnownCallback = null;
        }
    }

    public static <T extends AbstractListAdapter<? super TellurideSearchResult>> void queryPlaylist(String url, TellurideCourierCallback<T> callback) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, () -> queryPlaylist(url, callback));
            return;
        }
        if (callback != null) {
            lastKnownCallback = callback;
        }
        SystemUtils.ensureBackgroundThreadOrCrash("TellurideCourier::queryPlaylist");
        if (!Python.isStarted()) {
            Engine.startPython();
        }
        Python python = Engine.getPythonInstance();
        if (python == null) {
            LOG.error("TellurideCourier::queryPlaylist could not get Python instance");
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        PyObject telluride_module = null;
        try {
            telluride_module = python.getModule("telluride");
        } catch (Throwable t) {
            LOG.error("TellurideCourier::queryPlaylist failed to get telluride module", t);
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        if (telluride_module == null) {
            LOG.error("TellurideCourier::queryPlaylist telluride module is null");
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        PyObject query_playlist_result = null;
        try {
            query_playlist_result = telluride_module.callAttr("query_playlist", url);
        } catch (Throwable t) {
            LOG.error("TellurideCourier::queryPlaylist failed to call query_playlist", t);
            if (callback != null) {
                callback.onResults((List<TellurideSearchResult>) null, true);
            }
            return;
        }
        String result = query_playlist_result.toString();
        if (callback != null && !callback.aborted()) {
            callback.onResults(result, true);
            lastKnownCallback = null;
        }
    }

    public interface OnYtDlpVersionCallback {
        void onVersion(String version);
    }

    public static void ytDlpVersion(@NonNull OnYtDlpVersionCallback callback) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> ytDlpVersion(callback));
            return;
        }
        SystemUtils.ensureBackgroundThreadOrCrash("TellurideCourier::ytDlpVersion");
        try {
            if (!Python.isStarted()) {
                Engine.startPython();
            }
        } catch (Throwable t) {
            LOG.error("TellurideCourier::ytDlpVersion failed to start Python", t);
            callback.onVersion("<unavailable>");
            return;
        }
        Python python = Engine.getPythonInstance();
        if (python == null) {
            LOG.error("TellurideCourier::ytDlpVersion could not get Python instance");
            callback.onVersion("<unavailable>");
            return;
        }
        PyObject telluride_module = null;
        try {
            telluride_module = python.getModule("telluride");
        } catch (Throwable t) {
            LOG.error("TellurideCourier::ytDlpVersion failed to get telluride module", t);
            callback.onVersion("<unavailable>");
            return;
        }
        if (telluride_module == null) {
            LOG.error("TellurideCourier::ytDlpVersion telluride module is null");
            callback.onVersion("<unavailable>");
            return;
        }
        PyObject ytDlpVersionString = null;
        try {
            ytDlpVersionString = telluride_module.callAttr("yt_dlp_version");
        } catch (Throwable t) {
            LOG.error("TellurideCourier::ytDlpVersion failed to call yt_dlp_version", t);
            callback.onVersion("<unavailable>");
            return;
        }
        if (ytDlpVersionString == null) {
            LOG.error("TellurideCourier::ytDlpVersion could got a null result when invoking telluride.yt_dlp_version() in Pythonland");
            callback.onVersion("<unavailable>");
            return;
        }
        callback.onVersion(ytDlpVersionString.toString());
    }


    public static class SearchPerformer<T extends AbstractListAdapter<? super TellurideSearchResult>> implements ISearchPerformer {
        private final long token;
        private final String pageUrl;
        private final TellurideCourierCallback<T> courierCallback;

        protected boolean stopped;
        private SearchListener listener;

        public SearchPerformer(long token, String pageUrl, T adapter) {
            this.token = token;
            this.pageUrl = pageUrl;
            this.courierCallback = new TellurideCourierCallback<>(this, pageUrl, adapter);
        }

        @Override
        public void perform() {
            SystemUtils.ensureBackgroundThreadOrCrash("TellurideCourier.SearchPerformer.perform");
            TellurideCourier.queryPage(pageUrl, courierCallback);
        }

        @Override
        public void crawl(CrawlableSearchResult sr) {
            // nothing to crawl
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
            courierCallback.abort();
            lastKnownCallback = null;
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

        @Override
        public boolean isCrawler() {
            return false;
        }
    }
}
