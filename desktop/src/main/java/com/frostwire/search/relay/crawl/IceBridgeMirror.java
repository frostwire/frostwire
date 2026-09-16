/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.relay.MeshTorrentMetadataFetcher;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Operator-approved, strictly bounded "mirror" seeder.
 *
 * <p>Given an explicit allowlist of v1 (40-hex) infohashes, it resolves each torrent's holder
 * public key from a previously crawled {@link CrawlerStore}, fetches the full .torrent bytes from
 * the relay control API, independently verifies the fetched bytes actually match the allowlisted
 * infohash, writes {@code <ih>.torrent} into the mirror directory, and (unless {@code --dry-run})
 * adds it to a standalone {@link SessionManager} that seeds from that directory.
 *
 * <p>Nothing outside the allowlist is ever fetched or seeded, every decision is logged to {@code
 * System.out}, and the cumulative {@code size_bytes} of what gets seeded is capped by {@code
 * --max-total-bytes}. Anonymous: no peer address is ever stored or printed.
 */
public final class IceBridgeMirror {

  private static final Logger LOG = Logger.getLogger(IceBridgeMirror.class);

  /** Same public DHT bootstrap set used by the rest of the IceBridge stack. */
  static final String DHT_BOOTSTRAP_NODES =
      "dht.libtorrent.org:25401,"
          + "router.bittorrent.com:6881,"
          + "dht.transmissionbt.com:6881,"
          + "router.silotis.us:6881";

  private static final long DEFAULT_MAX_TOTAL_BYTES = 20L * 1024 * 1024 * 1024; // 20 GB
  private static final Pattern INFO_HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");

  private IceBridgeMirror() {}

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
    System.exit(mirror(options));
  }

  static int mirror(CliOptions options) {
    Set<String> allowlist = loadAllowlist(options);
    if (allowlist.isEmpty()) {
      System.out.println("mirror: no allowlisted infohashes; nothing to do");
      return 0;
    }
    File mirrorDir = new File(options.mirrorDir);
    if (!ensureDirectory(mirrorDir)) {
      LOG.error("mirror: cannot create mirror directory " + mirrorDir);
      return 1;
    }
    CrawlerStore store = null;
    SessionManager session = null;
    try {
      store = CrawlerStore.open(new File(options.dbPath));
      List<CrawlerStore.Torrent> catalog = store.torrents(store.torrentCount());
      if (!options.dryRun) {
        session = startSession();
        System.out.println("mirror: session started, seeding from " + mirrorDir);
      }
      RelayControlClient relayClient = new RelayControlClient(options.relayUrl, options.relayToken);
      ByteBudget budget = new ByteBudget(options.maxTotalBytes);
      int seeded = 0;
      int index = 0;
      for (String ihHex : allowlist) {
        index++;
        System.out.println("mirror: [" + index + "/" + allowlist.size() + "] " + ihHex);
        String holderPub = holderPubFor(catalog, ihHex);
        if (holderPub == null) {
          System.out.println("mirror:   skip: holder publisher unknown for " + ihHex);
          continue;
        }
        CrawlerStore.Torrent torrent = findTorrent(catalog, ihHex);
        long sizeBytes = torrent == null ? 0L : torrent.sizeBytes();
        if (sizeBytes <= 0) {
          System.out.println("mirror:   skip: unknown/unbounded size for " + ihHex);
          continue;
        }
        if (!budget.canFit(sizeBytes)) {
          System.out.println(
              "mirror:   refuse: would exceed max-total-bytes ("
                  + budget.usedBytes()
                  + "+"
                  + sizeBytes
                  + " > "
                  + options.maxTotalBytes
                  + ")");
          continue;
        }
        byte[] bytes;
        try {
          long timeoutMs = (long) options.timeoutSec * 1000L;
          int boundedTimeoutMs =
              timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
          bytes = relayClient.fetchTorrent(ihHex, holderPub, boundedTimeoutMs);
        } catch (Throwable t) {
          LOG.warn("mirror: relay fetch failed for " + ihHex, t);
          bytes = null;
        }
        if (bytes == null) {
          System.out.println("mirror:   skip: relay returned no .torrent for " + ihHex);
          continue;
        }
        if (!MeshTorrentMetadataFetcher.matchesInfoHash(bytes, Hex.decode(ihHex))) {
          System.out.println("mirror:   reject: fetched .torrent does not match " + ihHex);
          continue;
        }
        if (!budget.tryReserve(sizeBytes)) {
          System.out.println("mirror:   refuse: would exceed max-total-bytes for " + ihHex);
          continue;
        }
        File torrentFile = new File(mirrorDir, ihHex + ".torrent");
        try {
          writeTorrentFile(torrentFile, bytes);
        } catch (IOException e) {
          LOG.error("mirror: could not write " + torrentFile, e);
          continue;
        }
        System.out.println("mirror:   wrote " + torrentFile.getName() + " (" + bytes.length + "B)");
        if (options.dryRun) {
          System.out.println("mirror:   dry-run: not adding to session");
          seeded++;
          continue;
        }
        if (addToSession(session, bytes, mirrorDir, ihHex, options.timeoutSec)) {
          seeded++;
        }
      }
      System.out.println(
          "mirror: done. allowlisted="
              + allowlist.size()
              + " seeded="
              + seeded
              + " reservedBytes="
              + budget.usedBytes()
              + " dryRun="
              + options.dryRun);
      return 0;
    } catch (Throwable t) {
      LOG.error("mirror failed", t);
      return 1;
    } finally {
      closeQuietly(session);
      closeQuietly(store);
    }
  }

  private static SessionManager startSession() {
    SessionManager session = new SessionManager(false);
    SettingsPack settings = new SettingsPack();
    settings.setEnableDht(true);
    settings.setEnableLsd(false);
    settings.setDhtBootstrapNodes(DHT_BOOTSTRAP_NODES);
    settings.listenInterfaces("0.0.0.0:0");
    session.start(new SessionParams(settings));
    return session;
  }

  private static boolean addToSession(
      SessionManager session, byte[] torrentBytes, File mirrorDir, String ihHex, int timeoutSec) {
    TorrentInfo info = null;
    try {
      info = TorrentInfo.bdecode(torrentBytes);
      if (info == null || !info.isValid()) {
        LOG.warn("mirror: invalid torrent info for " + ihHex);
        return false;
      }
      session.download(info, mirrorDir);
      if (awaitHandle(session, info, timeoutSec)) {
        System.out.println("mirror:   seeding " + ihHex + " from " + mirrorDir);
        return true;
      }
      LOG.warn("mirror: torrent handle did not appear for " + ihHex);
      return false;
    } catch (Throwable t) {
      LOG.error("mirror: could not add " + ihHex + " to session", t);
      return false;
    } finally {
      if (info != null) {
        try {
          info.swig().delete();
        } catch (Throwable ignored) {
          // best effort release of the native torrent_info
        }
      }
    }
  }

  /** Blocks until the freshly added torrent handle is visible, keeping one torrent in flight. */
  private static boolean awaitHandle(SessionManager session, TorrentInfo info, int timeoutSec) {
    long deadlineMs = System.currentTimeMillis() + Math.max(1, timeoutSec) * 1000L;
    while (System.currentTimeMillis() < deadlineMs) {
      try {
        if (session.find(info) != null) {
          return true;
        }
      } catch (Throwable t) {
        return false;
      }
      try {
        Thread.sleep(100L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  private static void writeTorrentFile(File target, byte[] bytes) throws IOException {
    if (target.exists() && target.length() > 0) {
      System.out.println("mirror:   torrent file already present: " + target.getName());
      return;
    }
    File parent = target.getAbsoluteFile().getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("could not create directory " + parent);
    }
    Files.write(
        target.toPath(),
        bytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
  }

  private static boolean ensureDirectory(File dir) {
    return dir != null && (dir.isDirectory() || dir.mkdirs());
  }

  static Set<String> loadAllowlist(CliOptions options) {
    Set<String> hashes = new LinkedHashSet<>();
    if (options.allowFile != null && !options.allowFile.isBlank()) {
      try {
        String text =
            Files.readString(new File(options.allowFile).toPath(), StandardCharsets.UTF_8);
        hashes.addAll(parseAllowlist(text));
      } catch (IOException e) {
        LOG.error("mirror: cannot read allowlist file " + options.allowFile, e);
      }
    }
    hashes.addAll(parseAllowArg(options.allow));
    return hashes;
  }

  /**
   * Parses one 40-hex v1 infohash per line; {@code #} comments, blanks and invalid entries are
   * skipped.
   */
  static Set<String> parseAllowlist(String text) {
    Set<String> hashes = new LinkedHashSet<>();
    if (text == null || text.isBlank()) {
      return hashes;
    }
    for (String rawLine : text.split("\\R")) {
      String line = rawLine;
      int comment = line.indexOf('#');
      if (comment >= 0) {
        line = line.substring(0, comment);
      }
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      if (!isValidInfoHash(line)) {
        System.out.println("mirror: ignoring invalid allowlist entry: " + line);
        continue;
      }
      hashes.add(line.toLowerCase(Locale.US));
    }
    return hashes;
  }

  /** Parses a comma-separated {@code --allow} value, skipping blanks and invalid entries. */
  static Set<String> parseAllowArg(String csv) {
    Set<String> hashes = new LinkedHashSet<>();
    if (csv == null || csv.isBlank()) {
      return hashes;
    }
    for (String part : csv.split(",")) {
      String value = part.trim();
      if (value.isEmpty()) {
        continue;
      }
      if (!isValidInfoHash(value)) {
        System.out.println("mirror: ignoring invalid allowlist entry: " + value);
        continue;
      }
      hashes.add(value.toLowerCase(Locale.US));
    }
    return hashes;
  }

  static boolean isValidInfoHash(String value) {
    return value != null && INFO_HASH_PATTERN.matcher(value.trim()).matches();
  }

  /** First catalog row for {@code infohash}, or null when it was never crawled. */
  static CrawlerStore.Torrent findTorrent(List<CrawlerStore.Torrent> torrents, String infohash) {
    if (torrents == null || infohash == null) {
      return null;
    }
    for (CrawlerStore.Torrent torrent : torrents) {
      if (torrent != null && infohash.equalsIgnoreCase(torrent.infohash())) {
        return torrent;
      }
    }
    return null;
  }

  /**
   * Holder publisher public key for {@code infohash}, or null when unknown/blank. Never guesses.
   */
  static String holderPubFor(List<CrawlerStore.Torrent> torrents, String infohash) {
    CrawlerStore.Torrent torrent = findTorrent(torrents, infohash);
    if (torrent == null) {
      return null;
    }
    String pub = torrent.publisherPeerId();
    return pub == null || pub.isBlank() ? null : pub.trim();
  }

  private static void closeQuietly(SessionManager session) {
    if (session == null) {
      return;
    }
    try {
      session.stop();
    } catch (Throwable t) {
      LOG.warn("mirror: session stop failed", t);
    }
  }

  private static void closeQuietly(CrawlerStore store) {
    if (store == null) {
      return;
    }
    try {
      store.close();
    } catch (Throwable t) {
      LOG.warn("mirror: CrawlerStore close failed", t);
    }
  }

  private static String usage() {
    return "Usage: IceBridgeMirror --db <path> --relay-url <url> [--relay-token <t>]"
        + " [--allow-file <path>] [--allow <ih,ih,...>] --mirror-dir <dir>"
        + " [--timeout-sec <n>] [--max-total-bytes <n>] [--dry-run]";
  }

  /**
   * Cumulative byte accounting for the strictly bounded total. {@link #canFit(long)} is a
   * non-mutating admission check; {@link #tryReserve(long)} commits the reservation.
   */
  static final class ByteBudget {
    private final long maxBytes;
    private long usedBytes;

    ByteBudget(long maxBytes) {
      this.maxBytes = Math.max(0L, maxBytes);
    }

    long maxBytes() {
      return maxBytes;
    }

    long usedBytes() {
      return usedBytes;
    }

    boolean canFit(long bytes) {
      if (bytes <= 0) {
        return true;
      }
      return usedBytes <= maxBytes - bytes;
    }

    boolean tryReserve(long bytes) {
      if (!canFit(bytes)) {
        return false;
      }
      usedBytes += bytes;
      return true;
    }
  }

  static final class CliOptions {
    String dbPath;
    String relayUrl;
    String relayToken = "";
    String allowFile;
    String allow;
    String mirrorDir;
    int timeoutSec = MeshTorrentMetadataFetcher.DEFAULT_TIMEOUT_SEC;
    long maxTotalBytes = DEFAULT_MAX_TOTAL_BYTES;
    boolean dryRun;

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
            options.dbPath = value(args, i + 1, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          case "--relay-url":
            options.relayUrl = value(args, i + 1, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          case "--relay-token":
            options.relayToken = value(args, i + 1, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          case "--allow-file":
            options.allowFile = value(args, i + 1, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          case "--allow":
            options.allow = value(args, i + 1, inline, name);
            if (inline == null) {
              i++;
            }
            break;
          case "--mirror-dir":
            options.mirrorDir = value(args, i + 1, inline, name);
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
          case "--max-total-bytes":
            options.maxTotalBytes = positiveLong(value(args, i + 1, inline, name), name);
            if (inline == null) {
              i++;
            }
            break;
          case "--dry-run":
            options.dryRun = true;
            break;
          default:
            throw new IllegalArgumentException("unknown argument: " + raw);
        }
      }
      if (isBlank(options.dbPath)) {
        throw new IllegalArgumentException("--db is required");
      }
      if (isBlank(options.relayUrl)) {
        throw new IllegalArgumentException("--relay-url is required");
      }
      if (isBlank(options.mirrorDir)) {
        throw new IllegalArgumentException("--mirror-dir is required");
      }
      if (isBlank(options.allowFile) && isBlank(options.allow)) {
        throw new IllegalArgumentException("--allow-file or --allow is required");
      }
      return options;
    }

    private static boolean isBlank(String value) {
      return value == null || value.isBlank();
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

    private static long positiveLong(String raw, String name) {
      try {
        long parsed = Long.parseLong(raw.trim());
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
