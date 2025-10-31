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

package com.frostwire.search.internetarchive;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.search.SearchListener;
import com.frostwire.search.CrawlingStrategy;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.StreamableUtils;
import com.frostwire.search.internetarchive.InternetArchiveFile;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.UserAgentGenerator;
import com.frostwire.util.http.HttpClient;
import org.apache.commons.io.FilenameUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Crawling strategy for Internet Archive search results.
 * Fetches the JSON details page to extract file list.
 * Categorizes files into 3 types:
 * - Streamable (audio/video by extension)
 * - Torrent (.torrent files with seeds=3, totalSize)
 * - HTTP Download (other files)
 *
 * @author gubatron
 */
public class InternetArchiveCrawlingStrategy implements CrawlingStrategy {
    private static final Logger LOG = Logger.getLogger(InternetArchiveCrawlingStrategy.class);
    private static final String DEFAULT_USER_AGENT = UserAgentGenerator.getUserAgent();
    private static final String DOMAIN = "archive.org";
    private static final String DOWNLOAD_URL = "https://%s/download/%s/%s";

    private final HttpClient httpClient;
    private final int timeout;
    private final int maxCrawls;

    public InternetArchiveCrawlingStrategy() {
        this(HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH), 30000, 100);
    }

    public InternetArchiveCrawlingStrategy(HttpClient httpClient, int timeout, int maxCrawls) {
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.maxCrawls = maxCrawls;
    }

    @Override
    public void crawlResults(List<FileSearchResult> results, SearchListener listener, long token) {
        int crawlCount = 0;
        List<FileSearchResult> crawledResults = new ArrayList<>();

        for (FileSearchResult result : results) {
            if (crawlCount >= maxCrawls) {
                break;
            }

            try {
                String detailsUrl = result.getDetailsUrl();
                if (detailsUrl != null && !detailsUrl.isEmpty()) {
                    String detailsJson = httpClient.get(detailsUrl, timeout, DEFAULT_USER_AGENT, null, null, null);
                    if (detailsJson != null) {
                        List<FileSearchResult> crawledFiles = extractFilesFromDetails(result, detailsJson);
                        if (!crawledFiles.isEmpty()) {
                            crawledResults.addAll(crawledFiles);
                            crawlCount++;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error crawling Internet Archive details from " + result.getDetailsUrl() + ": " + e.getMessage());
            }
        }

        // Report crawled results to listener (preliminary results are now resolved)
        if (listener != null && !crawledResults.isEmpty()) {
            listener.onResults(token, (List) crawledResults);
        }
    }

    /**
     * Extracts files from the Internet Archive JSON details page.
     * Categorizes files into streamable, torrent, and HTTP download results.
     * Returns the list of complete FileSearchResult objects.
     */
    private List<FileSearchResult> extractFilesFromDetails(FileSearchResult preliminaryResult, String detailsJson) {
        List<FileSearchResult> results = new ArrayList<>();

        try {
            JsonElement element = JsonParser.parseString(detailsJson);
            JsonObject obj = element.getAsJsonObject();
            JsonObject files = obj.getAsJsonObject("files");

            if (files == null) {
                return results;
            }

            // Get identifier from preliminary result filename (we set it there in SearchPattern)
            String identifier = preliminaryResult.getFilename();

            // Extract all files and calculate total size
            List<InternetArchiveFile> fileList = new ArrayList<>();
            Iterator<Map.Entry<String, JsonElement>> it = files.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonElement> e = it.next();
                String name = e.getKey();
                String value = e.getValue().toString();
                InternetArchiveFile file = JsonUtils.toObject(value, InternetArchiveFile.class);

                // Filter: exclude metadata format files
                if (file != null && !(file.format != null && file.format.equalsIgnoreCase("metadata"))) {
                    // Clean name: remove leading /
                    file.filename = cleanName(name);
                    fileList.add(file);
                }
            }

            // Calculate total size for torrent results
            long totalSize = calcTotalSize(fileList);

            // Create results for each file, categorized by type
            for (InternetArchiveFile file : fileList) {
                FileSearchResult fileResult = createFileResult(preliminaryResult, identifier, file, totalSize);
                if (fileResult != null) {
                    results.add(fileResult);
                }
            }

            LOG.debug("InternetArchive: Crawled " + results.size() + " files from " + identifier);
        } catch (Exception e) {
            LOG.error("Error parsing Internet Archive details JSON: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Creates a FileSearchResult for a single file, categorized by type.
     */
    private FileSearchResult createFileResult(FileSearchResult preliminaryResult, String identifier,
                                              InternetArchiveFile file, long totalSize) {
        try {
            String filename = FilenameUtils.getName(file.filename);
            String displayName = FilenameUtils.getBaseName(filename) + " (" + preliminaryResult.getDisplayName() + ")";
            String downloadUrl = String.format(Locale.US, DOWNLOAD_URL, DOMAIN, identifier, UrlUtils.encode(file.filename));
            long fileSize = calcSize(file);

            // Determine result type based on file
            if (StreamableUtils.isStreamable(file.filename)) {
                // Streamable result (audio/video)
                return CompositeFileSearchResult.builder()
                        .displayName(displayName)
                        .filename(filename)
                        .size(fileSize)
                        .detailsUrl(preliminaryResult.getDetailsUrl())
                        .source(preliminaryResult.getSource())
                        .creationTime(preliminaryResult.getCreationTime())
                        .streaming(downloadUrl)  // Mark as streamable
                        .preliminary(false)  // Complete after crawling
                        .build();
            } else if (file.filename.endsWith(".torrent")) {
                // Torrent result - use totalSize (sum of all files), seeds=3 (hardcoded)
                return CompositeFileSearchResult.builder()
                        .displayName(displayName)
                        .filename(filename)
                        .size(totalSize)  // IMPORTANT: total size from all files
                        .detailsUrl(preliminaryResult.getDetailsUrl())
                        .source(preliminaryResult.getSource())
                        .creationTime(preliminaryResult.getCreationTime())
                        .torrent(downloadUrl, "", 3, downloadUrl)  // Seeds hardcoded to 3
                        .preliminary(false)  // Complete after crawling
                        .build();
            } else {
                // HTTP download result
                return CompositeFileSearchResult.builder()
                        .displayName(displayName)
                        .filename(filename)
                        .size(fileSize)
                        .detailsUrl(preliminaryResult.getDetailsUrl())
                        .source(preliminaryResult.getSource())
                        .creationTime(preliminaryResult.getCreationTime())
                        // No torrent, streaming, or crawlable metadata - just HTTP download
                        .preliminary(false)  // Complete after crawling
                        .build();
            }
        } catch (Exception e) {
            LOG.warn("Error creating Internet Archive file result: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clean filename: remove leading slash
     */
    private String cleanName(String name) {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name;
    }

    /**
     * Calculate total size of all files in the collection
     */
    private long calcTotalSize(List<InternetArchiveFile> files) {
        long size = 0;
        for (InternetArchiveFile f : files) {
            try {
                if (f.size != null && !f.size.isEmpty()) {
                    size += Long.parseLong(f.size);
                }
            } catch (Throwable e) {
                // ignore
            }
        }
        return size;
    }

    /**
     * Parse individual file size
     */
    private long calcSize(InternetArchiveFile file) {
        try {
            if (file.size != null && !file.size.isEmpty()) {
                return Long.parseLong(file.size);
            }
        } catch (Throwable e) {
            // ignore
        }
        return -1;
    }
}
