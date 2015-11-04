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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * A pure java based HTTP client with resume capabilities.
 *
 * @author gubatron
 * @author aldenml
 */
public interface HttpClient {
    void setListener(HttpClientListener listener);

    HttpClientListener getListener();

    void onCancel();

    void onData(byte[] b, int i, int n);

    void onError(Exception e);

    void onComplete();

    /**
     * Returns the HTTP response code
     */
    int head(String url, int connectTimeoutInMillis, Map<String, List<String>> outputHeaders) throws IOException;

    String get(String url) throws IOException;

    String get(String url, int timeout) throws IOException;

    String get(String url, int timeout, String userAgent) throws IOException;

    String get(String url, int timeout, String userAgent, String referrer, String cookie) throws IOException;

    String get(String url, int timeout, String userAgent, String referrer, String cookie, Map<String, String> customHeaders) throws IOException;

    byte[] getBytes(String url);

    byte[] getBytes(String url, int timeout);

    byte[] getBytes(String url, int timeout, String referrer);

    byte[] getBytes(String url, int timeout, String userAgent, String referrer);

    byte[] getBytes(String url, int timeout, String userAgent, String referrer, String cookies);

    void save(String url, File file) throws IOException;

    void save(String url, File file, boolean resume) throws IOException;

    void save(String url, File file, boolean resume, int timeout, String userAgent) throws IOException;

    void save(String url, File file, boolean resume, int timeout, String userAgent, String referrer) throws IOException;

    String post(String url, int timeout, String userAgent, Map<String, String> formData) throws IOException;

    String post(String url, int timeout, String userAgent, String content, boolean gzip) throws IOException;

    String post(String url, int timeout, String userAgent, String content, String postContentType, boolean gzip) throws IOException;

    void cancel();

    boolean isCanceled();

    interface HttpClientListener {

        void onError(HttpClient client, Throwable e);

        void onData(HttpClient client, byte[] buffer, int offset, int length);

        void onComplete(HttpClient client);

        void onCancel(HttpClient client);

        void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields);
    }

    abstract class HttpClientListenerAdapter implements HttpClientListener {

        public void onError(HttpClient client, Throwable e) {
        }

        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
        }

        public void onComplete(HttpClient client) {
        }

        public void onCancel(HttpClient client) {
        }

        public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
        }
    }

    class HttpRangeException extends IOException {

        private static final long serialVersionUID = 1891038288667531894L;

        HttpRangeException(String message) {
            super(message);
        }
    }

    final class RangeNotSupportedException extends HttpRangeException {

        private static final long serialVersionUID = -3356618211960630147L;

        RangeNotSupportedException(String message) {
            super(message);
        }
    }

    final class HttpRangeOutOfBoundsException extends HttpRangeException {

        private static final long serialVersionUID = -335661829606230147L;

        HttpRangeOutOfBoundsException(int rangeStart, long expectedFileSize) {
            super("HttpRange Out of Bounds error: start=" + rangeStart + " expected file size=" + expectedFileSize);
        }

    }

    final class ResponseCodeNotSupportedException extends IOException {
        private final int responseCode;

        ResponseCodeNotSupportedException(int code) {
            responseCode = code;
        }

        int getResponseCode() {
            return responseCode;
        }
    }
}