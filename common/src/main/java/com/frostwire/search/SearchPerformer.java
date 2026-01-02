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

package com.frostwire.search;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.UserAgentGenerator;
import com.frostwire.util.http.HttpClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

/**
 * Concrete, flat search performer implementation that replaces deep inheritance hierarchies.
 * Combines all search performer functionality via composition of SearchPattern and CrawlingStrategy.
 *
 * Replaces:
 * - AbstractSearchPerformer
 * - WebSearchPerformer
 * - PagedWebSearchPerformer
 * - CrawlPagedWebSearchPerformer
 * - CrawlRegexSearchPerformer
 * - SimpleTorrentSearchPerformer
 * And all their subclasses.
 *
 * @author gubatron
 */
public class SearchPerformer implements ISearchPerformer {
    private static final Logger LOG = Logger.getLogger(SearchPerformer.class);
    private static final String DEFAULT_USER_AGENT = UserAgentGenerator.getUserAgent();

    private final long token;
    private final String keywords;
    private final String encodedKeywords;
    private final SearchPattern pattern;
    private final CrawlingStrategy crawlingStrategy;
    private final HttpClient httpClient;
    private final int timeout;

    protected boolean stopped;
    private SearchListener listener;

    /**
     * Constructs a SearchPerformer with the given components.
     *
     * @param token unique token for this search
     * @param keywords the search keywords
     * @param encodedKeywords URL-encoded keywords
     * @param pattern the search pattern strategy
     * @param crawlingStrategy optional crawling strategy (null if no crawling needed)
     * @param timeout HTTP request timeout in milliseconds
     */
    public SearchPerformer(long token,
                       String keywords,
                       String encodedKeywords,
                       SearchPattern pattern,
                       CrawlingStrategy crawlingStrategy,
                       int timeout) {
        this.token = token;
        this.keywords = keywords;
        this.encodedKeywords = encodedKeywords;
        this.pattern = pattern;
        this.crawlingStrategy = crawlingStrategy;
        this.timeout = timeout;
        this.httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
        this.stopped = false;
    }

    @Override
    public long getToken() {
        return token;
    }

    @Override
    public void perform() {
        try {
            if (stopped) {
                return;
            }

            // Get search URL/endpoint from pattern
            String searchUrl = pattern.getSearchUrl(encodedKeywords);
            String responseBody = null;

            // Check if pattern specifies a custom HTTP method
            SearchPattern.HttpMethod httpMethod = pattern.getHttpMethod();
            if (httpMethod == SearchPattern.HttpMethod.POST_JSON) {
                // Handle POST request with JSON body
                String jsonBody = pattern.getRequestBody(encodedKeywords);
                responseBody = postJson(searchUrl, jsonBody);
            } else if (httpMethod == SearchPattern.HttpMethod.POST) {
                // Handle POST request with form data
                String postData = pattern.getRequestBody(encodedKeywords);
                String contentType = pattern.getPostContentType();
                try {
                    responseBody = httpClient.post(searchUrl, timeout, DEFAULT_USER_AGENT, postData, contentType, false);
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                    responseBody = null;
                }
            } else {
                // Default: GET request
                Map<String, String> customHeaders = pattern.getCustomHeaders();
                responseBody = fetch(searchUrl, null, customHeaders);
            }

            if (stopped || responseBody == null) {
                return;
            }

            // Parse results
            List<FileSearchResult> results = pattern.parseResults(responseBody);

            if (stopped) {
                return;
            }

            // If we have a crawling strategy, use it to crawl the results
            if (crawlingStrategy != null && !results.isEmpty()) {
                crawlingStrategy.crawlResults(results, listener, token);
            } else {
                // Otherwise, just report the results directly
                onResults(results);
            }
        } catch (Exception e) {
            LOG.error("Search error: " + e.getMessage(), e);
            if (listener != null && !stopped) {
                listener.onError(token, new SearchError(-1, e.getMessage()));
            }
        }
    }

    @Override
    public void crawl(com.frostwire.search.CrawlableSearchResult sr) {
        // Not used in v2 architecture - crawling is handled by CrawlingStrategy
        LOG.warn("crawl() called on v2 SearchEngine - use CrawlingStrategy instead");
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            if (listener != null) {
                listener.onStopped(token);
            }
        } catch (Throwable e) {
            LOG.warn("Error stopping search: " + e.getMessage());
        }
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public SearchListener getListener() {
        return listener;
    }

    @Override
    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return false;
    }

    @Override
    public boolean isCrawler() {
        return crawlingStrategy != null;
    }

    protected void onResults(List<FileSearchResult> results) {
        if (stopped) {
            return;
        }
        try {
            if (results == null) {
                results = java.util.Collections.emptyList();
            }
            // Cast to SearchResult interface for backward compatibility
            listener.onResults(token, (List) results);
        } catch (Throwable e) {
            LOG.warn("Error sending results to listener: " + e.getMessage());
        }
    }

    /**
     * Performs an HTTP GET request.
     *
     * @param url the URL to fetch
     * @return the response body
     * @throws IOException if the request fails
     */
    public String fetch(String url) throws IOException {
        return fetch(url, null, null);
    }

    public String fetch(String url, String cookie, Map<String, String> customHeaders) throws IOException {
        try {
            return httpClient.get(url, timeout, DEFAULT_USER_AGENT, null, cookie, customHeaders);
        } catch (SocketTimeoutException e) {
            // Wrap timeout with performer context for better error identification
            throw wrapTimeoutException(url, e);
        }
    }

    /**
     * Performs an HTTP POST request with form data.
     *
     * @param url the URL to post to
     * @param formData the form data
     * @return the response body
     */
    public String post(String url, Map<String, String> formData) {
        try {
            return httpClient.post(url, timeout, DEFAULT_USER_AGENT, formData);
        } catch (SocketTimeoutException e) {
            // Wrap timeout with performer context
            LOG.error(wrapTimeoutException(url, e).getMessage(), wrapTimeoutException(url, e));
            return null;
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * Performs an HTTP POST request with JSON data.
     *
     * @param url the URL to post to
     * @param jsonContent the JSON content
     * @return the response body
     */
    public String postJson(String url, String jsonContent) {
        try {
            return httpClient.post(url, timeout, DEFAULT_USER_AGENT, jsonContent, "application/json", false);
        } catch (SocketTimeoutException e) {
            // Wrap timeout with performer context
            LOG.error(wrapTimeoutException(url, e).getMessage(), wrapTimeoutException(url, e));
            return null;
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * Performs an HTTP GET request for binary content.
     *
     * @param url the URL to fetch
     * @return the raw bytes
     */
    public byte[] fetchBytes(String url) {
        return fetchBytes(url, null, timeout);
    }

    protected byte[] fetchBytes(String url, String referrer, int timeout) {
        if (url.startsWith("htt")) { // http(s)
            return httpClient.getBytes(url, timeout, DEFAULT_USER_AGENT, referrer);
        } else {
            return null;
        }
    }

    // Getters for composed components (useful for subclasses)
    public String getKeywords() {
        return keywords;
    }

    public String getEncodedKeywords() {
        return encodedKeywords;
    }

    public SearchPattern getPattern() {
        return pattern;
    }

    public CrawlingStrategy getCrawlingStrategy() {
        return crawlingStrategy;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Wraps a SocketTimeoutException with performer/domain context for better error tracking.
     * Makes it easy to identify which search performer/domain caused the timeout.
     *
     * @param url the URL that timed out
     * @param cause the original SocketTimeoutException
     * @return a SearchTimeoutException with performer context
     */
    private SearchTimeoutException wrapTimeoutException(String url, SocketTimeoutException cause) {
        String performerName = pattern.getClass().getSimpleName();
        String domain = extractDomain(url);
        return new SearchTimeoutException(performerName, domain, url, timeout, cause);
    }

    /**
     * Extracts the domain/host from a URL for error context.
     * Uses UrlUtils.extractDomainName() which properly parses the URI.
     *
     * @param url the URL to extract domain from
     * @return the domain or "unknown" if extraction fails
     */
    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return "unknown";
        }
        String domain = UrlUtils.extractDomainName(url);
        return domain != null ? domain : "unknown";
    }
}
