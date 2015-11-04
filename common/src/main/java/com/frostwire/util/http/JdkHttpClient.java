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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A pure java based HTTP client with resume capabilities.
 *
 * @author gubatron
 * @author aldenml
 */
public final class JdkHttpClient extends AbstractHttpClient {
    private static final Logger LOG = Logger.getLogger(JdkHttpClient.class);

    @Override
    public int head(String url, int connectTimeoutInMillis, Map<String, List<String>> outputHeaders) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(connectTimeoutInMillis);
        connection.setReadTimeout(connectTimeoutInMillis);
        connection.setRequestMethod("HEAD");
        copyMultiMap(connection.getHeaderFields(), outputHeaders);
        return connection.getResponseCode();
    }

    @Override
    public byte[] getBytes(String url, int timeout, String userAgent, String referrer, String cookies) {
        byte[] result = null;

        ByteArrayOutputStream baos = null;

        try {
            baos = new ByteArrayOutputStream();
            get(url, baos, timeout, userAgent, referrer, cookies, -1);

            result = baos.toByteArray();
        } catch (Throwable e) {
            LOG.error("Error getting bytes from http body response: " + e.getMessage(), e);
        } finally {
            closeQuietly(baos);
        }

        return result;
    }

    @Override
    public String get(String url, int timeout, String userAgent, String referrer, String cookie, Map<String, String> customHeaders) throws IOException {
        String result = null;

        ByteArrayOutputStream baos = null;

        try {
            baos = new ByteArrayOutputStream();
            get(url, baos, timeout, userAgent, referrer, cookie, -1, -1, customHeaders);

            result = new String(baos.toByteArray(), "UTF-8");
        } catch (java.net.SocketTimeoutException timeoutException) {
            throw timeoutException;
        } catch (IOException e) {
            throw e;
        } finally {
            closeQuietly(baos);
        }

        return result;
    }

    @Override
    public void save(String url, File file, boolean resume, int timeout, String userAgent, String referrer) throws IOException {
        FileOutputStream fos = null;
        long rangeStart;

        try {
            if (resume && file.exists()) {
                fos = new FileOutputStream(file, true);
                rangeStart = file.length();
            } else {
                fos = new FileOutputStream(file, false);
                rangeStart = -1;
            }

            get(url, fos, timeout, userAgent, null, referrer, rangeStart);
        } finally {
            closeQuietly(fos);
        }
    }

    /**
     * Post a form Content-type: application/x-www-form-urlencoded
     */
    @Override
    public String post(String url, int timeout, String userAgent, Map<String, String> formData) {
        String result = null;

        ByteArrayOutputStream baos = null;

        try {
            baos = new ByteArrayOutputStream();
            post(url, baos, timeout, userAgent, formData);
            result = new String(baos.toByteArray(), "UTF-8");
        } catch (Throwable e) {
            LOG.error("Error posting data via http: " + e.getMessage(), e);
        } finally {
            closeQuietly(baos);
        }

        return result;
    }

    @Override
    public String post(String url, int timeout, String userAgent, String content, String postContentType, boolean gzip) throws IOException {
        String result = null;
        canceled = false;
        final URL u = new URL(url);
        final HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setDoOutput(true);

        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setInstanceFollowRedirects(false);

        if (conn instanceof HttpsURLConnection) {
            setHostnameVerifier((HttpsURLConnection) conn);
        }

        byte[] data = content.getBytes("UTF-8");

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", postContentType);
        conn.setRequestProperty("charset", "utf-8");
        conn.setUseCaches(false);

        ByteArrayInputStream in = new ByteArrayInputStream(data);

        try {
            OutputStream out;
            if (gzip) {
                out = new GZIPOutputStream(conn.getOutputStream());
            } else {
                out = conn.getOutputStream();
            }

            byte[] b = new byte[4096];
            int n;
            while (!canceled && (n = in.read(b, 0, b.length)) != -1) {
                if (!canceled) {
                    out.write(b, 0, n);
                    out.flush();
                    onData(b, 0, n);
                }
            }

            closeQuietly(out);

            conn.connect();
            int httpResponseCode = getResponseCode(conn);

            if (httpResponseCode != HttpURLConnection.HTTP_OK && httpResponseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new ResponseCodeNotSupportedException(httpResponseCode);
            }

            if (canceled) {
                onCancel();
            } else {
                BufferedInputStream bis = new BufferedInputStream(conn.getInputStream(), 4096);
                ByteArrayBuffer baf = new ByteArrayBuffer(1024);
                byte[] buffer = new byte[64];
                int read;
                while (true) {
                    read = bis.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    baf.append(buffer, 0, read);
                }
                result = new String(baf.toByteArray());
                onComplete();
            }
        } catch (Exception e) {
            onError(e);
        } finally {
            closeQuietly(in);
            closeQuietly(conn);
        }
        return result;
    }

    private String buildRange(long rangeStart, long rangeLength) {
        String prefix = "bytes=" + rangeStart + "-";
        return prefix + ((rangeLength > -1) ? (rangeStart + rangeLength) : "");
    }

    private void checkRangeSupport(long rangeStart, URLConnection conn) throws HttpRangeOutOfBoundsException, RangeNotSupportedException {

        boolean hasContentRange = conn.getHeaderField("Content-Range") != null;
        boolean hasAcceptRanges = conn.getHeaderField("Accept-Ranges") != null && conn.getHeaderField("Accept-Ranges").equals("bytes");

        if (rangeStart > 0 && !hasContentRange && !hasAcceptRanges) {
            RangeNotSupportedException rangeNotSupportedException = new RangeNotSupportedException("Server does not support bytes range request");
            onError(rangeNotSupportedException);
            throw rangeNotSupportedException;
        }
    }

    private void get(String url, OutputStream out, int timeout, String userAgent, String referrer, String cookie, long rangeStart) throws IOException {
        get(url, out, timeout, userAgent, referrer, cookie, rangeStart, -1, null);
    }

    private void get(String url, OutputStream out, int timeout, String userAgent, String referrer, String cookie, long rangeStart, long rangeLength, final Map<String, String> customHeaders) throws IOException {
        canceled = false;
        final URL u = new URL(url);
        final URLConnection conn = u.openConnection();

        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);

        if (userAgent != null) {
            conn.setRequestProperty("User-Agent", userAgent);
        } else {
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
        }

        if (referrer != null) {
            conn.setRequestProperty("Referer", referrer);
        }

        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }

        if (conn instanceof HttpURLConnection) {
            ((HttpURLConnection) conn).setInstanceFollowRedirects(true);
        }

        if (conn instanceof HttpsURLConnection) {
            setHostnameVerifier((HttpsURLConnection) conn);
        }

        if (rangeStart > 0) {
            conn.setRequestProperty("Range", buildRange(rangeStart, rangeLength));
        }

        if (customHeaders != null && customHeaders.size() > 0) {
            //put down here so it can overwrite any of the previous headers.
            setCustomHeaders(conn, customHeaders);
        }

        InputStream in = conn.getInputStream();
        if ("gzip".equals(conn.getContentEncoding())) {
            in = new GZIPInputStream(in);
        }

        int httpResponseCode = getResponseCode(conn);

        if (httpResponseCode != HttpURLConnection.HTTP_OK &&
                httpResponseCode != HttpURLConnection.HTTP_PARTIAL &&
                httpResponseCode != HttpURLConnection.HTTP_MOVED_TEMP &&
                httpResponseCode != HttpURLConnection.HTTP_MOVED_PERM) {
            throw new ResponseCodeNotSupportedException(httpResponseCode);
        }
        onHeaders(conn.getHeaderFields());
        checkRangeSupport(rangeStart, conn);

        try {
            byte[] b = new byte[4096];
            int n;
            while (!canceled && (n = in.read(b, 0, b.length)) != -1) {
                if (!canceled) {
                    out.write(b, 0, n);
                    onData(b, 0, n);
                }
            }

            closeQuietly(out);

            if (canceled) {
                onCancel();
            } else {
                onComplete();
            }
        } catch (Exception e) {
            onError(e);
        } finally {
            closeQuietly(in);
            closeQuietly(conn);
        }
    }

    private void post(String url, OutputStream out, int timeout, String userAgent, Map<String, String> formData) throws IOException {
        canceled = false;
        final URL u = new URL(url);
        final HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setDoOutput(true);

        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setInstanceFollowRedirects(false);

        if (conn instanceof HttpsURLConnection) {
            setHostnameVerifier((HttpsURLConnection) conn);
        }

        byte[] data = getFormDataBytes(formData);

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setUseCaches(false);

        InputStream in = new ByteArrayInputStream(data);

        try {
            OutputStream postOut = conn.getOutputStream();

            byte[] b = new byte[4096];
            int n;
            while (!canceled && (n = in.read(b, 0, b.length)) != -1) {
                if (!canceled) {
                    postOut.write(b, 0, n);
                    postOut.flush();
                    onData(b, 0, n);
                }
            }

            closeQuietly(postOut);
            closeQuietly(in);

            conn.connect();

            in = conn.getInputStream();
            int httpResponseCode = getResponseCode(conn);

            if (httpResponseCode != HttpURLConnection.HTTP_OK &&
                    httpResponseCode != HttpURLConnection.HTTP_PARTIAL &&
                    httpResponseCode != HttpURLConnection.HTTP_MOVED_TEMP &&
                    httpResponseCode != HttpURLConnection.HTTP_MOVED_PERM) {
                throw new ResponseCodeNotSupportedException(httpResponseCode);
            }

            b = new byte[4096];
            while (!canceled && (n = in.read(b, 0, b.length)) != -1) {
                if (!canceled) {
                    out.write(b, 0, n);
                    onData(b, 0, n);
                }
            }

            closeQuietly(out);

            if (canceled) {
                onCancel();
            } else {
                onComplete();
            }
        } catch (Exception e) {
            onError(e);
        } finally {
            closeQuietly(in);
            closeQuietly(conn);
        }
    }

    private void setCustomHeaders(URLConnection conn, Map<String, String> headers) {
        for (Entry<String, String> e : headers.entrySet()) {
            conn.setRequestProperty(e.getKey(), e.getValue());
        }
    }

    private void setHostnameVerifier(HttpsURLConnection conn) {
        conn.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        setSSLSocketFactory(conn);
    }

    private void setSSLSocketFactory(HttpsURLConnection conn) {
        if (CUSTOM_SSL_SOCKET_FACTORY != null) {
            conn.setSSLSocketFactory(CUSTOM_SSL_SOCKET_FACTORY);
        }
    }

    private int getResponseCode(URLConnection conn) {
        try {
            return ((HttpURLConnection) conn).getResponseCode();
        } catch (Throwable e) {
            e.printStackTrace();
            LOG.error("can't get response code ", e);
            return -1;
        }
    }

    private void onHeaders(Map<String, List<String>> headerFields) {
        if (getListener() != null) {
            try {
                getListener().onHeaders(this, headerFields);
            } catch (Exception e) {
                LOG.warn(e.getMessage(), e);
            }
        }
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

    private void closeQuietly(URLConnection conn) {
        if (conn instanceof HttpURLConnection) {
            try {
                ((HttpURLConnection) conn).disconnect();
            } catch (Throwable e) {
                LOG.debug("Error closing http connection", e);
            }
        }
    }
}
