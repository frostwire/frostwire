/*
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.nyaa;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.http.HttpClient;

import java.util.List;

public class NyaaSearchPerformer extends TorrentSearchPerformer {
    private final int MAX_RESULTS = 75;

    public NyaaSearchPerformer(String domainName, long token, String keywords, int timeout) {
       super(domainName, token, keywords, timeout, 1, 1);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/?f=0&c=0_0&q=" + encodedKeywords;
    }

    @Override
    protected List<? extends NyaaSearchResult> searchPage(String page) {
        return null;
    }

    private static class NyaaSearchPerformerTest {
        public static void main(String[] args) {
            String TEST_SEARCH_TERM = UrlUtils.encode("dragon ball");
            HttpClient httpClient = HttpClientFactory.newInstance();
            String fileStr;

            NyaaSearchPerformer nyaa = new NyaaSearchPerformer("nyaa.si",1,"dragon ball",5000);
            nyaa.setListener(new SearchListener() {
                @Override
                public void onResults(long token, List<? extends SearchResult> results) {
                    
                }

                @Override
                public void onError(long token, SearchError error) {

                }

                @Override
                public void onStopped(long token) {

                }
            });

            try {
                fileStr = httpClient.get("https://nyaa.si/?f=0&c=0_0&q=" + TEST_SEARCH_TERM);
            } catch (Throwable t) {
                t.printStackTrace();
                System.out.println("Aborting test.");
                return;
            }
        }
    }
}
