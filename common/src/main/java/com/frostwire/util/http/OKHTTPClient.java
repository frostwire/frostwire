/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.

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

import com.frostwire.util.Logger;
import com.frostwire.util.Ssl;
import com.frostwire.util.StringUtils;
import com.frostwire.util.ThreadPool;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

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
 * An OkHttpClient based HTTP Client.
 *
 * @author gubatron
 * @author aldenml
 */
public class OKHTTPClient extends AbstractHttpClient {
    public static final ConnectionPool CONNECTION_POOL = new ConnectionPool(5, 30, TimeUnit.SECONDS);
    private static final Logger LOG = Logger.getLogger(OKHTTPClient.class);
    private final ThreadPool pool;

    public OKHTTPClient(final ThreadPool pool) {
        this.pool = pool;
    }

    public static OkHttpClient.Builder newOkHttpClient(ThreadPool pool) {
        OkHttpClient.Builder searchClient = new OkHttpClient.Builder();
        searchClient.dispatcher(new Dispatcher(pool));
        searchClient.connectionPool(CONNECTION_POOL);
        searchClient.followRedirects(true);
        searchClient.connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        searchClient = configNullSsl(searchClient);
        // Maybe we should use a custom connection pool here. Using default.
        //searchClient.setConnectionPool(?);
        return searchClient;
    }

    public static OkHttpClient.Builder configNullSsl(OkHttpClient.Builder b) {
        b.followSslRedirects(true);
        b.hostnameVerifier(Ssl.fwHostnameVerifier());
        b.sslSocketFactory(Ssl.nullSocketFactory(), Ssl.nullTrustManager());
        ConnectionSpec spec1 = cipherSpec(ConnectionSpec.CLEARTEXT);
        ConnectionSpec spec2 = cipherSpec(ConnectionSpec.COMPATIBLE_TLS);
        ConnectionSpec spec3 = cipherSpec(ConnectionSpec.MODERN_TLS);
        b.connectionSpecs(Arrays.asList(spec1, spec2, spec3));
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
        final OkHttpClient.Builder okHttpClient = newOkHttpClient();
        okHttpClient.connectTimeout(connectTimeoutInMillis, TimeUnit.MILLISECONDS);
        okHttpClient.followRedirects(false);
        Request req = new Request.Builder().
                url(url).
                header("User-Agent", DEFAULT_USER_AGENT).
                head().
                build();
         Response resp = okHttpClient.build().newCall(req).execute();
         closeQuietly(resp.body());
         copyMultiMap(resp.headers().toMultimap(), outputHeaders);
         return resp.code();
    }

    @Override
    public byte[] getBytes(String url, int timeout, String userAgent, String referrer, String cookies) {
        byte[] result = null;
        final OkHttpClient.Builder okHttpClient = newOkHttpClient();
        final Request.Builder builder = prepareRequestBuilder(okHttpClient, url, timeout, userAgent, referrer, cookies);
        ResponseBody responseBody = null;
        try {
            responseBody = getSyncResponse(okHttpClient, builder).body();
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
        final OkHttpClient.Builder okHttpClient = newOkHttpClient();
        final Request.Builder builder = prepareRequestBuilder(okHttpClient, url, timeoutMillis, userAgent, referrer, cookie);
        addCustomHeaders(customHeaders, builder);
        ResponseBody responseBody = null;
        try {
            responseBody = getSyncResponse(okHttpClient, builder).body();
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
        long rangeStart;
        canceled = false;
        if (resume && file.exists()) {
            fos = new FileOutputStream(file, true);
            rangeStart = file.length();
        } else {
            fos = new FileOutputStream(file, false);
            rangeStart = -1;
        }
        final OkHttpClient.Builder okHttpClient = newOkHttpClient();
        final Request.Builder builder = prepareRequestBuilder(okHttpClient, url, timeout, userAgent, referrer, null);
        addRangeHeader(rangeStart, -1, builder);
        final Response response = getSyncResponse(okHttpClient, builder);
        final Headers headers = response.headers();
        onHeaders(headers);
        final InputStream in = response.body().byteStream();
        byte[] b = new byte[4096];
        int n;
        while (!canceled && (n = in.read(b, 0, b.length)) != -1) {
            if (!canceled) {
                fos.write(b, 0, n);
                onData(b, 0, n);
            }
        }
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
        final OkHttpClient.Builder okHttpClient = newOkHttpClient();
        final Request.Builder builder = prepareRequestBuilder(okHttpClient, url, timeout, userAgent, null, null);
        final RequestBody requestBody = RequestBody.create(postData, MediaType.parse(postContentType));
        prepareOkHttpClientForPost(okHttpClient, gzip);
        builder.post(requestBody);
        return getPostSyncResponse(builder);
    }

    private String getPostSyncResponse(Request.Builder builder) throws IOException {
        String result = null;
        final OkHttpClient.Builder okHttpClient = newOkHttpClient();
        final Response response = this.getSyncResponse(okHttpClient, builder);
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

    private void prepareOkHttpClientForPost(OkHttpClient.Builder okHttpClient, boolean gzip) {
        okHttpClient.followRedirects(false);
        if (gzip) {
            if (okHttpClient.interceptors().size() > 0) {
                okHttpClient.interceptors().remove(0);
                okHttpClient.interceptors().add(0, new GzipRequestInterceptor());
            }
        }
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

    private Request.Builder prepareRequestBuilder(OkHttpClient.Builder okHttpClient, String url, int timeout, String userAgent, String referrer, String cookie) {
        okHttpClient.connectTimeout(timeout, TimeUnit.MILLISECONDS);
        okHttpClient.readTimeout(timeout, TimeUnit.MILLISECONDS);
        okHttpClient.writeTimeout(timeout, TimeUnit.MILLISECONDS);
        okHttpClient.interceptors().clear();
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

    private Response getSyncResponse(OkHttpClient.Builder okHttpClient, Request.Builder builder) throws IOException {
        final Request request = builder.build();
        return okHttpClient.build().newCall(request).execute();
    }

    private OkHttpClient.Builder newOkHttpClient() {
        return newOkHttpClient(pool);
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
