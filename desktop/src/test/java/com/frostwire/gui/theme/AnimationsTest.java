/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.util.Animator;
import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Lite turns the UI's animations off, Full keeps them (frostwire/frostwire#1555). */
class AnimationsTest {
  @AfterEach
  void backToFull() {
    PerformanceMode.resolve(Choice.FULL, null);
    Animations.applyToLookAndFeel();
  }

  @Test
  void liteTurnsFlatLafAnimationsAndSmoothScrollingOff() {
    PerformanceMode.resolve(Choice.LITE, null);
    Animations.applyToLookAndFeel();
    assertFalse(Animations.enabled());
    assertFalse(Animator.useAnimation(), "FlatLaf still animates");
    assertEquals(Boolean.FALSE, UIManager.get(Animations.SMOOTH_SCROLLING));
  }

  @Test
  void fullGivesThemBackToTheLookAndFeel() {
    PerformanceMode.resolve(Choice.LITE, null);
    Animations.applyToLookAndFeel();
    PerformanceMode.resolve(Choice.FULL, null);
    Animations.applyToLookAndFeel();
    assertTrue(Animations.enabled());
    assertTrue(Animator.useAnimation(), "FlatLaf animations stay off in Full");
    assertNull(System.getProperty(Animations.FLATLAF_ANIMATION));
  }

  @Test
  void theUpdateButtonAndTheSlideshowOnlyAnimateWhenAnimationsAreOn() throws Exception {
    String header = source("com/limegroup/gnutella/gui/ApplicationHeader.java");
    assertTrue(
        header.contains(
            "if(updateButtonAnimationStartedTimestamp==-1&&Animations.enabled()){startUpdateButtonIntermittentAnimation();"),
        "the update button blinks in Lite");
    String slideshow = source("com/frostwire/gui/components/slides/MultimediaSlideshowPanel.java");
    assertTrue(
        slideshow.contains(
            "if(!slides1.isEmpty()&&Animations.enabled()){timer=newTimer(\"SlideShowTimer\");"),
        "the slideshow rotates in Lite");
    String theme = source("com/frostwire/gui/theme/ThemeMediator.java");
    int startup = theme.indexOf("publicstaticvoidloadThemeAtStartup(");
    assertTrue(
        theme.indexOf("Animations.applyToLookAndFeel();", startup) > startup,
        "the startup look and feel ignores Lite");
  }

  private static String source(String path) throws Exception {
    return Files.readString(
            Path.of(System.getProperty("user.dir")).resolve("src/main/java").resolve(path))
        .replaceAll("\\s+", "");
  }
}
