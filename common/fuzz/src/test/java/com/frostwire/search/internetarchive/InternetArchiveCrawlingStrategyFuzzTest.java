/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.internetarchive;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.SevereLogTrap;
import com.frostwire.util.http.HttpClient;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Fuzzes how the Internet Archive crawler turns an item's details JSON ({@code
 * /details/<identifier>?output=json}, fetched from archive.org for every search result) into file
 * results. The HTTP client is a {@link Proxy} that answers every request with the fuzzed body, so
 * nothing touches the network. {@code crawlResults} declares no exceptions; a throw, a hang, or a
 * null among the results it reports is a finding. So is an exception it catches around the whole
 * details JSON and logs at {@code SEVERE} ({@link SevereLogTrap}), unless the body simply is not
 * the JSON archive.org documents: that one malformed file entry made the item lose all its files.
 */
class InternetArchiveCrawlingStrategyFuzzTest {

  private static final SevereLogTrap SWALLOWED =
      SevereLogTrap.attachTo(InternetArchiveCrawlingStrategy.class);

  private static final FileSearchResult PRELIMINARY =
      CompositeFileSearchResult.builder()
          .displayName("This Week in Linux 217")
          .filename("zffikl7tt7lma8zwhojrb0p7o1diwovlozfryxrj")
          .size(-1)
          .detailsUrl(
              "https://archive.org/details/zffikl7tt7lma8zwhojrb0p7o1diwovlozfryxrj?output=json")
          .source("internetarchive")
          .creationTime(0)
          .preliminary(true)
          .crawlable()
          .build();

  @FuzzTest
  void crawlResults(FuzzedDataProvider data) {
    String detailsJson = data.consumeRemainingAsString();
    InternetArchiveCrawlingStrategy crawler =
        new InternetArchiveCrawlingStrategy(answering(detailsJson), 1000, 1);
    SWALLOWED.clear();
    crawler.crawlResults(List.of(PRELIMINARY), new NullCheckingListener(), 1);
    SWALLOWED.rethrowUnexplained(SevereLogTrap.NOT_THE_DOCUMENTED_JSON);
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
