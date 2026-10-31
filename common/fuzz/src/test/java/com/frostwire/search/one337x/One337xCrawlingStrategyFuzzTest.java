/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.one337x;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.util.http.HttpClient;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Fuzzes how the 1337x crawler turns a torrent's detail page (fetched from whichever mirror
 * answered, for every search result) into a complete torrent result. The HTTP client is a {@link
 * Proxy} that answers every request with the fuzzed page, so nothing touches the network. {@code
 * crawlResults} declares no exceptions; a throw, a page that takes seconds to parse, or a null
 * among the results it reports is a finding. Its per-page failures are logged without the
 * exception, so the harness cannot see the ones it swallows.
 */
class One337xCrawlingStrategyFuzzTest {

  private static final FileSearchResult PRELIMINARY =
      CompositeFileSearchResult.builder()
          .displayName("ubuntu-24.04.1-desktop-amd64.iso")
          .filename("ubuntu-24.04.1-desktop-amd64.iso.torrent")
          .size(-1)
          .detailsUrl("https://www.1377x.to/torrent/6187471/ubuntu-24-04-1-desktop-amd64-iso/")
          .source("1337x")
          .creationTime(0)
          .preliminary(false)
          .crawlable()
          .build();

  @FuzzTest
  void crawlResults(FuzzedDataProvider data) {
    String detailsHtml = data.consumeRemainingAsString();
    One337xCrawlingStrategy crawler = new One337xCrawlingStrategy(answering(detailsHtml), 1000, 1);
    crawler.crawlResults(List.of(PRELIMINARY), new NullCheckingListener(), 1);
  }

  private static HttpClient answering(String body) {
    return (HttpClient)
        Proxy.newProxyInstance(
            HttpClient.class.getClassLoader(),
            new Class<?>[] {HttpClient.class},
            (proxy, method, args) -> {
              if (method.getName().equals("get") && method.getReturnType() == String.class) {
                return body;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static final class NullCheckingListener implements SearchListener {
    @Override
    public void onResults(long token, List<? extends SearchResult> results) {
      for (SearchResult r : results) {
        if (r == null) {
          throw new IllegalStateException("crawlResults reported a null result");
        }
      }
    }

    @Override
    public void onStopped(long token) {}

    @Override
    public void onError(long token, SearchError error) {}
  }
}
