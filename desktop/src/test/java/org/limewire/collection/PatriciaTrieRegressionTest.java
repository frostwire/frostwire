/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Regression tests for confirmed PatriciaTrie bugs. */
final class PatriciaTrieRegressionTest {

  private static PatriciaTrie<String, String> lakeTrie() {
    PatriciaTrie<String, String> trie = new PatriciaTrie<>(new CharSequenceKeyAnalyzer());
    for (String s : new String[] {"Lime", "LimeWire", "LimeRadio", "Lax", "Lake", "Lovely"}) {
      trie.put(s, s);
    }
    return trie;
  }

  @Test
  void prefixViewFirstAndLastKeyDoNotOverflowTheStack() {
    PatriciaTrie<String, String> trie = lakeTrie();
    assertEquals("Lake", trie.getPrefixedBy("La").firstKey());
    assertEquals("Lax", trie.getPrefixedBy("La").lastKey());
  }

  @Test
  void headMapEntrySetAndEqualsDoNotOverflowTheStack() {
    PatriciaTrie<String, String> trie = lakeTrie();
    TreeMap<String, String> ref = new TreeMap<>(trie);
    Map<String, String> head = trie.headMap("Lime");
    assertEquals(ref.headMap("Lime"), head);
    List<String> keys = new ArrayList<>();
    for (Map.Entry<String, String> e : head.entrySet()) keys.add(e.getKey());
    assertEquals(new ArrayList<>(ref.headMap("Lime").keySet()), keys);
  }

  @Test
  void subMapFirstAndLastKeyWithNullBound() {
    PatriciaTrie<String, String> trie = lakeTrie();
    assertEquals("Lovely", trie.tailMap("Lime").lastKey());
    assertEquals("Lake", trie.headMap("Lime").firstKey());
    assertTrue(trie.headMap("Lime").lastKey().compareTo("Lime") < 0);
  }

  @Test
  void entrySetRemoveRemovesByKeyAndOnlyWhenValueMatches() {
    PatriciaTrie<String, String> trie = lakeTrie();
    int size = trie.size();
    assertFalse(trie.entrySet().remove(new AbstractMap.SimpleEntry<>("Lake", "other")));
    assertEquals(size, trie.size());
    assertTrue(trie.entrySet().remove(new AbstractMap.SimpleEntry<>("Lake", "Lake")));
    assertEquals(size - 1, trie.size());
    assertFalse(trie.containsKey("Lake"));
    assertFalse(trie.entrySet().remove(new AbstractMap.SimpleEntry<>("Lake", "Lake")));
    assertFalse(trie.entrySet().remove("not an entry"));
    assertEquals(size - 1, trie.size());
  }
}
