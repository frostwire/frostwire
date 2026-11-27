/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;
import java.util.Locale;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The flags beside the languages in the language window. */
class LanguageFlagFactoryTest {

  private static LookAndFeel previous;

  @BeforeAll
  static void metal() throws Exception {
    System.setProperty("java.awt.headless", "true");
    previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new MetalLookAndFeel());
  }

  @AfterAll
  static void restore() throws Exception {
    UIManager.setLookAndFeel(previous);
  }

  @Test
  void aKnownCountryOrLanguageGetsAFlagAndAnUnknownOneAGlobe() {
    ImageIcon unitedStates = LanguageFlagFactory.getFlag("US", "en", false);
    assertNotNull(unitedStates);
    assertSame(unitedStates, LanguageFlagFactory.getFlag("us", "xx", false), "any letter case");
    assertSame(unitedStates, LanguageFlagFactory.getFlag("", "en", false), "language fallback");
    assertSame(unitedStates, LanguageFlagFactory.getFlag(null, "en", false));
    ImageIcon bigGlobe = LanguageFlagFactory.getFlag(null, "zz", false);
    ImageIcon smallGlobe = LanguageFlagFactory.getFlag(null, "zz", true);
    assertNotNull(bigGlobe);
    assertNotNull(smallGlobe);
    assertSame(bigGlobe, LanguageFlagFactory.getFlag("QQ", "zz", false));
  }

  @Test
  void flagsCanBePreloadedForTheLocalesOfTheLanguageWindow() {
    LanguageFlagFactory.preloadFlags(new Locale[] {Locale.US, Locale.FRANCE, Locale.GERMAN});
    LanguageFlagFactory.preloadFlags(new Locale[0]);
  }

  @Test
  void theListRendererShowsTheLanguageNameInItsOwnWordsWithAFlag() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ListCellRenderer<Object> renderer = LanguageFlagFactory.getListRenderer();
          JList<Object> list = new JList<>();
          Component shown =
              renderer.getListCellRendererComponent(list, Locale.FRENCH, 0, false, false);
          JLabel label = (JLabel) shown;
          assertEquals("Français", label.getText());
          assertNotNull(label.getIcon());
          assertEquals(10, label.getIconTextGap());
          Component other =
              renderer.getListCellRendererComponent(list, "not a locale", 0, false, false);
          assertNull(((JLabel) other).getIcon());
        });
  }
}
