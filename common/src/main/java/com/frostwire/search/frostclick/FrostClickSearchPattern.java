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

package com.frostwire.search.frostclick;

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 pattern-based search for FrostClick affiliate search engine.
 * FrostClick is an affiliate engine that returns no results yet - we just hit the endpoint
 * with custom headers and check for successful response format.
 *
 * @author gubatron
 */
public class FrostClickSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(FrostClickSearchPattern.class);
    private static final String DOMAIN = "api.frostclick.com";

    private final Map<String, String> customHeaders;

    /**
     * Constructs FrostClickSearchPattern with authentication headers.
     *
     * @param userAgentString the User-Agent string for the API (includes version/build info)
     * @param sessionId the sessionId (UUID) for API authentication
     * @param baseHeaders base headers map (e.g., OS, FWversion, FWbuild from UserAgent)
     */
    public FrostClickSearchPattern(String userAgentString, String sessionId, Map<String, String> baseHeaders) {
        this.customHeaders = buildCustomHeaders(userAgentString, sessionId, baseHeaders);
    }

    private Map<String, String> buildCustomHeaders(String userAgentString, String sessionId, Map<String, String> baseHeaders) {
        Map<String, String> headers = new HashMap<>();

        // Start with base headers if provided
        if (baseHeaders != null) {
            headers.putAll(baseHeaders);
        }

        // Add required authentication headers
        headers.put("User-Agent", userAgentString);
        headers.put("sessionId", sessionId);

        return headers;
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/q?page=0&q=" + encodedKeywords;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            LOG.warn("FrostClick: Received empty response body");
            return results;
        }

        try {
            // FrostClick returns no results yet - we're just checking the endpoint works
            // Check for successful response format: "errors:[]"
            if (responseBody.contains("errors:[]")) {
                LOG.debug("FrostClick: Response format OK (errors:[])");
            } else {
                LOG.warn("FrostClick: Unexpected response format, missing 'errors:[]'");
            }
        } catch (Exception e) {
            LOG.error("FrostClick: Error parsing response: " + e.getMessage(), e);
        }

        // Return empty results - FrostClick doesn't provide search results yet
        return results;
    }

    @Override
    public HttpMethod getHttpMethod() {
        // Default GET method is fine for FrostClick
        return HttpMethod.GET;
    }

    /**
     * Get custom headers for FrostClick API authentication.
     * Includes User-Agent with version/build info and sessionId (UUID).
     *
     * @return Map of custom headers
     */
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }
}
