/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The form in which search keywords are compared with the names of search results and library
 * files: accents removed and lower case, so "canción" finds "CANCION".
 *
 * <p>The search box and the Library search each did this with {@code
 * String.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")}, which compiles the regex on every
 * call, for every keyword and every result, on the EDT. The first compile of a Unicode block also
 * initializes {@code Character.UnicodeBlock}, several hundred objects: on a slow machine the first
 * search froze there (Icebase #914536b5767d, frostwire/frostwire#1521). The pattern is compiled
 * once here, and {@link JdkWarmUp} runs it at startup, off the EDT.
 */
public final class SearchText {

  private static final Pattern COMBINING_MARKS =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private SearchText() {}

  /** {@code text} decomposed (NFKD), without combining accents, in lower case. */
  public static String normalize(String text) {
    String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
    return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.US);
  }
}
