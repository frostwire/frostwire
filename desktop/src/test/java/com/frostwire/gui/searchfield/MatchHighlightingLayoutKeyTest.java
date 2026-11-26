/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.searchfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

/**
 * Icebase #83d7ce9e0dc4 and #d5f23e9abcc5: the layout cache key was a record whose equals and
 * hashCode were linked through invokedynamic on the EDT as the first suggestion painted.
 */
public class MatchHighlightingLayoutKeyTest {
  @Test
  public void layoutKeyWritesOutItsOwnEqualsAndHashCode() throws IOException {
    String source =
        Files.readString(
                Path.of(
                    "src/main/java/com/frostwire/gui/searchfield/MatchHighlightingCellRenderer.java"))
            .replaceAll("\\s+", "");
    String key = source.substring(source.indexOf("privaterecordLayoutKey("));
    key = key.substring(0, key.indexOf("MatchHighlightingCellRenderer(ListCellRenderer"));
    assertTrue(key.contains("publicbooleanequals(Objectother)"), "equals is generated again");
    assertTrue(key.contains("publicinthashCode()"), "hashCode is generated again");
  }

  @Test
  public void equalKeysHashAlikeAndDifferentOnesDiffer() throws Exception {
    Class<?> type = Class.forName(MatchHighlightingCellRenderer.class.getName() + "$LayoutKey");
    Constructor<?> constructor =
        type.getDeclaredConstructor(
            String.class, BitSet.class, Font.class, FontRenderContext.class);
    constructor.setAccessible(true);
    Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
    FontRenderContext frc = new FontRenderContext(null, true, true);
    BitSet bold = new BitSet();
    bold.set(1);
    Object a = constructor.newInstance("frost", bold, font, frc);
    Object b = constructor.newInstance("frost", (BitSet) bold.clone(), font, frc);
    Object c = constructor.newInstance("wire", bold, font, frc);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
  }
}
