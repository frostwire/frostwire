/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure-Java canonical bencode: what {@link Bencode} writes and what it refuses to read. */
class BencodeTest {

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.ISO_8859_1);
  }

  private static String str(byte[] b) {
    return new String(b, StandardCharsets.ISO_8859_1);
  }

  @Test
  void encodesScalars() {
    assertEquals("i0e", str(Bencode.encode(0L)));
    assertEquals("i-1e", str(Bencode.encode(-1)));
    assertEquals("i9223372036854775807e", str(Bencode.encode(Long.MAX_VALUE)));
    assertEquals("i-9223372036854775808e", str(Bencode.encode(Long.MIN_VALUE)));
    assertEquals("i7e", str(Bencode.encode((short) 7)));
    assertEquals("i-8e", str(Bencode.encode((byte) -8)));
    assertEquals("0:", str(Bencode.encode("")));
    assertEquals("4:spam", str(Bencode.encode("spam")));
    assertEquals("3:\u0000ÿ\u0001", str(Bencode.encode(new byte[] {0, (byte) 0xff, 1})));
  }

  @Test
  void encodesStringsAsUtf8() {
    // U+00E9 is 2 bytes, U+65E5 is 3, U+1F600 is 4 (a surrogate pair in Java).
    byte[] encoded = Bencode.encode("é日😀");
    assertEquals("9:", str(encoded).substring(0, 2));
    byte[] expected = "é日😀".getBytes(StandardCharsets.UTF_8);
    byte[] body = new byte[encoded.length - 2];
    System.arraycopy(encoded, 2, body, 0, body.length);
    assertArrayEquals(expected, body);
  }

  @Test
  void encodesListsAndDictionaries() {
    Map<String, Object> dict = new HashMap<>();
    dict.put("spam", List.of("a", 1L));
    dict.put("cow", "moo");
    dict.put("", new TreeMap<>());
    assertEquals("d0:de3:cow3:moo4:spaml1:ai1eee", str(Bencode.encode(dict)));
    assertEquals("le", str(Bencode.encode(List.of())));
  }

  @Test
  void sortsKeysByUtf8BytesNotUtf16() {
    // U+FF61 sorts after U+1F600 in UTF-16 (0xFF61 > 0xD83D) but before it in UTF-8
    // (EF BD A1 < F0 9F 98 80). libtorrent sorts bytes.
    Map<String, Object> dict = new TreeMap<>();
    dict.put("｡", 1L);
    dict.put("😀", 2L);
    dict.put("Z", 3L);
    dict.put("a", 4L);
    byte[] encoded = Bencode.encode(dict);
    String s = new String(encoded, StandardCharsets.UTF_8);
    assertEquals("d1:Zi3e1:ai4e3:｡i1e4:😀i2ee", s);
    assertArrayEquals(encoded, Bencode.encode(Bencode.decode(encoded)));
  }

  @Test
  void rejectsWhatItCannotEncode() {
    assertThrows(IllegalArgumentException.class, () -> Bencode.encode(null));
    assertThrows(IllegalArgumentException.class, () -> Bencode.encode(1.5d));
    assertThrows(IllegalArgumentException.class, () -> Bencode.encode(List.of(new Object())));
    Map<Object, Object> intKey = new HashMap<>();
    intKey.put(1, "x");
    assertThrows(IllegalArgumentException.class, () -> Bencode.encode(intKey));
    // An unpaired surrogate has no UTF-8 form.
    assertThrows(IllegalArgumentException.class, () -> Bencode.encode("\uD800"));
    List<Object> withNull = new ArrayList<>();
    withNull.add(null);
    assertThrows(IllegalArgumentException.class, () -> Bencode.encode(withNull));
  }

  @Test
  void decodesEveryType() {
    Map<String, Object> dict = Bencode.decodeDictionary(ascii("d1:ai-42e1:bl0:i0ee1:cd1:x3:abcee"));
    assertEquals(List.of("a", "b", "c"), new ArrayList<>(dict.keySet()));
    assertEquals(-42L, Bencode.asLong(dict.get("a")));
    List<Object> list = Bencode.asList(dict.get("b"));
    assertArrayEquals(new byte[0], (byte[]) list.get(0));
    assertEquals(0L, list.get(1));
    assertEquals("abc", Bencode.asString(Bencode.asDictionary(dict.get("c")).get("x")));
  }

  @Test
  void decodesLongExtremes() {
    assertEquals(Long.MAX_VALUE, Bencode.decode(ascii("i9223372036854775807e")));
    assertEquals(-Long.MAX_VALUE, Bencode.decode(ascii("i-9223372036854775807e")));
    // libtorrent's entry writes Long.MIN_VALUE but its bdecode_node reads it back as 0, so a
    // value carrying it never survives the DHT: rejected, like any other overflow.
    assertThrows(
        IllegalArgumentException.class, () -> Bencode.decode(ascii("i-9223372036854775808e")));
  }

  @Test
  void accessorsReturnNullForOtherTypes() {
    assertNull(Bencode.asLong(new byte[0]));
    assertNull(Bencode.asString(1L));
    assertNull(Bencode.asList(new TreeMap<>()));
    assertNull(Bencode.asDictionary(List.of()));
    assertNull(Bencode.asString(null));
    // Invalid UTF-8 in a value is replaced, as Entry.string() does.
    assertEquals("�", Bencode.asString(new byte[] {(byte) 0xff}));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "", // empty
        "i", // truncated
        "ie", // empty integer
        "i-e", // sign only
        "i-0e", // negative zero
        "i00e", // leading zero
        "i01e", // leading zero
        "i-01e", // leading zero
        "i1", // unterminated
        "i1.5e", // not an integer
        "i+1e", // plus sign
        "i9223372036854775808e", // overflow
        "i-9223372036854775809e", // underflow
        "i99999999999999999999999e", // overflow
        "01:a", // non-canonical length
        "-1:a", // negative length
        "2:a", // runs past the end
        ":", // no length
        "1a", // bad length
        "4294967297:a", // huge length
        "l", // unterminated list
        "li1e", // unterminated list
        "d", // unterminated dictionary
        "d1:a", // key without value
        "di1ei1ee", // integer key
        "d1:bi1e1:ai1ee", // unsorted keys
        "d1:ai1e1:ai2ee", // duplicate keys
        "i1ei2e", // trailing data
        "0:0:", // trailing data
        "le ", // trailing data
        "x", // unknown type
        "e", // stray end
      })
  void rejectsMalformedOrNonCanonical(String input) {
    assertThrows(IllegalArgumentException.class, () -> Bencode.decode(ascii(input)));
  }

  @Test
  void rejectsNonUtf8KeysButKeepsNonUtf8Values() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Bencode.decode(new byte[] {'d', '1', ':', (byte) 0xff, 'i', '1', 'e', 'e'}));
    byte[] value = {'1', ':', (byte) 0xff};
    assertArrayEquals(new byte[] {(byte) 0xff}, (byte[]) Bencode.decode(value));
  }

  @Test
  void rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> Bencode.decode(null));
    assertThrows(IllegalArgumentException.class, () -> Bencode.decodeDictionary(ascii("le")));
  }

  @Test
  void enforcesDepthLimit() {
    String deep = "l".repeat(Bencode.DEFAULT_MAX_DEPTH) + "e".repeat(Bencode.DEFAULT_MAX_DEPTH);
    Bencode.decode(ascii(deep));
    String deeper =
        "l".repeat(Bencode.DEFAULT_MAX_DEPTH + 1) + "e".repeat(Bencode.DEFAULT_MAX_DEPTH + 1);
    assertThrows(IllegalArgumentException.class, () -> Bencode.decode(ascii(deeper)));
    // Far past any stack: rejected by the limit, not a StackOverflowError.
    String hostile = "l".repeat(1_000_000);
    assertThrows(IllegalArgumentException.class, () -> Bencode.decode(ascii(hostile)));
  }

  @Test
  void enforcesStringAndItemLimits() {
    assertThrows(IllegalArgumentException.class, () -> Bencode.decode(ascii("5:abcde"), 4, 4, 100));
    assertEquals("abcd", Bencode.asString(Bencode.decode(ascii("4:abcd"), 4, 4, 100)));
    assertThrows(IllegalArgumentException.class, () -> Bencode.decode(ascii("li1ei2ee"), 4, 4, 2));
    assertEquals(1, Bencode.asList(Bencode.decode(ascii("li1ee"), 4, 4, 2)).size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "i0e",
        "i-7e",
        "0:",
        "le",
        "de",
        "d0:0:e",
        "lli1eeleld1:ai2eeee",
        "d1:Ai1e1:ai2e2:aai3e1:bl1:x1:yee",
        "d4:spaml1:a1:bee",
      })
  void decodeThenEncodeIsIdentityForCanonicalInput(String input) {
    assertArrayEquals(ascii(input), Bencode.encode(Bencode.decode(ascii(input))));
  }

  @Test
  void roundTripsBinaryAndUnicode() {
    Map<String, Object> dict = new TreeMap<>();
    dict.put("bin", new byte[] {0, 1, 2, (byte) 0xfe, (byte) 0xff});
    dict.put("日本", "é😀");
    dict.put("n", -123456789012L);
    byte[] encoded = Bencode.encode(dict);
    Map<String, Object> decoded = Bencode.decodeDictionary(encoded);
    assertArrayEquals(encoded, Bencode.encode(decoded));
    assertEquals("é😀", Bencode.asString(decoded.get("日本")));
    assertTrue(decoded.get("bin") instanceof byte[]);
  }

  /**
   * frostwire/frostwire#1398: our own unsigned compare, since Android has the JDK one from API 33.
   */
  @Test
  void compareUnsignedOrdersBytesLikeLibtorrent() {
    byte[] low = {0x7f};
    byte[] high = {(byte) 0x80};
    assertTrue(Bencode.compareUnsigned(low, high) < 0, "0x80 sorts after 0x7f as unsigned");
    assertTrue(Bencode.compareUnsigned(high, low) > 0);
    assertTrue(Bencode.compareUnsigned(new byte[] {1}, new byte[] {1, 0}) < 0, "prefix first");
    assertEquals(
        0, Bencode.compareUnsigned(new byte[] {1, (byte) 0xff}, new byte[] {1, (byte) 0xff}));
    byte[][] keys = {{(byte) 0xff}, {0}, {(byte) 0x80, 1}, {(byte) 0x80}, {}};
    for (byte[] a : keys) {
      for (byte[] b : keys) {
        assertEquals(
            Integer.signum(java.util.Arrays.compareUnsigned(a, b)),
            Integer.signum(Bencode.compareUnsigned(a, b)));
      }
    }
  }
}
