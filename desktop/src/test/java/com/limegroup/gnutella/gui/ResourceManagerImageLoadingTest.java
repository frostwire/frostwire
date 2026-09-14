/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.MissingResourceException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerImageLoadingTest {

    @Test
    void themePng_loadsBufferedImageBacked() {
        ImageIcon icon = ResourceManager.getThemeImage("search_result_play_over");

        assertNotNull(icon);
        assertTrue(icon.getImage() instanceof BufferedImage,
                "Static theme images must decode eagerly so icon paints are direct blits, got "
                        + icon.getImage().getClass());
    }

    @Test
    void themeGif_keepsDeferredLoadingForAnimation() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        ImageIcon icon = ResourceManager.getThemeImage("indeterminate_small_progress");

        assertNotNull(icon);
        assertFalse(icon.getImage() instanceof BufferedImage,
                "Animated GIFs must keep Toolkit loading so the spinner animation is preserved");
    }

    @Test
    void missingThemeImage_throws() {
        assertThrows(MissingResourceException.class,
                () -> ResourceManager.getThemeImage("no_such_image_xyz"));
    }
}
