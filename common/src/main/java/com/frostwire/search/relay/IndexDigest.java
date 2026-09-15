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

  /** Bytes per published digest. 4096 bytes × 8 bits = 32768 bits. */
  public static final int DEFAULT_BYTES = 4096;

  public static final int MIN_BYTES = 64;
  public static final int MAX_BYTES = 8192;

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
      out.add(current.toString());
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
      int indexed = 0;
      for (String text : texts) {
        for (String token : tokenize(text)) {
          if (indexed >= MAX_TOKENS) {
            break;
          }
          index(out, token);
          indexed++;
        }
        if (indexed >= MAX_TOKENS) {
          break;
        }
      }
    }
    return new IndexDigest(out);
  }

  /** Build directly from already-tokenized values. */
  public static IndexDigest fromTokens(Collection<String> tokens) {
    byte[] out = new byte[DEFAULT_BYTES];
    if (tokens != null) {
      int indexed = 0;
      for (String token : tokens) {
        if (token == null || indexed >= MAX_TOKENS) {
          continue;
        }
        for (String part : tokenize(token)) {
          if (indexed >= MAX_TOKENS) {
            break;
          }
          index(out, part);
          indexed++;
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
    if (raw == null || raw.length < MIN_BYTES || raw.length > MAX_BYTES) {
      return null;
    }
    return new IndexDigest(raw.clone());
  }

  public byte[] toBytes() {
    return bits.clone();
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
    long m = (long) bits.length * 8L;
    long h1 = hash(token, 0);
    long h2 = hash(token, 1) | 1L;
    for (int i = 0; i < HASHES; i++) {
      long bit = ((h1 + (long) i * h2) % m + m) % m;
      int index = (int) (bit >>> 3);
      int mask = 1 << (bit & 7);
      if ((bits[index] & mask) == 0) {
        return false;
      }
    }
    return true;
  }

  private static void index(byte[] out, String token) {
    long m = (long) out.length * 8L;
    long h1 = hash(token, 0);
    long h2 = hash(token, 1) | 1L;
    for (int i = 0; i < HASHES; i++) {
      long bit = ((h1 + (long) i * h2) % m + m) % m;
      int index = (int) (bit >>> 3);
      out[index] |= (byte) (1 << (bit & 7));
    }
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
