/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

/**
 * NOTE: Doesn't have Keywords because some search performers depend on solely URIs.
 * See WebSearchPerformer.
 * @author gubatron
 * @author aldenml
 */
public interface SearchPerformer {
    long getToken();

    void perform();

    void crawl(CrawlableSearchResult sr);

    void stop();

    boolean isStopped();

    SearchListener getListener();

    void setListener(SearchListener listener);

    boolean isDDOSProtectionActive();

    boolean isCrawler();
}
