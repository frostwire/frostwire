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

package com.frostwire.search.internetarchive;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.search.internetarchive.InternetArchiveItem;
import com.frostwire.search.internetarchive.InternetArchiveResponse;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 pattern-based search for Internet Archive.
 * Returns preliminary results (no file details yet).
 * Crawling strategy fetches and categorizes files.
 *
 * @author gubatron
 */
public class InternetArchiveSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(InternetArchiveSearchPattern.class);
    private static final String DOMAIN = "archive.org";
    private static final int MAX_RESULTS = 12;

    @Override
    public String getSearchUrl(String encodedKeywords) {
        // Build advanced search URL with field list (same as legacy)
        return "https://" + DOMAIN +
                "/advancedsearch.php?q=" + encodedKeywords +
                "&fl[]=avg_rating&fl[]=call_number&fl[]=collection&fl[]=contributor" +
                "&fl[]=coverage&fl[]=creator&fl[]=date&fl[]=description&fl[]=downloads" +
                "&fl[]=foldoutcount&fl[]=format&fl[]=headerImage&fl[]=identifier" +
                "&fl[]=imagecount&fl[]=language&fl[]=licenseurl&fl[]=mediatype" +
                "&fl[]=month&fl[]=num_reviews&fl[]=oai_updatedate&fl[]=publicdate" +
                "&fl[]=publisher&fl[]=rights&fl[]=scanningcentre&fl[]=source" +
                "&fl[]=title&fl[]=type&fl[]=volume&fl[]=week&fl[]=year" +
                "&rows=50&page=1&output=json";
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            // Parse JSON response
            InternetArchiveResponse response = JsonUtils.toObject(responseBody, InternetArchiveResponse.class);
            if (response == null || response.response == null || response.response.docs == null) {
                return results;
            }

            int resultCount = 0;
            for (InternetArchiveItem item : response.response.docs) {
                if (resultCount >= MAX_RESULTS) {
                    break;
                }

                // Filter out items from "samples_only" collection
                if (item.collection != null && item.collection.contains("samples_only")) {
                    continue;
                }

                try {
                    String identifier = item.identifier;
                    if (StringUtils.isNullOrEmpty(identifier)) {
                        continue;
                    }

                    // Get title - handle array of title objects
                    String displayName = "";
                    if (item.title instanceof String) {
                        displayName = (String) item.title;
                    } else if (item.title instanceof java.util.List) {
                        java.util.List<?> titleList = (java.util.List<?>) item.title;
                        if (!titleList.isEmpty()) {
                            displayName = titleList.get(0).toString();
                        }
                    }

                    if (StringUtils.isNullOrEmpty(displayName)) {
                        displayName = identifier;
                    }

                    // Create details URL for this item (for crawling)
                    String detailsUrl = "https://" + DOMAIN + "/details/" + identifier + "?output=json";

                    long creationTime = System.currentTimeMillis();

                    // Create preliminary result (will be crawled later to get file details)
                    CompositeFileSearchResult result = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(identifier)  // Use identifier as filename
                            .size(-1)  // Unknown until crawled
                            .detailsUrl(detailsUrl)
                            .source("internetarchive")
                            .creationTime(creationTime)
                            .preliminary(true)  // IMPORTANT: This result needs crawling to get file details
                            .crawlable()  // Mark as crawlable
                            .build();

                    LOG.debug("InternetArchive: Created preliminary result - displayName: '" + displayName +
                             "', identifier: " + identifier + ", preliminary: " + result.isPreliminary());
                    results.add(result);
                    resultCount++;
                } catch (Exception e) {
                    LOG.warn("Error parsing InternetArchive item: " + e.getMessage(), e);
                }
            }
            LOG.debug("InternetArchive: Finished parsing - found " + resultCount + " results, returning " + results.size());
        } catch (Exception e) {
            LOG.error("Error parsing InternetArchive response: " + e.getMessage(), e);
        }

        return results;
    }
}
