/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regression tests for confirmed StringTrie / StringTrieSet bugs. */
final class StringTrieRegressionTest {

  @Test
  void sizeOfNonEmptyTrieReturns() {
    StringTrie<String> trie = new StringTrie<>(false);
    trie.add("alpha", "a");
    trie.add("alphabet", "b");
    trie.add("beta", "c");
    int size = assertTimeoutPreemptively(Duration.ofSeconds(10), trie::size);
    assertEquals(3, size);
  }

  @Test
  void sizeOfEmptyTrieIsZero() {
    assertEquals(0, new StringTrie<String>(false).size());
  }

  /**
   * Production runs without -ea, but Gradle's test task enables assertions, so load a private copy
   * of the trie classes with assertions switched off.
   */
  @Test
  void insertsSurviveWithAssertionsDisabled() throws Exception {
    URL location = StringTrie.class.getProtectionDomain().getCodeSource().getLocation();
    try (URLClassLoader loader = new URLClassLoader(new URL[] {location}, null)) {
      loader.setDefaultAssertionStatus(false);
      Class<?> cls = loader.loadClass("org.limewire.collection.StringTrie");
      Object trie = cls.getConstructor(boolean.class).newInstance(false);
      java.lang.reflect.Method add = cls.getMethod("add", String.class, Object.class);
      java.lang.reflect.Method get = cls.getMethod("get", String.class);
      String[] words = {"banana", "apple", "cherry", "apricot", "blueberry", "avocado"};
      for (String w : words) add.invoke(trie, w, w.toUpperCase());
      List<String> got = new ArrayList<>();
      for (String w : words) got.add((String) get.invoke(trie, w));
      List<String> expected = new ArrayList<>();
      for (String w : words) expected.add(w.toUpperCase());
      assertEquals(expected, got);
    }
  }

  @Test
  void trimAppliesCompactorToEveryNode() {
    StringTrie<String> trie = new StringTrie<>(false);
    String[] words = {"", "he", "hello", "help", "world", "wore"};
    for (String w : words) trie.add(w, w);
    trie.trim(v -> v + "!");
    for (String w : words) assertEquals(w + "!", trie.get(w), "value of '" + w + "'");
  }

  @Test
  void stringTrieSetIgnoreCaseFlagControlsCaseSensitivity() {
    StringTrieSet insensitive = new StringTrieSet(true);
    insensitive.addEntry("Hello");
    assertEquals("Hello", insensitive.lookup("he"));
    StringTrieSet sensitive = new StringTrieSet(false);
    sensitive.addEntry("Hello");
    assertNull(sensitive.lookup("he"));
    assertEquals("Hello", sensitive.lookup("He"));
  }
}
