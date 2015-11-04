/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.

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

package com.frostwire.util.http;

import com.frostwire.logging.Logger;
import com.frostwire.util.UserAgentGenerator;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractHttpClient implements HttpClient {
    private static final Logger LOG = Logger.getLogger(AbstractHttpClient.class);
    protected static final int DEFAULT_TIMEOUT = 10000;
    protected static final String DEFAULT_USER_AGENT = UserAgentGenerator.getUserAgent();
    protected static final SSLSocketFactory CUSTOM_SSL_SOCKET_FACTORY = createCustomSSLSocketFactory();
    protected HttpClientListener listener;
    protected boolean canceled = false;

    @Override
    public void setListener(HttpClientListener listener) {
        this.listener = listener;
    }

    @Override
    public HttpClientListener getListener() {
        return listener;
    }

    @Override
    public void onCancel() {
        if (getListener() != null) {
            try {
                getListener().onCancel(this);
            } catch (Exception e) {
                LOG.warn(e.getMessage(), e);
            }
        }
    }

    @Override
    public void onData(byte[] b, int i, int n) {
        if (getListener() != null) {
            try {
                getListener().onData(this, b, 0, n);
            } catch (Exception e) {
                LOG.warn(e.getMessage(), e);
            }
        }
    }

    @Override
    public void onError(Exception e) {
        if (getListener() != null) {
            try {
                getListener().onError(this, e);
            } catch (Exception e2) {
                LOG.warn(e2.getMessage());
            }
        } else {
            e.printStackTrace();
        }
    }

    @Override
    public void onComplete() {
        if (getListener() != null) {
            try {
                getListener().onComplete(this);
            } catch (Exception e) {
                LOG.warn(e.getMessage(), e);
            }
        }
    }

    @Override
    abstract public int head(String url, int connectTimeoutInMillis, Map<String, List<String>> outputHeaders) throws IOException;

    @Override
    public String get(String url) throws IOException {
        return get(url, DEFAULT_TIMEOUT, DEFAULT_USER_AGENT);
    }

    @Override
    public String get(String url, int timeout) throws IOException {
        return get(url, timeout, DEFAULT_USER_AGENT);
    }

    @Override
    public String get(String url, int timeout, String userAgent) throws IOException {
        return get(url, timeout, userAgent, null, null);
    }

    @Override
    public String get(String url, int timeout, String userAgent, String referrer, String cookie) throws IOException {
        return get(url, timeout, userAgent, referrer, cookie, null);
    }

    @Override
    abstract public String get(String url, int timeout, String userAgent, String referrer, String cookie, Map<String, String> customHeaders) throws IOException;

    @Override
    public byte[] getBytes(String url) {
        return getBytes(url, DEFAULT_TIMEOUT);
    }

    @Override
    public byte[] getBytes(String url, int timeout) {
        return getBytes(url, timeout, null);
    }

    @Override
    public byte[] getBytes(String url, int timeout, String referrer) {
        return getBytes(url, timeout, DEFAULT_USER_AGENT, referrer);
    }

    @Override
    public byte[] getBytes(String url, int timeout, String userAgent, String referrer) {
        return getBytes(url, timeout, userAgent, referrer, null);
    }

    @Override
    abstract public byte[] getBytes(String url, int timeout, String userAgent, String referrer, String cookies);

    @Override
    public void save(String url, File file) throws IOException {
        save(url, file, false, DEFAULT_TIMEOUT, DEFAULT_USER_AGENT);
    }

    @Override
    public void save(String url, File file, boolean resume) throws IOException {
        save(url, file, resume, DEFAULT_TIMEOUT, DEFAULT_USER_AGENT);
    }

    @Override
    public void save(String url, File file, boolean resume, int timeout, String userAgent) throws IOException {
        save(url, file, resume, timeout, userAgent, null);
    }

    @Override
    abstract public void save(String url, File file, boolean resume, int timeout, String userAgent, String referrer) throws IOException;

    @Override
    abstract public String post(String url, int timeout, String userAgent, Map<String, String> formData) throws IOException;

    @Override
    public String post(String url, int timeout, String userAgent, String content, boolean gzip) throws IOException {
        return post(url, timeout, userAgent, content, "text/plain", gzip);
    }

    abstract public String post(String url, int timeout, String userAgent, String content, String postContentType, boolean gzip) throws IOException;

    @Override
    public void cancel() {
        canceled = true;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    protected byte[] getFormDataBytes(Map<String, String> formData) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if (formData != null && formData.size() > 0) {
            for (Map.Entry<String, String> kv : formData.entrySet()) {
                sb.append("&");
                sb.append(kv.getKey());
                sb.append("=");
                sb.append(kv.getValue());
            }
            sb.deleteCharAt(0);
        }
        return sb.toString().getBytes("UTF-8");
    }

    protected static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }

    protected static SSLSocketFactory createCustomSSLSocketFactory() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new AllX509TrustManager()}, new SecureRandom());
            SSLSocketFactory d = sc.getSocketFactory();
            return new WrapSSLSocketFactory(d);
        } catch (Throwable e) {
            LOG.error("Unable to create custom SSL socket factory", e);
        }

        return null;
    }

    protected static void copyMultiMap(Map<String, List<String>> origin, Map<String, List<String>> destination) {
        if (origin == null || destination == null){
            return;
        }

        for (String key : origin.keySet()) {
            destination.put(key, origin.get(key));
        }
    }
}