/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.init.SeedingPublishingText;
import com.limegroup.gnutella.gui.init.SetupWizardOverride;
import com.limegroup.gnutella.gui.init.WrappedText;
import java.util.List;
import org.junit.jupiter.api.Test;

class SetupWizardOverrideTest {

  @Test
  void wizardIsNotForcedByDefault() {
    assertFalse(SetupWizardOverride.isForced(null, null));
  }

  @Test
  void propertyOrEnvironmentForcesTheWizard() {
    assertTrue(SetupWizardOverride.isForced("true", null));
    assertTrue(SetupWizardOverride.isForced(null, "1"));
    assertTrue(SetupWizardOverride.isForced("true", "0"));
  }

  @Test
  void flagGivenWithoutAValueCountsAsOn() {
    assertTrue(SetupWizardOverride.isForced("", null));
  }

  @Test
  void explicitOffValuesDisableIt() {
    assertFalse(SetupWizardOverride.isForced("false", "0"));
    assertFalse(SetupWizardOverride.isForced(" FALSE ", "no"));
  }

  @Test
  void seedingPageExplainsDistributedSearchSharing() {
    String html = String.join(" ", SeedingPublishingText.distributedSearchPoints());
    assertTrue(html.contains("Distributed Search"), "must name the feature");
    assertTrue(html.contains("seeding or downloading"), "must say what is shared");
    assertTrue(html.contains("never published"), "must say what is not shared");
    assertTrue(html.contains("Tools > Options > IceBridge"), "must say where to change it later");
  }

  @Test
  void wizardTextWrapsWithinTheWidthAndNeverOverflows() {
    // 1 pixel per character keeps the arithmetic obvious.
    List<String> lines = WrappedText.wrap("aaaa bbbb cccc dddd", String::length, 9);
    assertEquals(List.of("aaaa bbbb", "cccc dddd"), lines);
    assertEquals(
        List.of("abcdefgh", "ijkl"),
        WrappedText.wrap("abcdefghijkl", String::length, 8),
        "an over-long word is broken by character");
    for (String line : WrappedText.wrap("one two three four five six seven", String::length, 12)) {
      assertTrue(line.length() <= 12);
    }
    assertEquals(
        "<html>a &lt;b&gt; &amp; c<br>d</html>", WrappedText.html(List.of("a <b> & c", "d")));
  }
}
