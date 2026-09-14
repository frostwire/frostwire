/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.limegroup.gnutella.gui.Main;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontSmoothingTest {

    @Test
    void grayscaleFontHints_requestsGrayscaleAntialiasing() {
        Map<Object, Object> hints = Main.grayscaleFontHints(null);

        assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                hints.get(RenderingHints.KEY_TEXT_ANTIALIASING),
                "Swing text must render grayscale, not subpixel-LCD");
    }

    @Test
    void grayscaleFontHints_preservesOtherDesktopHints() {
        Map<Object, Object> current = new HashMap<>();
        current.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        current.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        Map<Object, Object> hints = Main.grayscaleFontHints(current);

        assertNotSame(current, hints);
        assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                hints.get(RenderingHints.KEY_TEXT_ANTIALIASING));
        assertEquals(RenderingHints.VALUE_FRACTIONALMETRICS_ON,
                hints.get(RenderingHints.KEY_FRACTIONALMETRICS));
        assertTrue(current.get(RenderingHints.KEY_TEXT_ANTIALIASING)
                == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, "Input map must not be mutated");
    }
}
