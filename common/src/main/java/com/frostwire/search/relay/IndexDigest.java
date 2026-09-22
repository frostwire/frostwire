/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded keyword fingerprint a node publishes so forwarders can route a search to peers that
 * <em>might</em> hold it instead of blindly fanning out.
 *
 * <p>A Bloom filter over normalized name tokens. Properties that matter for routing:
 *
 * <ul>
 *   <li><b>No false negatives</b> for tokens indexed with the same tokenizer: if a peer holds a
 *       keyword, its digest always reports {@link #mightContain}. Missing a real holder is never
 *       acceptable.
 *   <li>Bounded size: a fixed {@link #DEFAULT_BYTES} byte frame the mesh can carry in one payload.
 *   <li>False positives only cost a little extra work (one extra peer queried), never correctness.
 * </ul>
 *
 * <p>Tokens are Unicode-normalized (NFD, diacritics stripped, lower-cased) so {@code "Inglés"} and
 * {@code "ingles"} route to the same holder.
 *
 * <p>Thread-safe: immutable after construction.
 */
public final class IndexDigest {

  /** Bytes of the bit array a leaf publishes. 4096 × 8 = 32768 bits. */
  public static final int DEFAULT_BYTES = 4096;

  /**
   * Bit-array size of an ultrapeer's cluster table (OR of its leaves, expanded).
   * 16384 × 8 = 131072 bits, the size late Gnutella used between ultrapeers.
   */
  public static final int CLUSTER_BYTES = 16384;

  public static final int MIN_BYTES = 64;
  public static final int MAX_BYTES = CLUSTER_BYTES;

  /** Wire version. Frames without it are rejected so an old hash cannot be misread. */
  public static final int VERSION = 1;

  private static final Set<String> STOPWORDS = Set.of(
      "the", "and", "for", "with", "from", "that", "this", "you", "not",
      "mp3", "mp4", "m4a", "m4v", "flac", "ogg", "wav", "aac", "wma",
      "mkv", "avi", "wmv", "webm", "mov", "mpg", "mpeg",
      "jpg", "jpeg", "png", "gif", "pdf", "zip", "rar",
      "720", "1080", "2160", "480", "1440",
      "x264", "x265", "h264", "h265", "uhd", "hdr");

  /** Number of Bloom hash probes per token. */
  public static final int HASHES = 4;

  /**
   * Upper bound on indexed tokens so the false-positive rate stays low: with 2048 tokens over
   * 32768 bits and k=4 the rate is ~0.24%.
   */
  public static final int MAX_TOKENS = 2048;

  /** Minimum token length; shorter fragments add noise without routing value. */
  public static final int MIN_TOKEN_LENGTH = 3;

  /** Per-text cap so a huge file list cannot make digest building expensive. */
  public static final int MAX_TEXT_CHARS = 16 * 1024;

  private static final int MASK = 0xFF;

  private final byte[] bits;

  private IndexDigest(byte[] bits) {
    this.bits = bits;
  }

  /**
   * Normalize free text into routing tokens: lower-case, diacritics stripped, split on non
   * alphanumerics, drop short fragments, de-duplicated, insertion-ordered.
   */
  public static List<String> tokenize(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }
    if (text.length() > MAX_TEXT_CHARS) {
      text = text.substring(0, MAX_TEXT_CHARS);
    }
    Set<String> tokens = new LinkedHashSet<>();
    String normalized =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(java.util.Locale.ROOT);
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < normalized.length() && tokens.size() < MAX_TOKENS; i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        current.append(c);
      } else {
        flushToken(current, tokens);
      }
    }
    flushToken(current, tokens);
    return new ArrayList<>(tokens);
  }

  private static void flushToken(StringBuilder current, Set<String> out) {
    if (current.length() >= MIN_TOKEN_LENGTH && out.size() < MAX_TOKENS) {
      String token = current.toString();
      if (!STOPWORDS.contains(token)) {
        out.add(token);
      }
    }
    current.setLength(0);
  }

  /** Build a digest from raw text fragments (torrent names, file paths). */
  public static IndexDigest build(Collection<String> texts) {
    return build(texts, DEFAULT_BYTES);
  }

  public static IndexDigest build(Collection<String> texts, int bytes) {
    int size = clampBytes(bytes);
    byte[] out = new byte[size];
    if (texts != null) {
      // Deduplicate across fragments: counting repeated tokens toward MAX_TOKENS would stop
      // indexing early on repetitive names/file lists and silently drop real keywords, which
      // would violate the no-false-negatives invariant (a holder becomes invisible for its own
      // content).
      Set<String> indexed = new LinkedHashSet<>();
      outer:
      for (String text : texts) {
        for (String token : tokenize(text)) {
          if (indexed.size() >= MAX_TOKENS) {
            break outer;
          }
          if (indexed.add(token)) {
            index(out, token);
          }
        }
      }
    }
    return new IndexDigest(out);
  }

  /** Build directly from already-tokenized values. */
  public static IndexDigest fromTokens(Collection<String> tokens) {
    byte[] out = new byte[DEFAULT_BYTES];
    if (tokens != null) {
      Set<String> indexed = new LinkedHashSet<>();
      outer:
      for (String token : tokens) {
        if (token == null) {
          continue;
        }
        for (String part : tokenize(token)) {
          if (indexed.size() >= MAX_TOKENS) {
            break outer;
          }
          if (indexed.add(part)) {
            index(out, part);
          }
        }
      }
    }
    return new IndexDigest(out);
  }

  /**
   * Wrap a received digest frame. Returns {@code null} when the frame is not a plausible digest so
   * callers can reject malformed peer announcements without throwing.
   */
  public static IndexDigest fromBytes(byte[] raw) {
    if (raw == null || raw.length < 1 + MIN_BYTES || raw[0] != (byte) VERSION) {
      return null;
    }
    int bitBytes = raw.length - 1;
    if (bitBytes > MAX_BYTES || Integer.bitCount(bitBytes) != 1) {
      return null;
    }
    byte[] bits = new byte[bitBytes];
    System.arraycopy(raw, 1, bits, 0, bitBytes);
    return new IndexDigest(bits);
  }

  /**
   * OR leaf filters into one cluster table. A token set in a smaller power-of-two
   * filter is expanded so a probe of the cluster cannot miss it.
   */
  public static IndexDigest aggregate(Collection<IndexDigest> parts) {
    byte[] cluster = new byte[CLUSTER_BYTES];
    if (parts != null) {
      for (IndexDigest part : parts) {
        if (part != null) {
          orExpanded(part.bits, cluster);
        }
      }
    }
    return new IndexDigest(cluster);
  }

  /**
   * Hits required before a peer counts as a holder. One or two tokens must all hit.
   * Longer queries use LimeWire's two-thirds rule so one noisy token cannot hide a match
   * and one common token cannot mark every library.
   */
  public static int requiredHits(int tokenCount) {
    if (tokenCount <= 2) {
      return Math.max(tokenCount, 1);
    }
    return (tokenCount * 2 + 2) / 3;
  }

  /** True when this filter passes {@link #requiredHits} for the already-tokenized query. */
  public boolean routes(Collection<String> queryTokens) {
    if (queryTokens == null || queryTokens.isEmpty()) {
      return false;
    }
    int hits = 0;
    int need = requiredHits(queryTokens.size());
    for (String token : queryTokens) {
      if (token != null && mightContainExact(token)) {
        hits++;
      }
    }
    return hits >= need;
  }

  public byte[] toBytes() {
    byte[] out = new byte[1 + bits.length];
    out[0] = (byte) VERSION;
    System.arraycopy(bits, 0, out, 1, bits.length);
    return out;
  }

  public int byteLength() {
    return bits.length;
  }

  /** True when the token may have been indexed (never false for an indexed token). */
  public boolean mightContain(String token) {
    if (token == null) {
      return false;
    }
    List<String> parts = tokenize(token);
    if (parts.isEmpty()) {
      return false;
    }
    for (String part : parts) {
      if (mightContainExact(part)) {
        return true;
      }
    }
    return false;
  }

  /** Number of query tokens the digest reports as possibly present. */
  public int matchCount(Collection<String> queryTokens) {
    int matched = 0;
    if (queryTokens == null) {
      return 0;
    }
    for (String token : queryTokens) {
      if (mightContain(token)) {
        matched++;
      }
    }
    return matched;
  }

  /** True when every supplied query token may be present. */
  public boolean mightContainAll(Collection<String> queryTokens) {
    if (queryTokens == null || queryTokens.isEmpty()) {
      return false;
    }
    for (String token : queryTokens) {
      if (!mightContain(token)) {
        return false;
      }
    }
    return true;
  }

  /** Approximate number of set bits, exposed for tests and diagnostics. */
  public int population() {
    int count = 0;
    for (byte b : bits) {
      count += Integer.bitCount(b & MASK);
    }
    return count;
  }

  private boolean mightContainExact(String token) {
    long bitCount = (long) bits.length * 8L;
    int shift = 64 - Long.numberOfTrailingZeros(bitCount);
    for (int i = 0; i < HASHES; i++) {
      if (!bitSet(bits, hash(token, i) >>> shift)) {
        return false;
      }
    }
    return true;
  }

  private static void index(byte[] out, String token) {
    long bitCount = (long) out.length * 8L;
    int shift = 64 - Long.numberOfTrailingZeros(bitCount);
    for (int i = 0; i < HASHES; i++) {
      setBit(out, hash(token, i) >>> shift);
    }
  }

  /** OR {@code small} into {@code large}, expanding when the cluster is a larger power of two. */
  private static void orExpanded(byte[] small, byte[] large) {
    int smallBits = small.length * 8;
    int largeBits = large.length * 8;
    if (smallBits == largeBits) {
      for (int i = 0; i < small.length; i++) {
        large[i] |= small[i];
      }
      return;
    }
    if (largeBits < smallBits || largeBits % smallBits != 0) {
      return;
    }
    int factor = largeBits / smallBits;
    for (int i = 0; i < smallBits; i++) {
      if (!bitSet(small, i)) {
        continue;
      }
      int base = i * factor;
      for (int j = 0; j < factor; j++) {
        setBit(large, base + j);
      }
    }
  }

  private static boolean bitSet(byte[] bits, long bit) {
    int index = (int) (bit >>> 3);
    return (bits[index] & (1 << (bit & 7))) != 0;
  }

  private static void setBit(byte[] bits, long bit) {
    int index = (int) (bit >>> 3);
    bits[index] |= (byte) (1 << (bit & 7));
  }

  private static long hash(String token, int salt) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update((byte) salt);
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      long value = 0;
      for (int i = 0; i < 8; i++) {
        value = (value << 8) | (hash[i] & 0xFFL);
      }
      return value;
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static int clampBytes(int bytes) {
    if (bytes < MIN_BYTES) {
      return MIN_BYTES;
    }
    return Math.min(bytes, MAX_BYTES);
  }
}
