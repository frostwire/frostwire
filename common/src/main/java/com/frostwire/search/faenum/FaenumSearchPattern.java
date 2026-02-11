/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.faenum;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SearchPattern for Faenum public domain image search.
 * Faenum provides AI-powered text-to-image search across thousands of public domain images.
 *
 * @author gubatron
 */
public class FaenumSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(FaenumSearchPattern.class);
    private static final String DOMAIN = "faenum.com";
    private static final int MAX_RESULTS = 50;

    @Override
    public String getSearchUrl(String encodedKeywords) {
        // Faenum API endpoint - will need to be verified/adjusted based on actual API
        // Common patterns: /api/search, /search/api, /api/v1/search
        return "https://www." + DOMAIN + "/api/search?q=" + encodedKeywords + "&limit=" + MAX_RESULTS;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            LOG.warn("Faenum: Empty response body");
            return results;
        }

        try {
            // Try to parse as JSON response
            FaenumResponse response = JsonUtils.toObject(responseBody, FaenumResponse.class);
            if (response == null || response.results == null) {
                LOG.warn("Faenum: Invalid response format");
                return results;
            }

            for (FaenumImage image : response.results) {
                try {
                    if (StringUtils.isNullOrEmpty(image.id) || StringUtils.isNullOrEmpty(image.imageUrl)) {
                        continue;
                    }

                    // Build display name from title or description
                    String displayName = !StringUtils.isNullOrEmpty(image.title) 
                        ? image.title 
                        : (!StringUtils.isNullOrEmpty(image.description) 
                            ? image.description 
                            : "Image " + image.id);

                    // Determine filename from URL or use fallback
                    String filename = extractFilenameFromUrl(image.imageUrl);
                    if (StringUtils.isNullOrEmpty(filename)) {
                        filename = image.id + ".jpg";
                    }

                    // Build detail URL
                    String detailsUrl = "https://www." + DOMAIN + "/image/" + image.id;
                    if (!StringUtils.isNullOrEmpty(image.detailsUrl)) {
                        detailsUrl = image.detailsUrl;
                    }

                    // Estimate size if available (default to unknown)
                    long size = image.fileSize > 0 ? image.fileSize : FileSearchResult.UNKNOWN_SIZE;

                    // All Faenum images are public domain
                    License license = Licenses.PUBLIC_DOMAIN_MARK;

                    CompositeFileSearchResult searchResult = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(filename)
                            .size(size)
                            .detailsUrl(detailsUrl)
                            .source("Faenum")
                            .creationTime(-1)  // Not provided by API
                            .license(license)
                            .thumbnailUrl(image.thumbnailUrl != null ? image.thumbnailUrl : image.imageUrl)
                            .build();

                    results.add(searchResult);
                } catch (Exception e) {
                    LOG.warn("Faenum: Error parsing image result: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Faenum: Error parsing response: " + e.getMessage(), e);
        }

        LOG.info("Faenum: Parsed " + results.size() + " results");
        return results;
    }

    /**
     * Extract filename from image URL.
     * Example: https://example.com/path/to/image.jpg -> image.jpg
     */
    private String extractFilenameFromUrl(String url) {
        if (StringUtils.isNullOrEmpty(url)) {
            return null;
        }
        try {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                String filename = url.substring(lastSlash + 1);
                // Remove query parameters if present
                int queryStart = filename.indexOf('?');
                if (queryStart > 0) {
                    filename = filename.substring(0, queryStart);
                }
                return filename;
            }
        } catch (Exception e) {
            LOG.warn("Faenum: Error extracting filename from URL: " + e.getMessage());
        }
        return null;
    }

    /**
     * Internal class representing Faenum API response format.
     * This structure may need to be adjusted based on actual API response.
     */
    private static class FaenumResponse {
        public List<FaenumImage> results;
        public int total;
        public int page;
    }

    /**
     * Internal class representing a Faenum image result.
     */
    private static class FaenumImage {
        public String id;
        public String title;
        public String description;
        public String imageUrl;
        public String thumbnailUrl;
        public String detailsUrl;
        public long fileSize;
        public int width;
        public int height;
        public String source;  // Original collection/museum source
        public String license;
    }
}
