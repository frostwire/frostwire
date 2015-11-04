/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014,, FrostWire(R). All rights reserved.
 
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

package com.frostwire.uxstats;

import java.util.concurrent.ExecutorService;

import com.frostwire.logging.Logger;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class UXStats {
    private static final boolean IS_TESTING = false;
    
    private static final Logger LOG = Logger.getLogger(UXStats.class);

    private static final int HTTP_TIMEOUT = 4000;

    private final HttpClient httpClient;

    private ExecutorService executor;
    private UXStatsConf conf;
    private UXData data;

    private static final UXStats instance = new UXStats();

    public static UXStats instance() {
        return instance;
    }

    private UXStats() {
        this.httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
        this.executor = null;
        this.conf = null;
        this.data = null;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public UXStatsConf getContext() {
        return conf;
    }

    public void setContext(UXStatsConf conf) {
        this.conf = conf;

        if (conf != null) {
            this.data = newData();
        } else {
            this.data = null;
        }
    }

    /**
     * Important: This method is not thread-safe. That means it's only
     * meant to be used on the UI thread.
     * 
     * @param action
     */
    public void log(int action) {
        try {
            if (conf != null && data != null) {
                //System.out.println(UXAction.getActionName(action));

                if (data.actions.size() < conf.getMaxEntries()) {
                    data.actions.add(new UXAction(action, System.currentTimeMillis()));
                }

                if (isReadyToSend()) {
                    sendData();
                }
            }
        } catch (Throwable e) {
            // ignore, not important
        }
    }

    public void flush() {
        try {
            if (conf != null && data != null) {
                sendData();
            }
        } catch (Throwable e) {
            // ignore, not important
        }
    }

    private boolean isReadyToSend() {
        return data.actions.size() >= conf.getMinEntries() && (System.currentTimeMillis() - data.time > conf.getPeriod() * 1000);
    }

    private void sendData() {
        SendDataRunnable r = new SendDataRunnable(data);

        this.data = newData();

        if (executor != null) { // remember, not thread safe
            executor.submit(r);
        } else {
            new Thread(r, "UXStats-sendData").start();
        }
    }
    
    

    private UXData newData() {
        return new UXData(conf.getGuid(), conf.getOS(), conf.getFwversion(), conf.getFwbuild());
    }

    private final class SendDataRunnable implements Runnable {

        private final UXData data;

        public SendDataRunnable(UXData data) {
            this.data = data;
        }

        @Override
        public void run() {
            try {
                String json = JsonUtils.toJson(data);
                String postURL = conf.getUrl();
                
                if (IS_TESTING) {
                    postURL += "?test=1";
                }
                
                httpClient.post(postURL, HTTP_TIMEOUT, "FrostWire/UXStats", json, true);
            } catch (Throwable e) {
                LOG.error("Unable to send ux stats", e);
            }
        }
    }
}
