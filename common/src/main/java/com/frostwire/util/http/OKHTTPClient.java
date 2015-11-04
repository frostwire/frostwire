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
import com.frostwire.util.StringUtils;
import com.frostwire.util.ThreadPool;
import com.squareup.okhttp.*;
import okio.Buffer;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** An OkHttpClient based HTTP Client.
  *
  * @author gubatron
  * @author aldenml
*/
public class OKHTTPClient extends AbstractHttpClient {
    private static final Logger LOG = Logger.getLogger(OKHTTPClient.class);
    private final ThreadPool pool;

    public OKHTTPClient(final ThreadPool pool) {
        this.pool = pool;
    }

    @Override
    public int head(String url, int connectTimeoutInMillis, Map<String, List<String>> outputHeaders) throws IOException {
        final OkHttpClient okHttpClient = newOkHttpClient();
        okHttpClient.setConnectTimeout(connectTimeoutInMillis, TimeUnit.MILLISECONDS);
        okHttpClient.setFollowRedirects(false);
        Request req = new Request.Builder().
                url(url).
                header("User-Agent", DEFAULT_USER_AGENT).
                head().
                build();
        Response resp = okHttpClient.newCall(req).execute();
        copyMultiMap(resp.headers().toMultimap(), outputHeaders);
        return resp.code();
    }

    @Override
    public byte[] getBytes(String url, int timeout, String userAgent, String referrer, String cookies) {
        byte[] result = null;
        final OkHttpClient okHttpClient = newOkHttpClient();
        final Request.Builder builder = prepareRequestBuilder(okHttpClient, url, timeout, userAgent, referrer, cookies);
        try {
            result = getSyncResponse(okHttpClient, builder).body().bytes();
        } catch (Throwable e) {
            LOG.error("Error getting bytes from http body response: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public String get(String url, int timeout, String userAgent, String referrer, String cookie, Map<String, String> customHeaders) throws IOException {
        String result = null;
        final OkHttpClient okHttpClient = newOkHttpClient();
        final Request.Builder builder = prepareRequestBuilder(okHttpClient, url, timeout, userAgent, referrer, cookie);
        addCustomHeaders(customHeaders, builder);
        try {
            result = getSyncResponse(okHttpClient, builder).body().string();
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
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

        final OkHttpClient okHttpClient = newOkHttpClient();
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
        return post(url, timeout, userAgent, postContentType, content.getBytes("UTF-8"), gzip);
    }

    private String post(String url, int timeout, String userAgent, String postContentType, byte[] postData, boolean gzip) throws IOException {
        canceled = false;
        final OkHttpClient okHttpClient = newOkHttpClient();
        final Request.Builder builder = prepareRequestBuilder(okHttpClient, url, timeout, userAgent, null, null);
        final RequestBody requestBody = RequestBody.create(MediaType.parse(postContentType), postData);
        prepareOkHttpClientForPost(okHttpClient, gzip);
        builder.post(requestBody);
        return getPostSyncResponse(builder);
    }

    private String getPostSyncResponse(Request.Builder builder) throws IOException {
        String result = null;
        final OkHttpClient okHttpClient = newOkHttpClient();
        final Response response = this.getSyncResponse(okHttpClient, builder);
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
        return result;
    }

    private void prepareOkHttpClientForPost(OkHttpClient okHttpClient, boolean gzip) {
        okHttpClient.setFollowRedirects(false);
        okHttpClient.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        okHttpClient.setSslSocketFactory(CUSTOM_SSL_SOCKET_FACTORY);
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
        sb.append(String.valueOf(rangeStart));
        sb.append('-');
        if (rangeEnd > 0 && rangeEnd > rangeStart) {
            sb.append(String.valueOf(rangeEnd));
        }
        builderRef.addHeader("Range", sb.toString());
    }

    private Request.Builder prepareRequestBuilder(OkHttpClient okHttpClient, String url, int timeout, String userAgent, String referrer, String cookie) {
        okHttpClient.setConnectTimeout(timeout, TimeUnit.MILLISECONDS);
        okHttpClient.setReadTimeout(timeout, TimeUnit.MILLISECONDS);
        okHttpClient.setWriteTimeout(timeout, TimeUnit.MILLISECONDS);
        okHttpClient.interceptors().clear();
        Request.Builder builder = new Request.Builder();
        builder.url(url);
        if (!StringUtils.isNullOrEmpty(userAgent)) {
            builder.header("User-Agent", userAgent);
        }
        if (!StringUtils.isNullOrEmpty(referrer)) {
            builder.header("Referer", referrer); // [sic - typo in HTTP protocol]
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

    private Response getSyncResponse(OkHttpClient okHttpClient, Request.Builder builder) throws IOException {
        final Request request = builder.build();
        return okHttpClient.newCall(request).execute();
    }

    private OkHttpClient newOkHttpClient() {
        return newOkHttpClient(pool);
    }

    public static OkHttpClient newOkHttpClient(ThreadPool pool) {
        OkHttpClient searchClient = new OkHttpClient();
        searchClient.setDispatcher(new Dispatcher(pool));
        searchClient.setFollowRedirects(true);
        searchClient.setFollowSslRedirects(true);
        searchClient.setConnectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        // Maybe we should use a custom connection pool here. Using default.
        //searchClient.setConnectionPool(?);
        return searchClient;
    }

    /** This interceptor compresses the HTTP request body. Many web servers can't handle this! */
    class GzipRequestInterceptor implements Interceptor {
        @Override public Response intercept(Chain chain) throws IOException {
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

        /** https://github.com/square/okhttp/issues/350 */
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