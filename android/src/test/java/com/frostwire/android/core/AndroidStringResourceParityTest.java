/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class AndroidStringResourceParityTest {

  // [^>]*? must be non-greedy: a greedy [^>]* swallows the '/' of a
  // self-closing "<string ... />" tag, the match then continues to the next
  // </string>, and every key after a self-closing string becomes invisible.
  private static final Pattern STRING_NAME_PATTERN =
      Pattern.compile(
          "<string\\s+name=\"([^\"]+)\"[^>]*?/>|<string\\s+name=\"([^\"]+)\"[^>]*?>.*?</string>",
          Pattern.DOTALL);

  @Test
  public void localizedStringFilesMatchBaseStringKeys() throws Exception {
    Path res = projectRoot().resolve("res");
    List<String> baseKeys = stringKeys(res.resolve("values/strings.xml"));
    Set<String> baseKeySet = new HashSet<>(baseKeys);
    List<String> errors = new ArrayList<>();

    try (java.util.stream.Stream<Path> paths = Files.list(res)) {
      paths
          .filter(path -> path.getFileName().toString().startsWith("values-"))
          .map(path -> path.resolve("strings.xml"))
          .filter(Files::exists)
          .forEach(
              path -> {
                try {
                  List<String> localizedKeys = stringKeys(path);
                  Set<String> localizedKeySet = new HashSet<>(localizedKeys);
                  if (localizedKeySet.size() != localizedKeys.size()) {
                    errors.add(path + " has duplicate string names");
                  }
                  for (String key : baseKeys) {
                    if (!localizedKeySet.contains(key)) {
                      errors.add(path + " missing " + key);
                    }
                  }
                  for (String key : localizedKeySet) {
                    if (!baseKeySet.contains(key)) {
                      errors.add(path + " has extra " + key);
                    }
                  }
                } catch (IOException e) {
                  errors.add(path + " failed to read: " + e.getMessage());
                }
              });
    }

    assertTrue(errors.toString(), errors.isEmpty());
  }

  @Test
  public void expectedLocaleCountDoesNotShrink() throws Exception {
    Path res = projectRoot().resolve("res");
    long count;
    try (java.util.stream.Stream<Path> paths = Files.list(res)) {
      count =
          paths
              .filter(path -> path.getFileName().toString().startsWith("values-"))
              .filter(path -> Files.exists(path.resolve("strings.xml")))
              .count();
    }
    assertEquals(37, count);
  }

  // Strings that are legitimately identical to English in some languages: a format string with no
  // words, and "N torrents" where the language uses the same word.
  private static final Set<String> MAY_MATCH_ENGLISH =
      new HashSet<>(
          java.util.Arrays.asList(
              "distributed_peers_row", "distributed_identity_shared_torrents", "use_bitsearch"));

  private static final Pattern STRING_VALUE_PATTERN =
      Pattern.compile("<string\\s+name=\"([^\"]+)\"([^>]*?)>(.*?)</string>", Pattern.DOTALL);

  @Test
  public void localizedStringsAreTranslatedNotEnglishCopies() throws Exception {
    Path res = projectRoot().resolve("res");
    java.util.Map<String, String> base = stringValues(res.resolve("values/strings.xml"));
    List<String> untranslated = new ArrayList<>();
    try (java.util.stream.Stream<Path> paths = Files.list(res)) {
      paths
          .filter(path -> path.getFileName().toString().startsWith("values-"))
          .map(path -> path.resolve("strings.xml"))
          .filter(Files::exists)
          .sorted()
          .forEach(
              path -> {
                try {
                  for (java.util.Map.Entry<String, String> e : stringValues(path).entrySet()) {
                    String english = base.get(e.getKey());
                    if (english != null
                        && english.equals(e.getValue())
                        && english.length() > 12
                        && english.matches(".*\\p{L}{3}.*")
                        && !MAY_MATCH_ENGLISH.contains(e.getKey())) {
                      untranslated.add(path.getParent().getFileName() + " " + e.getKey());
                    }
                  }
                } catch (IOException ex) {
                  untranslated.add(path + " failed to read: " + ex.getMessage());
                }
              });
    }
    assertTrue(
        untranslated.size() + " strings are still English copies: " + untranslated,
        untranslated.isEmpty());
  }

  private static java.util.Map<String, String> stringValues(Path path) throws IOException {
    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    java.util.Map<String, String> values = new java.util.HashMap<>();
    Matcher matcher = STRING_VALUE_PATTERN.matcher(source);
    while (matcher.find()) {
      if (!matcher.group(2).contains("translatable=\"false\"")) {
        values.put(matcher.group(1), matcher.group(3));
      }
    }
    return values;
  }

  private static List<String> stringKeys(Path path) throws IOException {
    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    Matcher matcher = STRING_NAME_PATTERN.matcher(source);
    List<String> keys = new ArrayList<>();
    while (matcher.find()) {
      String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      keys.add(key);
    }
    return keys;
  }

  private static Path projectRoot() {
    Path root = Path.of(System.getProperty("user.dir"));
    if (Files.exists(root.resolve("res/values/strings.xml"))) {
      return root;
    }
    return root.resolve("android");
  }
}
