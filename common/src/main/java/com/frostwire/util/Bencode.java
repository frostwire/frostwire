/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pure-Java reader and writer of canonical bencode, the form libtorrent's {@code entry::bencode}
 * writes. Signed relay records (karma chain entries, identity records) hash and sign these bytes,
 * so {@link #encode} must stay byte-identical to libtorrent's output.
 *
 * <p>Values in the neutral form:
 *
 * <ul>
 *   <li>a dictionary is a {@code Map<String, ?>}; {@link #decode} returns a {@link LinkedHashMap}
 *       in key order;
 *   <li>a list is a {@code List<?>};
 *   <li>an integer is a {@link Long} ({@link Integer}, {@link Short} and {@link Byte} are also
 *       accepted by {@link #encode});
 *   <li>a byte string is a {@code byte[]} ({@link #decode} always returns {@code byte[]}; {@link
 *       #encode} also accepts a {@link String}, written as its UTF-8 bytes).
 * </ul>
 *
 * <p>Canonical form, which {@link #encode} writes and {@link #decode} requires:
 *
 * <ul>
 *   <li>dictionary keys sorted by their raw UTF-8 bytes (unsigned), with no duplicates, as
 *       libtorrent's {@code std::map<std::string, entry>} orders them;
 *   <li>integers in shortest decimal form: no leading zeros, no {@code -0}, and within {@code
 *       -(2^63 - 1)} to {@code 2^63 - 1}. {@link #encode} writes {@link Long#MIN_VALUE} (as
 *       libtorrent's {@code entry} does) but {@link #decode} rejects it, because libtorrent's
 *       {@code bdecode_node::int_value()} can't read it either and returns 0;
 *   <li>string lengths in shortest decimal form;
 *   <li>dictionary keys that are valid UTF-8 (they become {@code String}s);
 *   <li>nothing after the top-level value.
 * </ul>
 *
 * <p>So for every input {@link #decode} accepts, {@code encode(decode(bytes))} equals {@code
 * bytes}. libtorrent's {@code bdecode} is more lenient (it accepts unsorted keys, for one); bytes
 * produced by {@code Entry.bencode()} are always canonical.
 *
 * <p>{@link #decode} is for untrusted input: string copies are bounded by the input's size and
 * {@code maxStringLength}, the number of values (and so the list and map overhead) by {@code
 * maxItems}, recursion by {@code maxDepth}, and it throws {@link IllegalArgumentException} for
 * every malformed, non-canonical or over-limit input and for nothing else.
 */
public final class Bencode {

  /** Default nesting limit of {@link #decode(byte[])}. */
  public static final int DEFAULT_MAX_DEPTH = 32;

  /** Default limit of {@link #decode(byte[])} on one byte string's length. */
  public static final int DEFAULT_MAX_STRING_LENGTH = 1 << 20;

  /** Default limit of {@link #decode(byte[])} on the total number of values. */
  public static final int DEFAULT_MAX_ITEMS = 1 << 16;

  private Bencode() {}

  /**
   * Bencodes a value in the neutral form, sorting dictionary keys by their UTF-8 bytes.
   *
   * @throws IllegalArgumentException if a value (or a nested one) is null or of another type, a
   *     dictionary key isn't a {@code String}, or a {@code String} isn't valid UTF-16 (an unpaired
   *     surrogate)
   */
  public static byte[] encode(Object value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write(out, value);
    return out.toByteArray();
  }

  /**
   * Decodes canonical bencode with the default limits.
   *
   * @throws IllegalArgumentException if {@code data} is null, malformed, not canonical, has
   *     trailing bytes, or exceeds a limit
   */
  public static Object decode(byte[] data) {
    return decode(data, DEFAULT_MAX_DEPTH, DEFAULT_MAX_STRING_LENGTH, DEFAULT_MAX_ITEMS);
  }

  /**
   * Decodes canonical bencode.
   *
   * @param maxDepth deepest nesting of lists and dictionaries accepted (1 = a flat list or
   *     dictionary)
   * @param maxStringLength longest byte string accepted
   * @param maxItems most values accepted in total, counting containers, keys and the top value
   * @throws IllegalArgumentException if {@code data} is null, malformed, not canonical, has
   *     trailing bytes, or exceeds a limit
   */
  public static Object decode(byte[] data, int maxDepth, int maxStringLength, int maxItems) {
    if (data == null) {
      throw new IllegalArgumentException("data is null");
    }
    Decoder decoder = new Decoder(data, maxDepth, maxStringLength, maxItems);
    Object value = decoder.value(0);
    if (decoder.pos != data.length) {
      throw new IllegalArgumentException("trailing data at " + decoder.pos);
    }
    return value;
  }

  /**
   * Decodes canonical bencode whose top-level value must be a dictionary, with the default limits.
   *
   * @throws IllegalArgumentException as {@link #decode(byte[])} does, or if the value isn't a
   *     dictionary
   */
  public static Map<String, Object> decodeDictionary(byte[] data) {
    Map<String, Object> dict = asDictionary(decode(data));
    if (dict == null) {
      throw new IllegalArgumentException("not a dictionary");
    }
    return dict;
  }

  /** The value as a dictionary, or null if it isn't one (as {@link #decode} returns them). */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> asDictionary(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  /** The value as a list, or null if it isn't one. */
  @SuppressWarnings("unchecked")
  public static List<Object> asList(Object value) {
    return value instanceof List ? (List<Object>) value : null;
  }

  /** The value as an integer, or null if it isn't one. */
  public static Long asLong(Object value) {
    return value instanceof Long ? (Long) value : null;
  }

  /**
   * A byte string's text, decoded as UTF-8 with invalid sequences replaced by U+FFFD, or null if
   * the value isn't a byte string. jlibtorrent's {@code Entry.string()} returns the same only from
   * 2.0.15.0 (frostwire-jlibtorrent#336); 2.0.12.9 decodes strings as JNI modified UTF-8.
   */
  public static String asString(Object value) {
    if (value instanceof byte[]) {
      return new String((byte[]) value, StandardCharsets.UTF_8);
    }
    if (value instanceof String) {
      return (String) value;
    }
    return null;
  }

  private static void write(ByteArrayOutputStream out, Object value) {
    if (value instanceof byte[]) {
      writeBytes(out, (byte[]) value);
    } else if (value instanceof String) {
      writeBytes(out, utf8((String) value));
    } else if (value instanceof Long
        || value instanceof Integer
        || value instanceof Short
        || value instanceof Byte) {
      out.write('i');
      writeAscii(out, Long.toString(((Number) value).longValue()));
      out.write('e');
    } else if (value instanceof List) {
      out.write('l');
      for (Object item : (List<?>) value) {
        write(out, item);
      }
      out.write('e');
    } else if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      byte[][] keys = new byte[map.size()][];
      Object[] values = new Object[map.size()];
      Integer[] order = new Integer[map.size()];
      int i = 0;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (!(e.getKey() instanceof String)) {
          throw new IllegalArgumentException("dictionary key is not a String: " + e.getKey());
        }
        keys[i] = utf8((String) e.getKey());
        values[i] = e.getValue();
        order[i] = i;
        i++;
      }
      Arrays.sort(order, (a, b) -> compareUnsigned(keys[a], keys[b]));
      out.write('d');
      for (int k = 0; k < order.length; k++) {
        if (k > 0 && Arrays.equals(keys[order[k - 1]], keys[order[k]])) {
          throw new IllegalArgumentException("duplicate dictionary key");
        }
        writeBytes(out, keys[order[k]]);
        write(out, values[order[k]]);
      }
      out.write('e');
    } else {
      throw new IllegalArgumentException(
          "can't bencode " + (value == null ? "null" : value.getClass().getName()));
    }
  }

  private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
    writeAscii(out, Integer.toString(bytes.length));
    out.write(':');
    out.write(bytes, 0, bytes.length);
  }

  private static void writeAscii(ByteArrayOutputStream out, String s) {
    for (int i = 0; i < s.length(); i++) {
      out.write(s.charAt(i));
    }
  }

  private static byte[] utf8(String s) {
    try {
      ByteBuffer bb =
          StandardCharsets.UTF_8
              .newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .encode(CharBuffer.wrap(s));
      byte[] bytes = new byte[bb.remaining()];
      bb.get(bytes);
      return bytes;
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("string is not valid UTF-16", e);
    }
  }

  private static final class Decoder {
    private final byte[] data;
    private final int maxDepth;
    private final int maxStringLength;
    private final int maxItems;
    private int items;
    int pos;

    Decoder(byte[] data, int maxDepth, int maxStringLength, int maxItems) {
      this.data = data;
      this.maxDepth = maxDepth;
      this.maxStringLength = maxStringLength;
      this.maxItems = maxItems;
    }

    Object value(int depth) {
      if (++items > maxItems) {
        throw new IllegalArgumentException("more than " + maxItems + " values");
      }
      int c = peek();
      if (c == 'i') {
        pos++;
        long n = integer('e');
        return n;
      }
      if (c == 'l' || c == 'd') {
        if (depth >= maxDepth) {
          throw new IllegalArgumentException("nested deeper than " + maxDepth);
        }
        pos++;
        return c == 'l' ? list(depth + 1) : dictionary(depth + 1);
      }
      if (c >= '0' && c <= '9') {
        return string();
      }
      throw new IllegalArgumentException("unexpected byte " + c + " at " + pos);
    }

    private List<Object> list(int depth) {
      List<Object> list = new ArrayList<>();
      while (peek() != 'e') {
        list.add(value(depth));
      }
      pos++;
      return list;
    }

    private Map<String, Object> dictionary(int depth) {
      Map<String, Object> map = new LinkedHashMap<>();
      byte[] previous = null;
      while (peek() != 'e') {
        if (++items > maxItems) {
          throw new IllegalArgumentException("more than " + maxItems + " values");
        }
        int keyAt = pos;
        byte[] key = string();
        if (previous != null && compareUnsigned(previous, key) >= 0) {
          throw new IllegalArgumentException("dictionary key out of order at " + keyAt);
        }
        previous = key;
        map.put(utf8Key(key, keyAt), value(depth));
      }
      pos++;
      return map;
    }

    private byte[] string() {
      int start = pos;
      long length = integer(':');
      if (length < 0) {
        throw new IllegalArgumentException("negative string length at " + start);
      }
      if (length > maxStringLength) {
        throw new IllegalArgumentException("string longer than " + maxStringLength);
      }
      if (length > data.length - pos) {
        throw new IllegalArgumentException("string runs past the end at " + start);
      }
      byte[] bytes = Arrays.copyOfRange(data, pos, pos + (int) length);
      pos += (int) length;
      return bytes;
    }

    /** Reads a canonical decimal integer up to {@code terminator}, consuming it. */
    private long integer(char terminator) {
      int start = pos;
      boolean negative = false;
      if (peek() == '-') {
        if (terminator == ':') {
          throw new IllegalArgumentException("negative string length at " + start);
        }
        negative = true;
        pos++;
      }
      int digitsAt = pos;
      long n = 0;
      int c;
      while ((c = peek()) != terminator) {
        if (c < '0' || c > '9') {
          throw new IllegalArgumentException("bad integer at " + start);
        }
        int digit = c - '0';
        // Accumulate as a negative number: the check catches overflow before it wraps.
        if (n < (Long.MIN_VALUE + digit) / 10) {
          throw new IllegalArgumentException("integer overflow at " + start);
        }
        n = n * 10 - digit;
        pos++;
      }
      int digits = pos - digitsAt;
      if (digits == 0) {
        throw new IllegalArgumentException("empty integer at " + start);
      }
      if (data[digitsAt] == '0' && (digits > 1 || negative)) {
        throw new IllegalArgumentException("non-canonical integer at " + start);
      }
      pos++; // terminator
      if (n == Long.MIN_VALUE) {
        // libtorrent's reader overflows on 2^63 in either sign.
        throw new IllegalArgumentException("integer overflow at " + start);
      }
      return negative ? n : -n;
    }

    private int peek() {
      if (pos >= data.length) {
        throw new IllegalArgumentException("unexpected end of data");
      }
      return data[pos] & 0xff;
    }

    private static String utf8Key(byte[] key, int at) {
      try {
        return StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(key))
            .toString();
      } catch (CharacterCodingException e) {
        throw new IllegalArgumentException("dictionary key is not UTF-8 at " + at, e);
      }
    }
  }

  /**
   * Compares two byte arrays as unsigned bytes, lexicographically, like libtorrent's key order.
   * Arrays.compareUnsigned(byte[], byte[]) would do, but Android only has it from API 33 and
   * FrostWire runs on API 26+ (frostwire/frostwire#1398).
   */
  static int compareUnsigned(byte[] a, byte[] b) {
    int n = Math.min(a.length, b.length);
    for (int i = 0; i < n; i++) {
      int d = (a[i] & 0xff) - (b[i] & 0xff);
      if (d != 0) {
        return d;
      }
    }
    return a.length - b.length;
  }
}
