/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import com.frostwire.util.Logger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The per-torrent {@link TorrentNetworkMode}, kept in memory and persisted in one small text file,
 * keyed by the canonical info hash (frostwire/frostwire#1301, #1304).
 *
 * <p>The key is the one {@code BTEngine.canonicalInfoHash} produces: the v1 hash when the torrent
 * has one (v1 and hybrid), else the v2 hash. {@link #canonicalKey(String, String)} spells that rule
 * for callers that only have hex strings; every sidecar must use the same key or a hybrid torrent
 * loses its choice on restart.
 *
 * <p>Threading: {@link #get}, {@link #set} and {@link #remove} only touch memory and never block,
 * so the EDT may call them. {@link #load()} and {@link #flushIfDirty()} do file I/O and must run
 * off the EDT; the engine calls them from its own threads.
 *
 * <p>File format, UTF-8, one entry per line: {@code <40 or 64 hex digits>=<MODE>}. Lines starting
 * with {@code #} and blank lines are comments. A torrent in the default mode has no line. Parsing
 * is defensive: a line that is not a valid entry, or names a mode this build does not know, is
 * skipped (the torrent falls back to the default) and never fails the load.
 */
public final class TorrentNetworkPolicyStore {

  private static final Logger LOG = Logger.getLogger(TorrentNetworkPolicyStore.class);

  /** Longest file {@link #load()} reads; a policy file is a few KB, this is only a guard. */
  static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

  /** Longest line the parser looks at; a valid entry is at most 64 + 1 + 14 characters. */
  static final int MAX_LINE_CHARS = 128;

  /** Most entries kept from one file. */
  static final int MAX_ENTRIES = 100_000;

  private static final String HEADER = "# FrostWire per-torrent network policy v1";

  private final File file;
  private final ConcurrentHashMap<String, TorrentNetworkMode> modes = new ConcurrentHashMap<>();
  private final AtomicBoolean dirty = new AtomicBoolean(false);
  private final Object ioLock = new Object();

  public TorrentNetworkPolicyStore(File file) {
    if (file == null) {
      throw new IllegalArgumentException("file");
    }
    this.file = file;
  }

  /** The torrent's mode; {@link TorrentNetworkMode#DEFAULT} for null, invalid or unknown keys. */
  public TorrentNetworkMode get(String infoHash) {
    String key = normalizeKey(infoHash);
    if (key == null) {
      return TorrentNetworkMode.DEFAULT;
    }
    TorrentNetworkMode mode = modes.get(key);
    return mode == null ? TorrentNetworkMode.DEFAULT : mode;
  }

  /**
   * Sets the torrent's mode in memory and marks the store for {@link #flushIfDirty()}.
   *
   * @return false, and changes nothing, when {@code infoHash} is not a valid hash, {@code mode} is
   *     null, or the store already holds {@link #MAX_ENTRIES} other torrents
   */
  public boolean set(String infoHash, TorrentNetworkMode mode) {
    String key = normalizeKey(infoHash);
    if (key == null || mode == null) {
      return false;
    }
    if (mode == TorrentNetworkMode.DEFAULT) {
      if (modes.remove(key) != null) {
        dirty.set(true);
      }
      return true;
    }
    if (!modes.containsKey(key) && modes.size() >= MAX_ENTRIES) {
      return false;
    }
    if (modes.put(key, mode) != mode) {
      dirty.set(true);
    }
    return true;
  }

  /** Forgets the torrent's choice (it is back to the default). Call when a torrent is removed. */
  public void remove(String infoHash) {
    String key = normalizeKey(infoHash);
    if (key != null && modes.remove(key) != null) {
      dirty.set(true);
    }
  }

  /** True when at least one torrent is in {@code mode}. Memory only, no copy. */
  public boolean hasAny(TorrentNetworkMode mode) {
    if (mode == null || mode == TorrentNetworkMode.DEFAULT) {
      return false;
    }
    for (TorrentNetworkMode m : modes.values()) {
      if (m == mode) {
        return true;
      }
    }
    return false;
  }

  /** A sorted, read-only copy of every non-default entry. */
  public Map<String, TorrentNetworkMode> snapshot() {
    return Collections.unmodifiableMap(new TreeMap<>(modes));
  }

  /** Replaces the in-memory entries with the file's. A missing or unreadable file is empty. */
  public void load() {
    byte[] bytes;
    synchronized (ioLock) {
      bytes = readFile();
      Map<String, TorrentNetworkMode> parsed =
          bytes == null
              ? Collections.<String, TorrentNetworkMode>emptyMap()
              : parse(new String(bytes, StandardCharsets.UTF_8));
      modes.clear();
      modes.putAll(parsed);
      dirty.set(false);
    }
  }

  /**
   * Writes the file when something changed since the last load or flush: to a temporary file, then
   * renamed over the old one, so a crash leaves the old or the new file, never half of one.
   *
   * @return true when the file is up to date, false when the write failed (it is retried by the
   *     next flush)
   */
  public boolean flushIfDirty() {
    synchronized (ioLock) {
      if (!dirty.getAndSet(false)) {
        return true;
      }
      byte[] bytes = serialize(modes).getBytes(StandardCharsets.UTF_8);
      File tmp = new File(file.getPath() + ".tmp");
      try {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
          throw new IOException("cannot create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(tmp)) {
          out.write(bytes);
          out.getFD().sync();
        }
        if (!tmp.renameTo(file)) {
          // Windows does not rename over an existing file.
          if (file.exists() && !file.delete()) {
            throw new IOException("cannot replace " + file);
          }
          if (!tmp.renameTo(file)) {
            throw new IOException("cannot rename " + tmp + " to " + file);
          }
        }
        return true;
      } catch (IOException | SecurityException e) {
        dirty.set(true);
        LOG.warn("TorrentNetworkPolicyStore: cannot write " + file + ": " + e);
        tmp.delete();
        return false;
      }
    }
  }

  private byte[] readFile() {
    if (!file.isFile()) {
      return null;
    }
    try (InputStream in = new FileInputStream(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int total = 0;
      int n;
      while ((n = in.read(buffer)) > 0) {
        total += n;
        if (total > MAX_FILE_BYTES) {
          LOG.warn("TorrentNetworkPolicyStore: " + file + " is too large, ignoring it");
          return null;
        }
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    } catch (IOException | SecurityException e) {
      LOG.warn("TorrentNetworkPolicyStore: cannot read " + file + ": " + e);
      return null;
    }
  }

  /**
   * Parses the file format. Never throws: bad lines, bad hashes and unknown modes are skipped, so
   * the result only holds valid hashes mapped to non-default modes. Linear in the input size and
   * capped at {@link #MAX_ENTRIES}. A hash that appears twice keeps its last mode.
   */
  public static Map<String, TorrentNetworkMode> parse(CharSequence text) {
    Map<String, TorrentNetworkMode> result = new TreeMap<>();
    if (text == null) {
      return result;
    }
    int length = text.length();
    int start = 0;
    while (start <= length) {
      int end = start;
      while (end < length && text.charAt(end) != '\n') {
        end++;
      }
      parseLine(text, start, end, result);
      start = end + 1;
    }
    return result;
  }

  private static void parseLine(
      CharSequence text, int start, int end, Map<String, TorrentNetworkMode> into) {
    // Trim, including the \r of CRLF files.
    while (start < end && Character.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    if (start >= end || end - start > MAX_LINE_CHARS || text.charAt(start) == '#') {
      return;
    }
    int eq = start;
    while (eq < end && text.charAt(eq) != '=') {
      eq++;
    }
    if (eq == end) {
      return;
    }
    String key = normalizeKey(text.subSequence(start, eq).toString());
    if (key == null) {
      return;
    }
    TorrentNetworkMode mode = TorrentNetworkMode.fromPersisted(text.subSequence(eq + 1, end));
    if (mode == TorrentNetworkMode.DEFAULT) {
      // A later "= MIXED" (or unknown) line resets an earlier choice.
      into.remove(key);
    } else if (into.containsKey(key) || into.size() < MAX_ENTRIES) {
      into.put(key, mode);
    }
  }

  /** The file contents for {@code entries}: sorted, default-mode and invalid entries left out. */
  public static String serialize(Map<String, TorrentNetworkMode> entries) {
    Map<String, TorrentNetworkMode> sorted = new TreeMap<>();
    if (entries != null) {
      for (Map.Entry<String, TorrentNetworkMode> e : entries.entrySet()) {
        String key = normalizeKey(e.getKey());
        TorrentNetworkMode mode = e.getValue();
        if (key != null && mode != null && mode != TorrentNetworkMode.DEFAULT) {
          sorted.put(key, mode);
        }
      }
    }
    StringBuilder sb = new StringBuilder(HEADER).append('\n');
    for (Map.Entry<String, TorrentNetworkMode> e : sorted.entrySet()) {
      sb.append(e.getKey()).append('=').append(e.getValue().persistedName()).append('\n');
    }
    return sb.toString();
  }

  /**
   * The canonical persistence key for a torrent from its hex info hashes: the v1 hash when there is
   * one (v1 and hybrid torrents), else the v2 hash (v2-only). Null when neither is a valid hash.
   * Same rule as {@code BTEngine.canonicalInfoHash}.
   */
  public static String canonicalKey(String v1Hex, String v2Hex) {
    String v1 = normalizeKey(v1Hex);
    if (v1 != null && !isAllZeros(v1)) {
      return v1;
    }
    String v2 = normalizeKey(v2Hex);
    if (v2 != null && !isAllZeros(v2)) {
      return v2;
    }
    return null;
  }

  /** Lowercase hex of 40 or 64 digits, or null for anything else. */
  static String normalizeKey(String hash) {
    if (hash == null) {
      return null;
    }
    String h = hash.trim();
    int n = h.length();
    if (n != 40 && n != 64) {
      return null;
    }
    for (int i = 0; i < n; i++) {
      char c = h.charAt(i);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!hex) {
        return null;
      }
    }
    return h.toLowerCase(Locale.ROOT);
  }

  private static boolean isAllZeros(String hex) {
    for (int i = 0; i < hex.length(); i++) {
      if (hex.charAt(i) != '0') {
        return false;
      }
    }
    return true;
  }
}
