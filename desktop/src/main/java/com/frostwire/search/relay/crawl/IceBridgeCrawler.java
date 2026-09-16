/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.search.relay.DhtRendezvous;
import com.frostwire.search.relay.RelayConstants;
import com.frostwire.search.relay.icebridge.IceBridgeDhtSession;
import com.frostwire.util.Logger;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Headless presence crawler for the IceBridge DHT heartbeat topics.
 *
 * <p>For every hour bucket in a small window it does a read-only BEP 5 {@code dhtGetPeers} lookup
 * on {@link RelayConstants#heartbeatTopic(long)} and records the returned {@code host:port} pairs.
 * It never dials, authenticates, or handshakes with any peer; the DHT lookup itself is the only
 * network activity beyond the embedding session's bootstrap traffic.
 *
 * <p>Run once with {@code --once}, continuously with the default loop, or dump stored counts with
 * {@code --report}.
 */
public final class IceBridgeCrawler {

  private static final Logger LOG = Logger.getLogger(IceBridgeCrawler.class);

  private IceBridgeCrawler() {}

  public static void main(String[] args) {
    CliOptions options;
    try {
      options = CliOptions.parse(args);
    } catch (IllegalArgumentException e) {
      System.err.println("error: " + e.getMessage());
      System.err.println(usage());
      System.exit(1);
      return;
    }
    System.exit(run(options));
  }

  static int run(CliOptions options) {
    File dbFile = new File(options.dbPath);
    if (options.report) {
      try (CrawlerStore store = CrawlerStore.open(dbFile)) {
        printSummary(store, options.buckets, System.currentTimeMillis());
        return 0;
      } catch (Throwable t) {
        LOG.error("Crawler report failed", t);
        return 1;
      }
    }

    IceBridgeDhtSession dht = null;
    CrawlerStore store = null;
    try {
      dht = IceBridgeDhtSession.start("0.0.0.0");
      store = CrawlerStore.open(dbFile);
      if (options.once) {
        runOnce(
            store, dht.session(), options.buckets, options.timeoutSec, System.currentTimeMillis());
      } else {
        loop(store, dht.session(), options);
      }
      printSummary(store, options.buckets, System.currentTimeMillis());
      return 0;
    } catch (Throwable t) {
      LOG.error("Crawler failed", t);
      return 1;
    } finally {
      closeQuietly(store);
      closeQuietly(dht);
    }
  }

  /**
   * One crawl pass: look up each heartbeat topic in the window {@code [current - (buckets - 1) ..
   * current]} and persist the unique endpoints. A DHT failure for one bucket is logged and the pass
   * continues; it never aborts the other buckets.
   */
  public static void runOnce(
      CrawlerStore store, SessionManager session, int buckets, int timeoutSec, long nowMs) {
    if (store == null) {
      throw new IllegalArgumentException("store is null");
    }
    if (session == null) {
      throw new IllegalArgumentException("session is null");
    }
    int window = Math.max(1, buckets);
    long current = RelayConstants.heartbeatBucketIndex(nowMs);
    for (long bucket = current - (window - 1); bucket <= current; bucket++) {
      Set<String> seen = new HashSet<>();
      List<String> hosts = new ArrayList<>();
      List<Integer> ports = new ArrayList<>();
      try {
        String topic = RelayConstants.heartbeatTopic(bucket * RelayConstants.HEARTBEAT_BUCKET_MS);
        List<TcpEndpoint> found =
            DhtRendezvous.find(session, DhtRendezvous.topic(topic), timeoutSec);
        collectEndpoints(found, seen, hosts, ports);
      } catch (Throwable t) {
        LOG.error("DHT lookup failed for heartbeat bucket " + bucket, t);
      }
      store.recordSightings(bucket, hosts, ports, nowMs);
      System.out.println("bucket " + bucket + ": " + hosts.size() + " endpoints");
    }
  }

  private static void loop(CrawlerStore store, SessionManager session, CliOptions options) {
    long intervalMs = TimeUnit.SECONDS.toMillis(options.intervalSec);
    while (true) {
      try {
        runOnce(store, session, options.buckets, options.timeoutSec, System.currentTimeMillis());
      } catch (Throwable t) {
        LOG.error("Crawl pass failed; continuing", t);
      }
      try {
        Thread.sleep(intervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static void collectEndpoints(
      List<TcpEndpoint> found, Set<String> seen, List<String> hosts, List<Integer> ports) {
    if (found == null || found.isEmpty()) {
      return;
    }
    // Snapshot: jlibtorrent alert threads may mutate the live list after return.
    TcpEndpoint[] snapshot = found.toArray(new TcpEndpoint[0]);
    for (TcpEndpoint endpoint : snapshot) {
      if (endpoint == null) {
        continue;
      }
      String host = endpoint.address() == null ? null : endpoint.address().toString();
      int port = endpoint.port();
      if (host == null || host.isBlank() || port <= 0) {
        continue;
      }
      if (seen.add(host + ":" + port)) {
        hosts.add(host);
        ports.add(port);
      }
    }
  }

  private static void printSummary(CrawlerStore store, int buckets, long nowMs) {
    int window = Math.max(1, buckets);
    System.out.println("--- presence summary (last " + window + " buckets) ---");
    for (CrawlerStore.BucketCount count : store.countsByBucket(window, nowMs)) {
      System.out.println(
          "bucket "
              + count.bucketIndex()
              + ": "
              + count.distinctEndpoints()
              + " distinct, "
              + count.newEndpoints()
              + " new");
    }
    System.out.println("total distinct endpoints: " + store.totalDistinct(window, nowMs));
  }

  private static void closeQuietly(CrawlerStore store) {
    if (store == null) {
      return;
    }
    try {
      store.close();
    } catch (Throwable t) {
      LOG.warn("CrawlerStore close failed", t);
    }
  }

  private static void closeQuietly(IceBridgeDhtSession dht) {
    if (dht == null) {
      return;
    }
    try {
      dht.close();
    } catch (Throwable t) {
      LOG.warn("IceBridge DHT session close failed", t);
    }
  }

  private static String usage() {
    return "Usage: IceBridgeCrawler [--db <path>] [--once] [--interval-sec <n>]"
        + " [--buckets <n>] [--timeout-sec <n>] [--report]";
  }

  static final class CliOptions {
    String dbPath = "icebridge-crawl.db";
    boolean once;
    boolean report;
    int intervalSec = 1800;
    int buckets = 3;
    int timeoutSec = 15;

    static CliOptions parse(String[] args) {
      CliOptions options = new CliOptions();
      for (int i = 0; args != null && i < args.length; i++) {
        String raw = args[i];
        String name = raw;
        String inline = null;
        int eq = raw.indexOf('=');
        if (raw.startsWith("--") && eq > 0) {
          name = raw.substring(0, eq);
          inline = raw.substring(eq + 1);
        }
        switch (name) {
          case "--db":
            {
              String db = value(args, i + 1, inline, name);
              if (db.isBlank()) {
                throw new IllegalArgumentException("--db requires a non-empty path");
              }
              options.dbPath = db;
              if (inline == null) {
                i++;
              }
              break;
            }
          case "--once":
            options.once = true;
            break;
          case "--interval-sec":
            options.intervalSec = positiveInt(value(args, i + 1, inline, name), name);
            if (inline == null) {
              i++;
            }
            break;
          case "--buckets":
            options.buckets = positiveInt(value(args, i + 1, inline, name), name);
            if (inline == null) {
              i++;
            }
            break;
          case "--timeout-sec":
            options.timeoutSec = positiveInt(value(args, i + 1, inline, name), name);
            if (inline == null) {
              i++;
            }
            break;
          case "--report":
            options.report = true;
            break;
          default:
            throw new IllegalArgumentException("unknown argument: " + raw);
        }
      }
      return options;
    }

    private static String value(String[] args, int index, String inline, String name) {
      if (inline != null) {
        return inline;
      }
      if (index >= args.length) {
        throw new IllegalArgumentException(name + " requires a value");
      }
      return args[index];
    }

    private static int positiveInt(String raw, String name) {
      try {
        int parsed = Integer.parseInt(raw.trim());
        if (parsed <= 0) {
          throw new IllegalArgumentException(name + " must be > 0: " + raw);
        }
        return parsed;
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(name + " must be an integer: " + raw);
      }
    }
  }
}
