/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.http;

import java.util.Collections;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Request {

    private final Method method;
    private final String url;
    private final Map<String, String> headers;
    private final String mime;
    private final String body;

    public Request(Method method, String url, Map<String, String> headers, String mime, String body) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.mime = mime;
        this.body = body;
    }

    public Request(Method method, String url, Map<String, String> headers) {
        this(method, url, headers, null, null);
    }

    public Request(Method method, String url) {
        this(method, url, Collections.<String, String>emptyMap());
    }

    public Request(String url) {
        this(Method.GET, url);
    }

    public Method method() {
        return method;
    }

    public String url() {
        return url;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String mime() {
        return mime;
    }

    public String body() {
        return body;
    }

    public enum Method {
        GET("GET"),
        HEAD("HEAD"),
        POST("POST");

        private final String str;

        Method(String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }
    }
}
