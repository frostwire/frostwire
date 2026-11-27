/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Covers {@link StringTrie}, {@link StringTrieSet} and the small helpers in the package. */
final class StringTrieTest {

  private static List<String> drain(Iterator<String> it) {
    List<String> out = new ArrayList<>();
    while (it.hasNext()) out.add(it.next());
    Collections.sort(out);
    return out;
  }

  @Test
  void addGetRemoveAndOverwrite() {
    StringTrie<String> trie = new StringTrie<>(false);
    assertNull(trie.add("hello", "1"));
    assertNull(trie.add("help", "2"));
    assertNull(trie.add("he", "3"));
    assertNull(trie.add("", "root"));
    assertEquals("1", trie.get("hello"));
    assertEquals("2", trie.get("help"));
    assertEquals("3", trie.get("he"));
    assertEquals("root", trie.get(""));
    assertNull(trie.get("hel"));
    assertNull(trie.get("helpful"));
    assertNull(trie.get("zzz"));
    assertEquals("1", trie.add("hello", "1b"));
    assertEquals("1b", trie.get("hello"));
    assertEquals(4, drain(trie.getIterator()).size());
    assertTrue(trie.remove("hello"));
    assertFalse(trie.remove("hello"));
    assertFalse(trie.remove("nothing"));
    assertFalse(trie.remove("hel"));
    assertNull(trie.get("hello"));
    assertEquals(3, drain(trie.getIterator()).size());
    trie.clear();
    assertEquals(0, drain(trie.getIterator()).size());
    assertNull(trie.get("he"));
  }

  @Test
  void prefixIteration() {
    StringTrie<String> trie = new StringTrie<>(false);
    for (String s : new String[] {"lime", "limewire", "limeradio", "lax", "lake", "lovely"}) {
      trie.add(s, s);
    }
    assertEquals(Arrays.asList("lime", "limeradio", "limewire"), drain(trie.getPrefixedBy("lime")));
    assertEquals(Arrays.asList("lake", "lax"), drain(trie.getPrefixedBy("la")));
    assertEquals(Arrays.asList("limeradio"), drain(trie.getPrefixedBy("limer")));
    assertEquals(6, drain(trie.getPrefixedBy("")).size());
    assertFalse(trie.getPrefixedBy("x").hasNext());
    assertFalse(trie.getPrefixedBy("limx").hasNext());
    assertFalse(trie.getPrefixedBy("limewirex").hasNext());
    // substring variant
    assertEquals(Arrays.asList("lax", "lake"), sortedDesc(trie.getPrefixedBy("xxla", 2, 4)));
    assertThrows(NoSuchElementException.class, () -> trie.getPrefixedBy("x").next());
    Iterator<String> it = trie.getIterator();
    it.next();
    assertThrows(UnsupportedOperationException.class, it::remove);
  }

  private static List<String> sortedDesc(Iterator<String> it) {
    List<String> l = drain(it);
    Collections.reverse(l);
    return l;
  }

  @Test
  void ignoreCaseCanonicalises() {
    StringTrie<String> trie = new StringTrie<>(true);
    trie.add("FrostWire", "fw");
    assertEquals("fw", trie.get("frostwire"));
    assertEquals("fw", trie.get("FROSTWIRE"));
    assertEquals("frostwire", trie.canonicalCase("FrostWire"));
    assertEquals(Arrays.asList("fw"), drain(trie.getPrefixedBy("FROST")));
    assertTrue(trie.remove("FROSTwire"));
    assertEquals(0, drain(trie.getIterator()).size());
    StringTrie<String> exact = new StringTrie<>(false);
    assertEquals("FrostWire", exact.canonicalCase("FrostWire"));
    exact.add("FrostWire", "fw");
    assertNull(exact.get("frostwire"));
  }

  @Test
  void trimKeepsEveryValueReachable() {
    StringTrie<String> trie = new StringTrie<>(false);
    trie.add("a", "x");
    trie.add("ab", "y");
    trie.add("b", "z");
    trie.trim(v -> v);
    assertEquals("x", trie.get("a"));
    assertEquals("y", trie.get("ab"));
    assertEquals("z", trie.get("b"));
    trie.trim(null);
    trie.add("c", "w");
    assertEquals("w", trie.get("c"));
  }

  @Test
  void toStringShowsTreeStructure() {
    StringTrie<String> trie = new StringTrie<>(false);
    trie.add("ab", "1");
    trie.add("ac", "2");
    String s = trie.toString();
    assertTrue(s.startsWith("<root>"));
    assertTrue(s.contains("a"));
    assertTrue(s.contains(" -> 1"));
    assertTrue(s.contains(" -> 2"));
  }

  @Test
  void behavesLikeTreeMapUnderRandomOperations() {
    for (boolean ignoreCase : new boolean[] {false, true}) {
      Random rnd = new Random(77L);
      StringTrie<String> trie = new StringTrie<>(ignoreCase);
      TreeMap<String, String> ref = new TreeMap<>();
      String alphabet = "abAB";
      for (int step = 0; step < 4000; step++) {
        String key = randomKey(rnd, alphabet, rnd.nextInt(6));
        String canon = ignoreCase ? key.toLowerCase() : key;
        int op = rnd.nextInt(10);
        if (op < 5) {
          assertEquals(ref.put(canon, "v" + step), trie.add(key, "v" + step));
        } else if (op < 7) {
          assertEquals(ref.remove(canon) != null, trie.remove(key));
        } else if (op < 9) {
          assertEquals(ref.get(canon), trie.get(key));
        } else {
          String prefix = randomKey(rnd, alphabet, rnd.nextInt(3));
          String canonPrefix = ignoreCase ? prefix.toLowerCase() : prefix;
          List<String> expected = new ArrayList<>();
          for (Map.Entry<String, String> e : ref.entrySet()) {
            if (e.getKey().startsWith(canonPrefix)) expected.add(e.getValue());
          }
          Collections.sort(expected);
          assertEquals(expected, drain(trie.getPrefixedBy(prefix)), "prefix '" + prefix + "'");
        }
        if (step % 25 == 0) assertEquals(ref.size(), drain(trie.getIterator()).size());
      }
      List<String> all = new ArrayList<>(ref.values());
      Collections.sort(all);
      assertEquals(all, drain(trie.getIterator()));
    }
  }

  private static String randomKey(Random rnd, String alphabet, int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
    return sb.toString();
  }

  @Test
  void trieSetKeepsUniqueEntriesAndSupportsLookup() {
    StringTrieSet set = new StringTrieSet(true);
    set.addEntry("Ubuntu");
    set.addEntry("ubuntu");
    set.addEntry("Fedora");
    set.addEntry("Arch");
    assertEquals(Arrays.asList("arch", "fedora", "ubuntu"), toLower(drain(set.iterator())));
    assertEquals("fedora", set.lookup("fed").toLowerCase());
    assertNull(set.lookup("zzz"));
    assertEquals(Arrays.asList("ubuntu"), toLower(drain(set.iterator("UB"))));
    List<String> viaForEach = new ArrayList<>();
    for (String s : set) viaForEach.add(s);
    assertEquals(3, viaForEach.size());
    assertTrue(set.removeEntry("ARCH"));
    assertFalse(set.removeEntry("ARCH"));
    set.clear();
    assertFalse(set.iterator().hasNext());
    assertNull(set.lookup(""));
  }

  private static List<String> toLower(List<String> in) {
    List<String> out = new ArrayList<>();
    for (String s : in) out.add(s.toLowerCase());
    return out;
  }

  @Test
  void emptyIteratorHasNothingAndRejectsRemoval() {
    Iterator<String> it = EmptyIterator.emptyIterator();
    assertFalse(it.hasNext());
    assertThrows(NoSuchElementException.class, it::next);
    assertThrows(UnsupportedOperationException.class, it::remove);
  }

  @Test
  void comparatorsOrderLikeTheirJdkEquivalents() {
    assertTrue(new StringComparator().compare("a", "b") < 0);
    assertEquals(0, new StringComparator().compare("a", "a"));
    java.io.File a = new java.io.File("/tmp/a");
    java.io.File b = new java.io.File("/tmp/b");
    assertTrue(new FileComparator().compare(a, b) < 0);
    assertEquals(0, new FileComparator().compare(a, new java.io.File("/tmp/a")));
    assertEquals(0, Comparators.caseInsensitiveStringComparator().compare("AbC", "aBc"));
    assertTrue(Comparators.caseInsensitiveStringComparator().compare("a", "B") < 0);
  }

  @Test
  void forgetfulHashSetEvictsOldestEntry() {
    FixedsizeForgetfulHashSet<String> set = new FixedsizeForgetfulHashSet<>(3, 8);
    assertTrue(set.isEmpty());
    assertTrue(set.add("a"));
    assertFalse(set.add("a"));
    assertTrue(set.add("b"));
    assertTrue(set.add("c"));
    assertEquals(3, set.size());
    assertTrue(set.add("d"));
    assertEquals(3, set.size());
    assertFalse(set.contains("a"));
    assertTrue(set.contains("d"));
    List<String> order = new ArrayList<>(set);
    assertEquals(Arrays.asList("b", "c", "d"), order);
    FixedsizeForgetfulHashSet<String> copy = set.clone();
    assertTrue(set.remove("b"));
    assertFalse(set.remove("b"));
    assertTrue(copy.contains("b"));
    assertEquals(2, set.size());
    set.clear();
    assertTrue(set.isEmpty());
    assertEquals(3, copy.size());
  }

  @Test
  void forgetfulHashMapReportsFullnessAndRejectsBadSize() {
    FixedsizeForgetfulHashMap<String, Integer> map = new FixedsizeForgetfulHashMap<>(2);
    assertFalse(map.isFull());
    assertNull(map.put("a", 1));
    assertEquals(1, map.put("a", 2));
    map.put("b", 3);
    assertTrue(map.isFull());
    map.put("c", 4);
    assertEquals(2, map.size());
    assertFalse(map.containsKey("a") && map.containsKey("b") && map.containsKey("c"));
    assertEquals(4, map.get("c"));
    FixedsizeForgetfulHashMap<String, Integer> copy = map.clone();
    assertEquals(map, copy);
    assertThrows(
        IllegalArgumentException.class, () -> new FixedsizeForgetfulHashMap<String, String>(0));
  }

  @Test
  void keyAnalyzerBitMath() {
    CharSequenceKeyAnalyzer an = new CharSequenceKeyAnalyzer();
    assertEquals(0, an.length(null));
    assertEquals(48, an.length("abc"));
    assertEquals(16, an.bitsPerElement());
    assertTrue(an.isBitSet("a", 16, 9)); // 'a' = 0x0061 -> bit 9 (from MSB) is set
    assertFalse(an.isBitSet("a", 16, 0));
    assertFalse(an.isBitSet("a", 16, 99));
    assertFalse(an.isBitSet(null, 16, 0));
    assertEquals(PatriciaTrie.KeyAnalyzer.EQUAL_BIT_KEY, an.bitIndex("abc", 0, 48, "abc", 0, 48));
    assertEquals(PatriciaTrie.KeyAnalyzer.NULL_BIT_KEY, an.bitIndex("", 0, 0, null, 0, 0));
    assertEquals(16 + 15, an.bitIndex("ab", 0, 32, "ac", 0, 32));
    assertEquals(0 * 16 + 9, an.bitIndex("a", 0, 16, "", 0, 0));
    assertThrows(IllegalArgumentException.class, () -> an.bitIndex("a", 1, 16, "a", 0, 16));
    assertTrue(an.isPrefix("lime", 0, 32, "limewire"));
    assertFalse(an.isPrefix("lime", 0, 32, "lax"));
    assertTrue(an.isPrefix("xxli", 32, 64, "lime".substring(0, 2) + "me"));
    assertThrows(IllegalArgumentException.class, () -> an.isPrefix("a", 1, 16, "a"));
    assertTrue(an.compare("a", "b") < 0);
  }
}
