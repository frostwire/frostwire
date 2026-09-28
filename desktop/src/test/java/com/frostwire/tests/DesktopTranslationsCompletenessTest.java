/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the desktop message catalogs: every string the UI asks for must exist in every language,
 * translated. Source strings are scanned from single-literal {@code tr("...")} calls.
 */
class DesktopTranslationsCompletenessTest {

  private static final Set<String> ENGLISH = Set.of("default", "en", "en_CA", "en_GB");
  private static final Pattern ENTRY =
      Pattern.compile(
          "^msgid \"((?:[^\"\\\\]|\\\\.)*)\"\\n((?:\"(?:[^\"\\\\]|\\\\.)*\"\\n)*)msgstr \"((?:[^\"\\\\]|\\\\.)*)\"\\n((?:\"(?:[^\"\\\\]|\\\\.)*\"\\n)*)",
          Pattern.MULTILINE);
  private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
  private static final Pattern TR_CALL =
      Pattern.compile("\\btr\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");

  private static Path desktop() {
    return Path.of(System.getProperty("user.dir"));
  }

  private static String join(String first, String continuation) {
    StringBuilder sb = new StringBuilder(first);
    Matcher m = LITERAL.matcher(continuation);
    while (m.find()) {
      sb.append(m.group(1));
    }
    return sb.toString();
  }

  /** msgid to msgstr, with multi-line values joined. */
  private static java.util.Map<String, String> load(Path po) throws IOException {
    String text = Files.readString(po, StandardCharsets.UTF_8);
    java.util.Map<String, String> out = new java.util.HashMap<>();
    Matcher m = ENTRY.matcher(text);
    while (m.find()) {
      out.put(join(m.group(1), m.group(2)), join(m.group(3), m.group(4)));
    }
    return out;
  }

  private static List<Path> catalogs() throws IOException {
    List<Path> out = new ArrayList<>();
    try (DirectoryStream<Path> files =
        Files.newDirectoryStream(desktop().resolve("lib/messagebundles"), "*.po")) {
      files.forEach(out::add);
    }
    return out;
  }

  private static Set<String> sourceStrings() throws IOException {
    Set<String> out = new TreeSet<>();
    try (Stream<Path> files = Files.walk(desktop().resolve("src/main/java"))) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        Matcher m = TR_CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (m.find()) {
          String s = m.group(1).replace("\\'", "'");
          if (!s.isBlank()) {
            out.add(s);
          }
        }
      }
    }
    return out;
  }

  @Test
  void everyStringTheUiUsesIsInEveryCatalogAndTranslated() throws Exception {
    Set<String> used = sourceStrings();
    assertTrue(used.size() > 500, "source scan found too little: " + used.size());
    List<String> problems = new ArrayList<>();
    for (Path po : catalogs()) {
      String lang = po.getFileName().toString().replace(".po", "");
      java.util.Map<String, String> catalog = load(po);
      for (String msgid : used) {
        String msgstr = catalog.get(msgid);
        if (msgstr == null) {
          problems.add(lang + " missing: " + msgid);
        } else if (!ENGLISH.contains(lang) && msgstr.isBlank()) {
          problems.add(lang + " untranslated: " + msgid);
        }
      }
    }
    assertTrue(
        problems.isEmpty(),
        problems.size() + " gaps, first: " + problems.subList(0, Math.min(10, problems.size())));
  }
}
