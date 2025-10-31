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

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Wraps SocketTimeoutException with context about which search performer caused it.
 * Provides easy identification of the domain/performer that timed out.
 *
 * Example error message:
 * "Search timeout (30000ms) from performer: YTSearchPattern (domain: youtube.com)
 *  URL: https://www.youtube.com/results?app=desktop&search_query=..."
 *
 * @author gubatron
 */
public class SearchTimeoutException extends IOException {
    private final String performerName;
    private final String domain;
    private final String url;
    private final int timeoutMs;

    /**
     * Creates a SearchTimeoutException wrapping a SocketTimeoutException with performer context.
     *
     * @param performerName name of the search performer/pattern (e.g., "BTDiggSearchPattern", "YTSearchPattern")
     * @param domain the domain being searched (e.g., "youtube.com", "pirate-bay.info")
     * @param url the URL that timed out
     * @param timeoutMs the timeout duration in milliseconds
     * @param cause the original SocketTimeoutException
     */
    public SearchTimeoutException(String performerName, String domain, String url, int timeoutMs, SocketTimeoutException cause) {
        super(formatMessage(performerName, domain, url, timeoutMs), cause);
        this.performerName = performerName;
        this.domain = domain;
        this.url = url;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Creates a SearchTimeoutException with minimal context.
     *
     * @param performerName name of the search performer/pattern
     * @param cause the original SocketTimeoutException
     */
    public SearchTimeoutException(String performerName, SocketTimeoutException cause) {
        this(performerName, "unknown", "unknown", 0, cause);
    }

    private static String formatMessage(String performerName, String domain, String url, int timeoutMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Search timeout");
        if (timeoutMs > 0) {
            sb.append(" (").append(timeoutMs).append("ms)");
        }
        sb.append(" from performer: ").append(performerName);
        if (domain != null && !domain.equals("unknown")) {
            sb.append(" (domain: ").append(domain).append(")");
        }
        if (url != null && !url.equals("unknown")) {
            sb.append("\n  URL: ").append(truncateUrl(url, 100));
        }
        return sb.toString();
    }

    private static String truncateUrl(String url, int maxLength) {
        if (url.length() <= maxLength) {
            return url;
        }
        return url.substring(0, maxLength) + "...";
    }

    /**
     * Checks if a Throwable is a search timeout (original or wrapped).
     *
     * @param throwable the exception to check
     * @return true if it's a SocketTimeoutException or SearchTimeoutException
     */
    public static boolean isSearchTimeout(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof SocketTimeoutException) {
            return true;
        }
        if (throwable instanceof SearchTimeoutException) {
            return true;
        }
        // Check cause chain
        return isSearchTimeout(throwable.getCause());
    }

    public String getPerformerName() {
        return performerName;
    }

    public String getDomain() {
        return domain;
    }

    public String getUrl() {
        return url;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }
}
