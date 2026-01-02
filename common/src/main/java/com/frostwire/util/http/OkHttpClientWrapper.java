/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.util.http;

import com.frostwire.util.*;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * COMMON
 * An OkHttpClient based AbstractHttpClient.
 *
 * @author gubatron
 * @author aldenml
 */
public class OkHttpClientWrapper extends AbstractHttpClient {
    public static final ConnectionPool CONNECTION_POOL = new ConnectionPool(8, 30, TimeUnit.SECONDS);
    private static final Logger LOG = Logger.getLogger(OkHttpClientWrapper.class);
    private final ThreadPool pool;
    private final OkHttpClient sharedClient;

    public OkHttpClientWrapper(final ThreadPool pool) {
        this.pool = pool;
        this.sharedClient = newOkHttpClient(pool).build();
    }

    public static void cancelAllRequests() {
        try {
            // We're in common so we'll just fire off this one thread here.
            // This occurs when we cancel searches and when we shutdown the app.
            new Thread("OkHttpClientWrapper::cancelAllRequests") {
                @Override
                public void run() {
                    try {
                        CONNECTION_POOL.evictAll();
                    } catch (Throwable t) {
                        LOG.error(t.getMessage(), t);
                    }
                }
            }.start();
        } catch (Throwable ignored) {
        }
    }

    public static OkHttpClient.Builder newOkHttpClient(ThreadPool pool) {
        OkHttpClient.Builder searchClient = new OkHttpClient.Builder();
        searchClient.dispatcher(new Dispatcher(pool));
        searchClient.connectionPool(CONNECTION_POOL);
        searchClient.followRedirects(true);
        searchClient.connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        searchClient = configNullSsl(searchClient);
        return searchClient;
    }

    public static OkHttpClient.Builder configNullSsl(OkHttpClient.Builder b) {
        b.followSslRedirects(true);
        b.hostnameVerifier(Ssl.fwHostnameVerifier());
        b.sslSocketFactory(Ssl.nullSocketFactory(), Ssl.nullTrustManager());
        ConnectionSpec spec0 = cipherSpec(ConnectionSpec.CLEARTEXT);
        ConnectionSpec spec1 = cipherSpec(ConnectionSpec.COMPATIBLE_TLS);
        ConnectionSpec spec2 = cipherSpec(ConnectionSpec.MODERN_TLS);
        ConnectionSpec spec3 = cipherSpec(ConnectionSpec.RESTRICTED_TLS);
        b.connectionSpecs(Arrays.asList(spec0, spec1, spec2, spec3));
        return b;
    }

    private static ConnectionSpec cipherSpec(ConnectionSpec spec) {
        ConnectionSpec.Builder b = new ConnectionSpec.Builder(spec);
        if (spec.isTls()) {
            b = b.allEnabledCipherSuites();
            b = b.allEnabledTlsVersions();
        }
        return b.build();
    }

    @Override
    public int head(String url, int connectTimeoutInMillis, Map<String, List<String>> outputHeaders) throws IOException {
        OkHttpClient client = sharedClient.newBuilder()
                .connectTimeout(connectTimeoutInMillis, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .build();
        Request req = new Request.Builder().
                url(url).
                header("User-Agent", DEFAULT_USER_AGENT).
                head().
                build();
        Response resp = client.newCall(req).execute();
        closeQuietly(resp.body());
        copyMultiMap(resp.headers().toMultimap(), outputHeaders);
        return resp.code();
    }

    @Override
    public byte[] getBytes(String url, int timeout, String userAgent, String referrer, String cookies) {
        byte[] result = null;
        OkHttpClient client = sharedClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
        final Request.Builder builder = prepareRequestBuilder(url, userAgent, referrer, cookies);
        ResponseBody responseBody = null;
        try {
            responseBody = getSyncResponse(client, builder).body();
            if (responseBody != null) {
                result = responseBody.bytes();
            }
        } catch (Throwable e) {
            LOG.error("Error getting bytes from http body response: " + e.getMessage());
        } finally {
            if (responseBody != null) {
                closeQuietly(responseBody);
            }
        }
        return result;
    }

    @Override
    public String get(String url, int timeoutMillis, String userAgent, String referrer, String cookie, Map<String, String> customHeaders) throws IOException {
        String result = null;
        OkHttpClient client = sharedClient.newBuilder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
        final Request.Builder builder = prepareRequestBuilder(url, userAgent, referrer, cookie);
        addCustomHeaders(customHeaders, builder);
        ResponseBody responseBody = null;
        try {
            responseBody = getSyncResponse(client, builder).body();
            if (responseBody != null) {
                result = responseBody.string();
            }
        } catch (IOException ioe) {
            //ioe.printStackTrace();
            throw ioe;
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        } finally {
            if (responseBody != null) {
                closeQuietly(responseBody);
            }
        }
        return result;
    }

    @Override
    public void save(String url, File file, boolean resume, int timeout, String userAgent, String referrer) throws IOException {
        FileOutputStream fos;
        BufferedOutputStream bos = null;
        long rangeStart;
        canceled = false;
        if (resume && file.exists()) {
            fos = new FileOutputStream(file, true);
            rangeStart = file.length();
        } else {
            fos = new FileOutputStream(file, false);
            rangeStart = -1;
        }
        bos = new BufferedOutputStream(fos, 32768); // 32 KiB buffer
        OkHttpClient client = sharedClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
        final Request.Builder builder = prepareRequestBuilder(url, userAgent, referrer, null);
        addRangeHeader(rangeStart, -1, builder);
        final Response response = getSyncResponse(client, builder);
        final Headers headers = response.headers();
        onHeaders(headers);
        final InputStream in = response.body().byteStream();
        byte[] b = new byte[32768]; // 32 KiB buffer
        int n;
        while (!canceled && (n = in.read(b, 0, b.length)) != -1) {
            if (!canceled) {
                bos.write(b, 0, n);
                onData(b, 0, n);
            }
        }
        closeQuietly(bos);
        closeQuietly(fos);
        closeQuietly(response.body());
        if (canceled) {
            onCancel();
        } else {
            onComplete();
        }
    }

    private void onHeaders(Headers headers) {
        if (getListener() != null) {
            try {
                getListener().onHeaders(this, headers.toMultimap());
            } catch (Exception e) {
                LOG.warn(e.getMessage(), e);
            }
        }
    }

    @Override
    public String post(String url, int timeout, String userAgent, Map<String, String> formData) throws IOException {
        return post(url, timeout, userAgent, "application/x-www-form-urlencoded; charset=utf-8", getFormDataBytes(formData), false);
    }

    @Override
    public String post(String url, int timeout, String userAgent, String content, String postContentType, boolean gzip) throws IOException {
        return post(url, timeout, userAgent, postContentType, content.getBytes(StandardCharsets.UTF_8), gzip);
    }

    private String post(String url, int timeout, String userAgent, String postContentType, byte[] postData, boolean gzip) throws IOException {
        canceled = false;
        OkHttpClient.Builder clientBuilder = sharedClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(false);
        if (gzip) {
            clientBuilder.addInterceptor(new GzipRequestInterceptor());
        }
        OkHttpClient client = clientBuilder.build();
        final Request.Builder builder = prepareRequestBuilder(url, userAgent, null, null);
        final RequestBody requestBody = RequestBody.create(postData, MediaType.parse(postContentType));
        builder.post(requestBody);
        return getPostSyncResponse(client, builder);
    }

    private String getPostSyncResponse(OkHttpClient client, Request.Builder builder) throws IOException {
        String result = null;
        final Response response = this.getSyncResponse(client, builder);
        try {
            int httpResponseCode = response.code();
            if ((httpResponseCode != HttpURLConnection.HTTP_OK) && (httpResponseCode != HttpURLConnection.HTTP_PARTIAL)) {
                throw new ResponseCodeNotSupportedException(httpResponseCode);
            }
            if (canceled) {
                onCancel();
            } else {
                result = response.body().string();
                onComplete();
            }
        } finally {
            closeQuietly(response.body());
        }
        return result;
    }

    private void addRangeHeader(long rangeStart, long rangeEnd, Request.Builder builderRef) {
        if (rangeStart < 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("bytes=");
        sb.append(rangeStart);
        sb.append('-');
        if (rangeEnd > 0 && rangeEnd > rangeStart) {
            sb.append(rangeEnd);
        }
        builderRef.addHeader("Range", sb.toString());
    }

    private Request.Builder prepareRequestBuilder(String url, String userAgent, String referrer, String cookie) {
        Request.Builder builder = new Request.Builder();
        builder.url(url);
        if (!StringUtils.isNullOrEmpty(userAgent)) {
            builder.header("User-Agent", userAgent);
        }
        if (!StringUtils.isNullOrEmpty(referrer)) {
            try {
                builder.header("Referer", referrer); // [sic - typo in HTTP protocol]
            } catch (IllegalArgumentException illegalEx) {
                LOG.info("Referer value: " + referrer);
                LOG.warn(illegalEx.getMessage(), illegalEx);
            }
        }
        if (!StringUtils.isNullOrEmpty(cookie)) {
            builder.header("Cookie", cookie);
        }
        return builder;
    }

    private void addCustomHeaders(Map<String, String> customHeaders, Request.Builder builder) {
        if (customHeaders != null && customHeaders.size() > 0) {
            try {
                for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                    builder.header(header.getKey(), header.getValue());
                }
            } catch (Throwable e) {
                LOG.warn(e.getMessage(), e);
            }
        }
    }

    private Response getSyncResponse(OkHttpClient client, Request.Builder builder) throws IOException {
        final Request request = builder.build();
        return client.newCall(request).execute();
    }

    /**
     * This interceptor compresses the HTTP request body. Many web servers can't handle this!
     */
    class GzipRequestInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            if (originalRequest.body() == null || originalRequest.header("Content-Encoding") != null) {
                return chain.proceed(originalRequest);
            }
            Request compressedRequest = originalRequest.newBuilder()
                    .header("Content-Encoding", "gzip")
                    .method(originalRequest.method(), forceContentLength(gzip(originalRequest.body())))
                    .build();
            return chain.proceed(compressedRequest);
        }

        /**
         * https://github.com/square/okhttp/issues/350
         */
        private RequestBody forceContentLength(final RequestBody requestBody) throws IOException {
            final Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);
            return new RequestBody() {
                @Override
                public MediaType contentType() {
                    return requestBody.contentType();
                }

                @Override
                public long contentLength() {
                    return buffer.size();
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    sink.write(buffer.snapshot());
                }
            };
        }

        private RequestBody gzip(final RequestBody body) {
            return new RequestBody() {
                @Override
                public MediaType contentType() {
                    return body.contentType();
                }

                @Override
                public long contentLength() {
                    return -1; // We don't know the compressed length in advance!
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    BufferedSink gzipSink = Okio.buffer(new GzipSink(sink));
                    body.writeTo(gzipSink);
                    gzipSink.close();
                }
            };
        }
    }
}
