/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Icebase #8c5e4ef59924: LibraryFilesTableMediator.instance() was a plain lazy singleton reached
 * from the EDT and from background threads (Library rows, searches); two threads each built a
 * mediator, and the second one's table settings threw "duplicate setting key". A mediator builds
 * its table settings from fixed ids, so it must be built once: the accessor has to be synchronized.
 */
public class MediatorSingletonsTest {
  private static final Pattern LAZY_ACCESSOR =
      Pattern.compile(
          "public static (synchronized )?\\w+ (instance|getInstance)\\(\\) \\{\\s*if \\(INSTANCE == null\\)");

  @Test
  public void lazyMediatorSingletonsAreSynchronized() throws IOException {
    List<String> unsafe = new ArrayList<>();
    int lazy = 0;
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path file :
          (Iterable<Path>) files.filter(f -> f.toString().endsWith("Mediator.java"))::iterator) {
        Matcher m = LAZY_ACCESSOR.matcher(Files.readString(file));
        while (m.find()) {
          lazy++;
          if (m.group(1) == null) {
            unsafe.add(file.getFileName().toString());
          }
        }
      }
    }
    assertTrue(lazy >= 5, "found only " + lazy + " lazy mediator singletons: the pattern is stale");
    assertEquals(List.of(), unsafe, "lazy mediator singletons that are not synchronized");
  }
}
