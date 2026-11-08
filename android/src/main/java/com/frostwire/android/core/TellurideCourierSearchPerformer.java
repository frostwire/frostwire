/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchListener;
import com.frostwire.util.Logger;

/**
 * Asks Telluride, through the embedded Python runtime, for the media behind a page URL, and reports
 * what it finds to its search listener like any search engine (frostwire/frostwire#1002). Android's
 * {@code TellurideSearchFactory} makes these; desktop's runs the Telluride launcher instead.
 */
public final class TellurideCourierSearchPerformer implements ISearchPerformer {
  private static final Logger LOG = Logger.getLogger(TellurideCourierSearchPerformer.class);

  private final long token;
  private final String pageUrl;
  private final boolean playlistMode;
  private final TellurideCourierCallback<?> callback;
  private volatile boolean stopped;
  private volatile SearchListener listener;

  public TellurideCourierSearchPerformer(long token, String pageUrl, boolean playlistMode) {
    this.token = token;
    this.pageUrl = pageUrl;
    this.playlistMode = playlistMode;
    this.callback = new TellurideCourierCallback<>(this, pageUrl);
  }

  @Override
  public void perform() {
    SystemUtils.ensureBackgroundThreadOrCrash("TellurideCourierSearchPerformer.perform");
    if (playlistMode) {
      TellurideCourier.queryPlaylist(pageUrl, callback);
    } else {
      TellurideCourier.queryPage(pageUrl, callback);
    }
  }

  @Override
  public void crawl(CrawlableSearchResult sr) {
    // nothing to crawl
  }

  @Override
  public long getToken() {
    return token;
  }

  @Override
  public void stop() {
    stopped = true;
    callback.abort();
    SearchListener l = listener;
    if (l != null) {
      try {
        l.onStopped(token);
      } catch (Throwable e) {
        LOG.warn("Error sending stopped signal to listener: " + e.getMessage());
      }
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
    return false;
  }
}
