/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.Executor;
import org.limewire.util.CommonUtils;

/**
 * Remembers which search results the user started downloading, so later searches can mark them
 * (#950).
 *
 * <p>Only a Bloom filter of SHA-256 hashes is kept, in memory and on disk: there is no list of
 * downloads to read back. It can answer "was this result downloaded?" for a result at hand, with a
 * tiny false positive rate (about 1 in 200 million after 10,000 downloads), and never misses one.
 * The file is a fixed {@value #BYTES} bytes no matter how many results were added.
 */
final class DownloadedSearchResults {
  private static final Logger LOG = Logger.getLogger(DownloadedSearchResults.class);

  static final int BITS = 1 << 20;
  static final int BYTES = BITS / 8;
  private static final int PROBES = 7;

  private final File file;
  private final Executor io;
  private final long[] words = new long[BITS / 64];

  DownloadedSearchResults(File file, Executor io) {
    this.file = file;
    this.io = io;
    io.execute(this::load);
  }

  static DownloadedSearchResults instance() {
    return Holder.INSTANCE;
  }

  /** Created on first use, so loading this class (e.g. for {@link #keyOf}) touches no files. */
  private static final class Holder {
    static final DownloadedSearchResults INSTANCE =
        new DownloadedSearchResults(
            new File(CommonUtils.getUserSettingsDir(), "downloaded_search_results.bloom"),
            BackgroundQueuedExecutorService::schedule);
  }

  /** Torrents are identified by info hash, everything else by its details page. */
  static String keyOf(UISearchResult sr) {
    String hash = sr.getHash();
    if (hash != null && !hash.isEmpty()) {
      return "hash:" + hash.toLowerCase(Locale.US);
    }
    String detailsUrl = sr.getDetailsUrl();
    if (detailsUrl != null && !detailsUrl.isEmpty()) {
      return "url:" + detailsUrl;
    }
    return null;
  }

  /** Marks the result as downloaded and saves the filter in the background. */
  void add(String key) {
    if (key == null) {
      return;
    }
    long[] h = hash(key);
    synchronized (words) {
      for (int i = 0; i < PROBES; i++) {
        int bit = bit(h, i);
        words[bit >>> 6] |= 1L << bit;
      }
    }
    io.execute(this::save);
  }

  boolean mightContain(String key) {
    if (key == null) {
      return false;
    }
    long[] h = hash(key);
    synchronized (words) {
      for (int i = 0; i < PROBES; i++) {
        int bit = bit(h, i);
        if ((words[bit >>> 6] & (1L << bit)) == 0) {
          return false;
        }
      }
    }
    return true;
  }

  /** Kirsch-Mitzenmacher double hashing over the two halves of the digest's first 16 bytes. */
  private static int bit(long[] h, int i) {
    return (int) Math.floorMod(h[0] + i * h[1], (long) BITS);
  }

  private static long[] hash(String key) {
    try {
      ByteBuffer digest =
          ByteBuffer.wrap(
              MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
      return new long[] {digest.getLong(), digest.getLong()};
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JVM", e);
    }
  }

  /** Merges the saved filter into the one in memory, keeping results added before it loaded. */
  private void load() {
    if (!file.isFile()) {
      return;
    }
    try {
      byte[] bytes = Files.readAllBytes(file.toPath());
      if (bytes.length != BYTES) {
        LOG.warn("Ignoring " + file + ": " + bytes.length + " bytes, expected " + BYTES);
        return;
      }
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      synchronized (words) {
        for (int i = 0; i < words.length; i++) {
          words[i] |= buffer.getLong();
        }
      }
    } catch (IOException e) {
      LOG.warn("Could not read " + file, e);
    }
  }

  private void save() {
    ByteBuffer buffer = ByteBuffer.allocate(BYTES);
    synchronized (words) {
      for (long word : words) {
        buffer.putLong(word);
      }
    }
    File tmp = new File(file.getPath() + ".tmp");
    try {
      Files.write(tmp.toPath(), buffer.array());
      Files.move(
          tmp.toPath(),
          file.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      LOG.warn("Could not save " + file, e);
    }
  }
}
