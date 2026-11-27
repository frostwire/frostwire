/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Exercises {@link PatriciaTrie} against its documented behaviour and against a TreeMap. */
final class PatriciaTrieTest {

  private static PatriciaTrie<String, String> lakeTrie() {
    PatriciaTrie<String, String> trie = new PatriciaTrie<>(new CharSequenceKeyAnalyzer());
    for (String s : new String[] {"Lime", "LimeWire", "LimeRadio", "Lax", "Lake", "Lovely"}) {
      trie.put(s, s);
    }
    return trie;
  }

  @Test
  void documentedExample() {
    PatriciaTrie<String, String> trie = lakeTrie();
    assertEquals("Lovely", trie.select("Lo"));
    assertEquals("Lime", trie.select("Lime"));
    assertEquals("{Lake=Lake, Lax=Lax}", trie.getPrefixedBy("La").toString());
    assertEquals(
        "[Lime, LimeRadio, LimeWire]",
        new ArrayList<>(trie.getPrefixedBy("Lime").keySet()).toString());
  }

  @Test
  void prefixLengthAndOffsetVariants() {
    PatriciaTrie<String, String> trie = lakeTrie();
    assertEquals(
        Arrays.asList("Lime", "LimeRadio", "LimeWire"),
        new ArrayList<>(trie.getPrefixedBy("LimePlastics", 4).keySet()));
    assertEquals(
        Arrays.asList("Lime", "LimeRadio", "LimeWire"),
        new ArrayList<>(trie.getPrefixedBy("The Lime Plastics", 4, 4).keySet()));
    assertEquals(
        Arrays.asList("Lake", "Lax"), new ArrayList<>(trie.getPrefixedByBits("La", 32).keySet()));
    assertTrue(trie.getPrefixedBy("Zebra").isEmpty());
  }

  @Test
  void prefixViewReflectsMutationsAndSupportsMapOperations() {
    PatriciaTrie<String, String> trie = lakeTrie();
    SortedMap<String, String> view = trie.getPrefixedBy("Lime");
    assertEquals(3, view.size());
    assertTrue(view.containsKey("LimeRadio"));
    assertFalse(view.containsKey("Lax"));
    assertEquals("LimeRadio", view.get("LimeRadio"));
    assertNull(view.get("Lax"));
    assertNull(view.remove("Lax"));
    assertEquals(6, trie.size());
    assertEquals("LimeRadio", view.remove("LimeRadio"));
    assertFalse(trie.containsKey("LimeRadio"));
    assertEquals(2, view.size());
    assertNull(view.put("LimeLight", "x"));
    assertEquals("x", trie.get("LimeLight"));
    assertThrows(IllegalArgumentException.class, () -> view.put("Lax2", "y"));
    Iterator<Map.Entry<String, String>> it = view.entrySet().iterator();
    it.next();
    it.remove();
    assertEquals(2, view.size());
    assertEquals(
        Arrays.asList("Lake", "Lax", "LimeLight", "LimeWire", "Lovely"),
        new ArrayList<>(trie.keySet()));
  }

  @Test
  void emptyKeyLivesInRoot() {
    PatriciaTrie<String, Integer> trie = new PatriciaTrie<>(new CharSequenceKeyAnalyzer());
    assertTrue(trie.isEmpty());
    assertNull(trie.put("", 1));
    assertEquals(1, trie.size());
    assertEquals(1, trie.put("", 2));
    assertEquals(1, trie.size());
    assertEquals(2, trie.get(""));
    trie.put("a", 3);
    assertEquals(Arrays.asList("", "a"), new ArrayList<>(trie.keySet()));
    assertEquals(2, trie.remove(""));
    assertFalse(trie.containsKey(""));
    assertEquals(1, trie.size());
  }

  @Test
  void nullKeyRejected() {
    PatriciaTrie<String, String> trie = new PatriciaTrie<>(new CharSequenceKeyAnalyzer());
    assertThrows(NullPointerException.class, () -> trie.put(null, "x"));
    assertNull(trie.get(null));
    assertFalse(trie.containsKey(null));
  }

  @Test
  void containsValueAndToString() {
    PatriciaTrie<String, String> trie = lakeTrie();
    assertTrue(trie.containsValue("Lax"));
    assertFalse(trie.containsValue("nope"));
    assertTrue(trie.toString().startsWith("Trie["));
    assertEquals(6, trie.size());
    trie.clear();
    assertEquals(0, trie.size());
    assertTrue(trie.isEmpty());
    assertNull(trie.select("Lax"));
  }

  @Test
  void traverseHonoursEveryStatus() {
    PatriciaTrie<String, String> trie = lakeTrie();
    List<String> seen = new ArrayList<>();
    Map.Entry<String, String> stop =
        trie.traverse(
            e -> {
              seen.add(e.getKey());
              return e.getKey().equals("Lax")
                  ? Trie.Cursor.SelectStatus.EXIT
                  : Trie.Cursor.SelectStatus.CONTINUE;
            });
    assertEquals("Lax", stop.getKey());
    assertEquals(Arrays.asList("Lake", "Lax"), seen);
    assertEquals(6, trie.size());

    // REMOVE every key that contains "Lime", keep going.
    assertNull(
        trie.traverse(
            e ->
                e.getKey().startsWith("Lime")
                    ? Trie.Cursor.SelectStatus.REMOVE
                    : Trie.Cursor.SelectStatus.CONTINUE));
    assertEquals(Arrays.asList("Lake", "Lax", "Lovely"), new ArrayList<>(trie.keySet()));

    Map.Entry<String, String> removed =
        trie.traverse(e -> Trie.Cursor.SelectStatus.REMOVE_AND_EXIT);
    assertEquals("Lake", removed.getKey());
    assertEquals("Lake", removed.getValue());
    assertFalse(trie.containsKey("Lake"));
    assertEquals(2, trie.size());
  }

  @Test
  void selectWithCursorWalksInXorOrder() {
    PatriciaTrie<String, String> trie = lakeTrie();
    List<String> order = new ArrayList<>();
    assertNull(
        trie.select(
            "Lime",
            e -> {
              order.add(e.getKey());
              return Trie.Cursor.SelectStatus.CONTINUE;
            }));
    assertEquals(6, order.size());
    assertEquals("Lime", order.get(0));

    Map.Entry<String, String> exit = trie.select("Lime", e -> Trie.Cursor.SelectStatus.EXIT);
    assertEquals("Lime", exit.getKey());
    assertEquals(6, trie.size());

    assertThrows(
        UnsupportedOperationException.class,
        () -> trie.select("Lime", e -> Trie.Cursor.SelectStatus.REMOVE));

    Map.Entry<String, String> gone =
        trie.select("Lovely", e -> Trie.Cursor.SelectStatus.REMOVE_AND_EXIT);
    assertEquals("Lovely", gone.getKey());
    assertFalse(trie.containsKey("Lovely"));
    assertEquals(5, trie.size());
  }

  @Test
  void iteratorRemovalAndViews() {
    PatriciaTrie<String, String> trie = lakeTrie();
    Iterator<String> keys = trie.keySet().iterator();
    assertEquals("Lake", keys.next());
    keys.remove();
    assertFalse(trie.containsKey("Lake"));
    assertThrows(IllegalStateException.class, keys::remove);
    assertTrue(trie.keySet().remove("Lax"));
    assertFalse(trie.keySet().remove("Lax"));
    assertTrue(trie.keySet().contains("Lime"));
    assertEquals(4, trie.keySet().size());
    assertTrue(trie.values().contains("Lovely"));
    assertTrue(trie.values().remove("Lovely"));
    assertFalse(trie.values().remove("Lovely"));
    assertEquals(3, trie.values().size());
    Map.Entry<String, String> entry = trie.entrySet().iterator().next();
    assertTrue(trie.entrySet().contains(entry));
    assertEquals(3, trie.entrySet().size());
    trie.keySet().clear();
    assertTrue(trie.isEmpty());
    trie.put("x", "1");
    trie.values().clear();
    assertTrue(trie.isEmpty());
    trie.put("y", "2");
    trie.entrySet().clear();
    assertTrue(trie.isEmpty());
    assertFalse(trie.keySet().iterator().hasNext());
    Iterator<String> empty = trie.values().iterator();
    assertThrows(NoSuchElementException.class, empty::next);
  }

  @Test
  void entrySetValueWriteThrough() {
    PatriciaTrie<String, String> trie = lakeTrie();
    for (Map.Entry<String, String> e : trie.entrySet()) {
      e.setValue(e.getKey().toUpperCase());
    }
    assertEquals("LAX", trie.get("Lax"));
    Map.Entry<String, String> first = trie.firstEntry();
    assertEquals("Lake", first.getKey());
    Map.Entry<String, String> last = trie.lastEntry();
    assertEquals("Lovely", last.getKey());
    assertEquals("Lake", trie.firstKey());
    assertEquals("Lovely", trie.lastKey());
  }

  @Test
  void sortedViewsMatchTreeMapBounds() {
    PatriciaTrie<String, String> trie = lakeTrie();
    TreeMap<String, String> ref = new TreeMap<>(trie);
    assertEquals(ref.tailMap("Lime"), trie.tailMap("Lime"));
    assertEquals(ref.subMap("Lax", "Lovely"), trie.subMap("Lax", "Lovely"));
    SortedMap<String, String> sub = trie.subMap("Lax", "Lovely");
    assertEquals("Lax", sub.firstKey());
    assertEquals("LimeWire", sub.lastKey());
    assertEquals(4, sub.size());
    assertNull(sub.get("Lake"));
    assertFalse(sub.containsKey("Lovely"));
    assertEquals("Lax", sub.remove("Lax"));
    assertNull(sub.remove("Lake"));
    assertThrows(IllegalArgumentException.class, () -> sub.put("Aardvark", "x"));
    assertThrows(IllegalArgumentException.class, () -> trie.subMap("Lovely", "Lax"));
    assertThrows(IllegalArgumentException.class, () -> sub.headMap("Zzz"));
    assertThrows(IllegalArgumentException.class, () -> sub.tailMap("A"));
    assertThrows(IllegalArgumentException.class, () -> sub.subMap("A", "Lime"));
    assertEquals("Lime", sub.tailMap("Lime").firstKey());
    assertEquals("Lime", sub.headMap("LimeRadio").lastKey());
    assertNotNull(trie.comparator());
    assertTrue(trie.getKeyAnalyzer() instanceof CharSequenceKeyAnalyzer);
  }

  @Test
  void behavesLikeTreeMapUnderRandomOperations() {
    char[] alphabet = {'a', 'b', 'c', 'A', 'é', '中'};
    Random rnd = new Random(0xF205L);
    PatriciaTrie<String, Integer> trie = new PatriciaTrie<>(new CharSequenceKeyAnalyzer());
    TreeMap<String, Integer> ref = new TreeMap<>();
    for (int step = 0; step < 6000; step++) {
      String key = randomKey(rnd, alphabet, 1 + rnd.nextInt(6));
      int op = rnd.nextInt(10);
      if (op < 5) {
        assertEquals(ref.put(key, step), trie.put(key, step));
      } else if (op < 8) {
        assertEquals(ref.remove(key), trie.remove(key));
      } else if (op == 8) {
        assertEquals(ref.get(key), trie.get(key));
        assertEquals(ref.containsKey(key), trie.containsKey(key));
      } else {
        String prefix = randomKey(rnd, alphabet, 1 + rnd.nextInt(3));
        TreeMap<String, Integer> expected = new TreeMap<>();
        for (Map.Entry<String, Integer> e : ref.entrySet()) {
          if (e.getKey().startsWith(prefix)) expected.put(e.getKey(), e.getValue());
        }
        SortedMap<String, Integer> actual = trie.getPrefixedBy(prefix);
        assertEquals(expected, actual, "prefix " + prefix);
        assertEquals(expected.size(), actual.size());
      }
      assertEquals(ref.size(), trie.size());
      if (step % 50 == 0) {
        assertEquals(new ArrayList<>(ref.keySet()), new ArrayList<>(trie.keySet()));
        assertEquals(new ArrayList<>(ref.values()), new ArrayList<>(trie.values()));
        if (!ref.isEmpty()) {
          assertEquals(ref.firstKey(), trie.firstKey());
          assertEquals(ref.lastKey(), trie.lastKey());
          String lo = randomKey(rnd, alphabet, 2);
          String hi = lo + "zzzz";
          assertEquals(ref.tailMap(lo), trie.tailMap(lo));
          assertEquals(ref.subMap(lo, hi), trie.subMap(lo, hi));
        }
      }
    }
    assertEquals(ref, trie);
    // drain through the iterator
    for (Iterator<String> it = trie.keySet().iterator(); it.hasNext(); ) {
      it.next();
      it.remove();
    }
    assertTrue(trie.isEmpty());
  }

  @Test
  void selectReturnsTheEntryWithLongestSharedPrefixBits() {
    PatriciaTrie<String, String> trie = new PatriciaTrie<>(new CharSequenceKeyAnalyzer());
    trie.put("D", "D");
    trie.put("H", "H");
    trie.put("L", "L");
    // 'D' (1000100) XORs closer to 'L' (1001100) than to 'H' (1001000) in the low bits
    trie.remove("D");
    assertEquals("L", trie.select("D"));
  }

  private static String randomKey(Random rnd, char[] alphabet, int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) sb.append(alphabet[rnd.nextInt(alphabet.length)]);
    return sb.toString();
  }
}
