/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.beans.BeanInfo;
import java.beans.DefaultPersistenceDelegate;
import java.beans.Introspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The vendored MigLayout registered XMLEncoder persistence delegates for UnitValue and BoundSize in
 * their static initializers, which ran java.beans.Introspector (its own class init plus a full
 * introspection of both classes) on the EDT when MainFrame built its first MigLayout (Icebase
 * #311ada154787). FrostWire never XML-encodes layout constraints, and the registration did not even
 * stick on current JDKs.
 */
class MigLayoutNoIntrospectionTest {
  private static final Path MIGLAYOUT =
      Path.of(System.getProperty("user.dir")).resolve("src/main/java/net/miginfocom");

  @Test
  void migLayoutDoesNotIntrospectBeans() throws Exception {
    List<Path> sources;
    try (Stream<Path> files = Files.walk(MIGLAYOUT)) {
      sources = files.filter(p -> p.toString().endsWith(".java")).toList();
    }
    for (Path source : sources) {
      String code = withoutComments(Files.readString(source)).replaceAll("\\s+", "");
      assertFalse(code.contains("Introspector."), source + " uses java.beans.Introspector");
      assertFalse(code.contains("setDelegate("), source + " registers a persistence delegate");
    }
  }

  /** Why the removed registration was dead code: this is exactly what MigLayout did. */
  @Test
  void delegateSetOnAnUncachedBeanInfoIsLost() throws Exception {
    Introspector.getBeanInfo(Fixture.class, Introspector.IGNORE_ALL_BEANINFO)
        .getBeanDescriptor()
        .setValue("persistenceDelegate", new DefaultPersistenceDelegate());

    BeanInfo seenByXmlEncoder = Introspector.getBeanInfo(Fixture.class);
    assertNull(seenByXmlEncoder.getBeanDescriptor().getValue("persistenceDelegate"));
  }

  private static String withoutComments(String code) {
    return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
  }

  public static final class Fixture {
    public int getValue() {
      return 1;
    }
  }
}
