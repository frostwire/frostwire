/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.engines;

import com.frostwire.search.CrawlingStrategy;
import com.frostwire.search.SearchPattern;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.bitsearch.BitsearchSearchPattern;
import com.frostwire.search.frostclick.FrostClickSearchPattern;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.internetarchive.InternetArchiveCrawlingStrategy;
import com.frostwire.search.internetarchive.InternetArchiveSearchPattern;
import com.frostwire.search.knaben.KnabenSearchPattern;
import com.frostwire.search.magnetdl.MagnetDLSearchPattern;
import com.frostwire.search.nyaa.NyaaSearchPattern;
import com.frostwire.search.one337x.One337xCrawlingStrategy;
import com.frostwire.search.one337x.One337xSearchPattern;
import com.frostwire.search.relay.DistributedSearchPerformer;
import com.frostwire.search.relay.LocalSharedTorrentSearchPerformer;
import com.frostwire.search.relay.RelaySearchWiring;
import com.frostwire.search.soundcloud.SoundcloudSearchPattern;
import com.frostwire.search.torrentscsv.TorrentsCSVSearchPattern;
import com.frostwire.search.torrentz2.Torrentz2SearchPattern;
import com.frostwire.search.tpb.TPBMirrors;
import com.frostwire.search.tpb.TPBSearchPattern;
import com.frostwire.search.yt.YTSearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.http.HttpClient;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Every search engine both clients offer, defined once (frostwire/frostwire#1002). A client builds
 * one registry from its {@link SearchEngineAdapter} and keeps it for the life of the app.
 *
 * <p>Thread-safe: the engine list is fixed at construction; the TPB mirror and the relay wiring are
 * set from background threads and read through volatile fields.
 */
public final class SearchEngines {
  private static final Logger LOG = Logger.getLogger(SearchEngines.class);

  /** HTTP timeout for engines that answer from one search page. */
  static final int DEFAULT_TIMEOUT = 5000;

  /**
   * Archive.org's search page links to item pages the crawl reads one by one for their file lists;
   * 5 s cut it short on mobile links, so both clients use desktop's 15 s.
   */
  static final int INTERNET_ARCHIVE_TIMEOUT = 15000;

  /**
   * How long a TPB mirror may take to answer the probe. Desktop used 1 s, which left TPB
   * unavailable on slow mobile links; Android used 6 s. The probe runs in the background, so a
   * longer bound only delays when TPB becomes ready.
   */
  static final int TPB_MIRROR_RESPONSE_MS = 3000;

  static final int TPB_MIRRORS_TO_TEST = 6;

  private final SearchEngineAdapter adapter;
  private final RelaySearchWiring localWiring = new RelaySearchWiring();
  private final RelaySearchWiring distributedWiring = new RelaySearchWiring();
  private volatile String tpbDomain;
  private final List<SearchEngine> engines;
  private final SearchEngine telluride;

  public SearchEngines(SearchEngineAdapter adapter) {
    this.adapter = adapter;
    this.telluride =
        new SearchEngine(
            SearchEngineID.TELLURIDE_ID,
            "Cloud Backup",
            adapter,
            () -> adapter.telluride() != null,
            (token, url, playlistMode) -> adapter.telluride().create(token, url, playlistMode));
    // The order the clients list engines in.
    this.engines =
        List.of(
            new SearchEngine(
                SearchEngineID.LOCAL_ID,
                "Local (test)",
                adapter,
                () -> localWiring.localIndex() != null,
                (token, keywords, playlistMode) ->
                    new LocalSharedTorrentSearchPerformer(
                        token,
                        keywords,
                        localWiring.localIndex(),
                        localWiring.karmaCache(),
                        localWiring.visibility(),
                        LocalSharedTorrentSearchPerformer.DEFAULT_RESULT_LIMIT)),
            new SearchEngine(
                SearchEngineID.DISTRIBUTED_ID,
                "Distributed",
                adapter,
                () ->
                    distributedWiring.localIndex() != null
                        && distributedWiring.peerDirectory() != null
                        && distributedWiring.identity() != null
                        && distributedWiring.searchTransport() != null,
                (token, keywords, playlistMode) ->
                    new DistributedSearchPerformer(
                        token,
                        keywords,
                        distributedWiring.localIndex(),
                        distributedWiring.peerDirectory(),
                        distributedWiring.identity(),
                        distributedWiring.searchTransport())),
            web(SearchEngineID.YT_ID, "YT", YTSearchPattern::new),
            web(
                SearchEngineID.INTERNET_ARCHIVE_ID,
                "Archive.org",
                InternetArchiveSearchPattern::new,
                InternetArchiveCrawlingStrategy::new,
                INTERNET_ARCHIVE_TIMEOUT),
            web(SearchEngineID.BITSEARCH_ID, "Bitsearch", BitsearchSearchPattern::new),
            web(SearchEngineID.KNABEN_ID, "Knaben", KnabenSearchPattern::new),
            web(SearchEngineID.MAGNETDL_ID, "magnetdl", MagnetDLSearchPattern::new),
            web(SearchEngineID.NYAA_ID, "Nyaa", NyaaSearchPattern::new),
            web(
                SearchEngineID.ONE337X_ID,
                "1337x",
                One337xSearchPattern::new,
                One337xCrawlingStrategy::new,
                DEFAULT_TIMEOUT),
            new SearchEngine(
                SearchEngineID.TPB_ID,
                "TPB",
                adapter,
                () -> tpbDomain != null,
                (token, keywords, playlistMode) ->
                    SearchPerformerFactory.createSearchPerformer(
                        token, keywords, new TPBSearchPattern(tpbDomain), null, DEFAULT_TIMEOUT)),
            web(SearchEngineID.TORRENTZ2_ID, "torrentz2", Torrentz2SearchPattern::new),
            web(SearchEngineID.TORRENTSCSV_ID, "TorrentsCSV", TorrentsCSVSearchPattern::new),
            web(
                SearchEngineID.SOUNDCLOUD_ID,
                "Soundcloud",
                () ->
                    new SoundcloudSearchPattern(
                        adapter.soundCloudClientId(), adapter.soundCloudAppVersion())),
            web(
                SearchEngineID.FROSTCLICK_ID,
                "FrostClick",
                () -> {
                  UserAgent userAgent = adapter.userAgent();
                  return new FrostClickSearchPattern(
                      userAgent.toString(), userAgent.getUUID(), userAgent.getHeadersMap());
                }));
  }

  private SearchEngine web(SearchEngineID id, String name, Supplier<SearchPattern> pattern) {
    return web(id, name, pattern, () -> null, DEFAULT_TIMEOUT);
  }

  private SearchEngine web(
      SearchEngineID id,
      String name,
      Supplier<SearchPattern> pattern,
      Supplier<CrawlingStrategy> crawling,
      int timeout) {
    return new SearchEngine(
        id,
        name,
        adapter,
        () -> true,
        (token, keywords, playlistMode) ->
            SearchPerformerFactory.createSearchPerformer(
                token, keywords, pattern.get(), crawling.get(), timeout));
  }

  /**
   * Finds the fastest TPB mirror on a named daemon thread; TPB stays not ready until it does, and
   * if no mirror answers. Call once, at startup.
   */
  public void startTpbMirrorProbe(HttpClient httpClient) {
    Thread probe =
        new Thread(
            () -> {
              try {
                tpbDomain =
                    UrlUtils.getFastestMirrorDomain(
                        httpClient,
                        TPBMirrors.getMirrors(),
                        TPB_MIRROR_RESPONSE_MS,
                        TPB_MIRRORS_TO_TEST);
              } catch (Throwable t) {
                LOG.warn("No TPB mirror found: " + t.getMessage());
              }
            },
            "tpb-mirror-finder");
    probe.setDaemon(true);
    probe.start();
  }

  /** For tests: the TPB mirror the probe would have found. */
  void tpbDomain(String domain) {
    this.tpbDomain = domain;
  }

  /** The engines this client offers, in listing order (Telluride is not listed: it opens URLs). */
  public List<SearchEngine> all() {
    return engines.stream().filter(SearchEngine::isOffered).toList();
  }

  /** The engines a search runs: offered, switched on by the user and ready. */
  public List<SearchEngine> enabled() {
    return engines.stream().filter(e -> e.isEnabled() && e.isReady()).toList();
  }

  /** The engine with this id, Telluride included; null if none. */
  public SearchEngine byId(SearchEngineID id) {
    if (id == SearchEngineID.TELLURIDE_ID) {
      return telluride;
    }
    return engines.stream().filter(e -> e.getId() == id).findFirst().orElse(null);
  }

  /**
   * The engine a result's source name names: its name, ignoring case, or its name followed by a
   * space and more (e.g. "TPB (mirror)"). "Cloud:" sources are Telluride's. Null if none.
   */
  public SearchEngine byName(String name) {
    if (name == null) {
      return null;
    }
    if (name.startsWith("Cloud:")) {
      return telluride;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    return engines.stream()
        .filter(
            e -> {
              String engine = e.getName().toLowerCase(Locale.ROOT);
              return lower.equals(engine) || lower.startsWith(engine + " ");
            })
        .findFirst()
        .orElse(null);
  }

  public SearchEngine telluride() {
    return telluride;
  }

  /** The Local engine's relay wiring; the client installs the index once the relay stack starts. */
  public RelaySearchWiring localWiring() {
    return localWiring;
  }

  /** The Distributed engine's relay wiring. */
  public RelaySearchWiring distributedWiring() {
    return distributedWiring;
  }
}
