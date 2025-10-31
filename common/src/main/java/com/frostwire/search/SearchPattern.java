/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.search;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for defining how to search and parse results.
 * Implementations define the search URL, HTTP method, and how to parse results from HTML/JSON.
 *
 * @author gubatron
 */
public interface SearchPattern {
    /**
     * HTTP method enum for search requests.
     */
    enum HttpMethod {
        GET, POST, POST_JSON
    }

    /**
     * Gets the search URL for the given keywords.
     *
     * @param keywords the search keywords (already URL-encoded if needed by implementation)
     * @return the complete search URL or endpoint
     */
    String getSearchUrl(String keywords);

    /**
     * Gets the HTTP method to use for this search.
     * Default is GET for backward compatibility.
     *
     * @return the HTTP method to use
     */
    default HttpMethod getHttpMethod() {
        return HttpMethod.GET;
    }

    /**
     * Gets the request body for POST requests (not used for GET).
     * Only called if getHttpMethod() returns POST or POST_JSON.
     *
     * @param keywords the search keywords
     * @return the request body (JSON string for POST_JSON, form data for POST, or null)
     */
    default String getRequestBody(String keywords) {
        return null;
    }

    /**
     * Gets the content type for POST requests.
     * Only used if getHttpMethod() returns POST.
     *
     * @return the content type (e.g., "application/json", "application/x-www-form-urlencoded")
     */
    default String getPostContentType() {
        return "application/x-www-form-urlencoded";
    }

    /**
     * Parses search results from the response body.
     *
     * @param responseBody the HTTP response body (HTML or other format)
     * @return list of FileSearchResult parsed from the response
     */
    List<FileSearchResult> parseResults(String responseBody);

    /**
     * Gets custom HTTP headers for this search request.
     * Only called if the implementation needs custom headers (e.g., authentication, sessionId).
     *
     * @return a Map of custom headers, or null/empty map if no custom headers needed
     */
    default Map<String, String> getCustomHeaders() {
        return null;
    }
}
