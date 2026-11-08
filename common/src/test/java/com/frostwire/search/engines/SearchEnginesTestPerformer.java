/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.engines;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchListener;

/** A performer that does nothing, for checking which performer an engine hands out. */
final class SearchEnginesTestPerformer implements ISearchPerformer {
  private final long token;

  SearchEnginesTestPerformer(long token) {
    this.token = token;
  }

  @Override
  public long getToken() {
    return token;
  }

  @Override
  public void perform() {}

  @Override
  public void crawl(CrawlableSearchResult sr) {}

  @Override
  public void stop() {}

  @Override
  public boolean isStopped() {
    return false;
  }

  @Override
  public void setListener(SearchListener listener) {}

  @Override
  public SearchListener getListener() {
    return null;
  }

  @Override
  public boolean isDDOSProtectionActive() {
    return false;
  }

  @Override
  public boolean isCrawler() {
    return false;
  }
}
