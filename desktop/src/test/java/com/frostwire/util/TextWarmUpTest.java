/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TextWarmUpTest {

  @Test
  void distinctFontsKeepsOneFontPerFamilyAndStyleAndSkipsOtherValues() {
    Map<Object, Object> defaults = new LinkedHashMap<>();
    defaults.put("Label.font", new Font(Font.DIALOG, Font.PLAIN, 12));
    defaults.put("Table.font", new Font(Font.DIALOG, Font.PLAIN, 13)); // same data, other size
    defaults.put("TitledBorder.font", new Font(Font.DIALOG, Font.BOLD, 12));
    defaults.put("TextArea.font", new Font(Font.MONOSPACED, Font.PLAIN, 12));
    defaults.put("Label.foreground", Color.BLACK);
    defaults.put("Button.margin", "not a font");

    List<Font> fonts = TextWarmUp.distinctFonts(defaults);

    assertEquals(3, fonts.size());
    assertTrue(fonts.stream().allMatch(f -> f.getSize() == 12));
  }

  @Test
  void distinctFontsOfEmptyDefaultsIsEmpty() {
    assertTrue(TextWarmUp.distinctFonts(Map.of()).isEmpty());
  }
}
