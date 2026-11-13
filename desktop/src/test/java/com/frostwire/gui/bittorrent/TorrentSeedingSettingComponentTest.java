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

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The setup wizard's BitTorrent page froze while building these radio buttons: their text was HTML,
 * so the EDT built Swing's HTML view and its composite font while the startup warm-up loaded font
 * files under the JDK's global font lock (Icebase #7d83b84c37c5, frostwire/frostwire#1544). The
 * radio buttons are plain text now, and the page still shows each choice's explanation.
 */
class TorrentSeedingSettingComponentTest {

  @Test
  void theRadioButtonsArePlainTextAndEachExplanationShowsBelowItsChoice() throws Exception {
    AtomicReference<TorrentSeedingSettingComponent> built = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> built.set(new TorrentSeedingSettingComponent(false, false)));
    TorrentSeedingSettingComponent component = built.get();

    for (JRadioButton radio :
        List.of(component.seedingRadioButton(), component.notSeedingRadioButton())) {
      assertFalse(
          radio.getText().toLowerCase(Locale.ROOT).startsWith("<html>"),
          "HTML radio button: " + radio.getText());
      assertFalse(radio.getText().contains("<"), "markup in " + radio.getText());
      assertTrue(radio.getClientProperty("html") == null, "Swing built an HTML view for it");
    }
    assertEquals("Seed finished downloads.", component.seedingRadioButton().getText());
    assertEquals("Don't seed finished downloads.", component.notSeedingRadioButton().getText());

    assertExplanationFollows(
        component,
        component.seedingRadioButton(),
        component.seedingDescription(),
        "will be able to download file chunks of the data your torrents seed. (Recommended)");
    assertExplanationFollows(
        component,
        component.notSeedingRadioButton(),
        component.notSeedingDescription(),
        "Some trackers will penalize this Leeching behavior.");
  }

  @Test
  void clickingAnExplanationPicksItsChoice() throws Exception {
    AtomicReference<TorrentSeedingSettingComponent> built = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> built.set(new TorrentSeedingSettingComponent(false, false)));
    TorrentSeedingSettingComponent component = built.get();
    assertFalse(component.hasOneBeenSelected());

    SwingUtilities.invokeAndWait(
        () -> {
          JTextArea description = component.notSeedingDescription();
          description.dispatchEvent(
              new java.awt.event.MouseEvent(
                  description,
                  java.awt.event.MouseEvent.MOUSE_CLICKED,
                  System.currentTimeMillis(),
                  0,
                  1,
                  1,
                  1,
                  false));
        });

    assertTrue(component.hasOneBeenSelected());
    assertFalse(component.wantsSeeding());
  }

  private static void assertExplanationFollows(
      TorrentSeedingSettingComponent component,
      JRadioButton radio,
      JTextArea description,
      String text) {
    assertTrue(description.getText().endsWith(text), description.getText());
    assertFalse(description.getText().contains("<"), "markup in " + description.getText());
    assertFalse(description.isEditable());
    assertTrue(description.getLineWrap(), "the explanation does not wrap");
    List<Component> children = List.of(component.getComponents());
    assertSame(
        description,
        children.get(children.indexOf(radio) + 1),
        "the explanation is not right below its radio button");
  }
}
