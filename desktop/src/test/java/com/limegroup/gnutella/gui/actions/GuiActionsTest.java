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

package com.limegroup.gnutella.gui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.UISettings;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.limewire.setting.BooleanSetting;

/** The menu and toolbar actions: names and mnemonics, toggles, links, and file menu behaviour. */
class GuiActionsTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  private static final class Named extends AbstractAction {
    Named(String name) {
      super(name);
    }

    @Override
    public void actionPerformed(ActionEvent e) {}
  }

  @Test
  void anAmpersandInTheNameBecomesTheMnemonic() {
    Named save = new Named("&Save");
    assertEquals("Save", save.getValue(Action.NAME));
    assertEquals(KeyEvent.VK_S, save.getValue(Action.MNEMONIC_KEY));
    Named middle = new Named("Save &As");
    assertEquals("Save As", middle.getValue(Action.NAME));
    assertEquals(KeyEvent.VK_A, middle.getValue(Action.MNEMONIC_KEY));
  }

  @Test
  void aNameWithoutAnAmpersandHasNoMnemonic() {
    Named plain = new Named("Plain name");
    assertEquals("Plain name", plain.getValue(Action.NAME));
    assertNull(plain.getValue(Action.MNEMONIC_KEY));
  }

  @Test
  void otherValuesAreStoredAsTheyAre() {
    Named action = new Named("&X");
    action.putValue(Action.LONG_DESCRIPTION, "a & b");
    assertEquals("a & b", action.getValue(Action.LONG_DESCRIPTION));
  }

  private static final class Toggle extends ToggleSettingAction {
    Toggle(BooleanSetting setting) {
      super(setting, "&Toggle", "Toggles it");
    }
  }

  @Test
  void aToggleActionWritesTheButtonStateIntoItsSetting() {
    BooleanSetting setting = UISettings.TEXT_WITH_ICONS;
    boolean original = setting.getValue();
    try {
      Toggle toggle = new Toggle(setting);
      assertSame(setting, toggle.getSetting());
      assertEquals("Toggles it", toggle.getValue(Action.LONG_DESCRIPTION));
      JCheckBox box = new JCheckBox();
      box.setSelected(!original);
      toggle.actionPerformed(new ActionEvent(box, ActionEvent.ACTION_PERFORMED, "x"));
      assertEquals(!original, setting.getValue());
      box.setSelected(original);
      toggle.actionPerformed(new ActionEvent(box, ActionEvent.ACTION_PERFORMED, "x"));
      assertEquals(original, setting.getValue());
    } finally {
      setting.setValue(original);
    }
  }

  @Test
  void aToggleActionOnlyWorksWithButtons() {
    Toggle toggle = new Toggle(UISettings.TEXT_WITH_ICONS);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            toggle.actionPerformed(
                new ActionEvent(new JLabel(), ActionEvent.ACTION_PERFORMED, "")));
  }

  @Test
  void aLinkActionHasANameAndADescription() {
    OpenLinkAction action = new OpenLinkAction("https://www.frostwire.com", "&Website", "Opens it");
    assertEquals("Website", action.getValue(Action.NAME));
    assertEquals("Opens it", action.getValue(Action.LONG_DESCRIPTION));
    assertEquals(KeyEvent.VK_W, action.getValue(Action.MNEMONIC_KEY));
    OpenLinkAction noDescription = new OpenLinkAction("https://www.frostwire.com", "Site");
    assertNull(noDescription.getValue(Action.LONG_DESCRIPTION));
  }

  @Test
  void aSearchActionRefusesAnEmptyQuery() {
    assertThrows(IllegalArgumentException.class, () -> new SearchAction(""));
  }

  @Test
  void aSearchActionIsNamedAfterItsKeywords() {
    SearchAction action = new SearchAction("creative commons");
    assertNotNull(action.getValue(Action.NAME));
    assertTrue(action.getValue(Action.NAME).toString().contains("creative commons"));
  }

  @Test
  void theOpenDialogSearchesTheCloudOnlyForAcceptableAddresses() {
    assertNull(FileMenuActions.cloudUrlSearchAction(""));
    assertNull(FileMenuActions.cloudUrlSearchAction("x".repeat(5000)));
    SearchAction ok = FileMenuActions.cloudUrlSearchAction("https://example.com/{braces}/'q'");
    assertNotNull(ok);
    assertTrue(ok.getValue(Action.NAME).toString().contains("{braces}"));
  }

  @Test
  void textThatIsNotAMagnetLinkOrTorrentIsNotOpened() {
    assertFalse(FileMenuActions.openMagnetOrTorrent("just some words"));
    assertFalse(FileMenuActions.openMagnetOrTorrent("relative/path.torrent"));
    assertFalse(FileMenuActions.openMagnetOrTorrent("/no/such/folder/missing.torrent"));
    assertFalse(FileMenuActions.openMagnetOrTorrent(""));
  }

  @Test
  void theFileMenuActionsAreNamedWithMnemonics() {
    assertEquals("Close", new FileMenuActions.CloseAction().getValue(Action.NAME));
    assertEquals(KeyEvent.VK_C, new FileMenuActions.CloseAction().getValue(Action.MNEMONIC_KEY));
    assertEquals("Exit", new FileMenuActions.ExitAction().getValue(Action.NAME));
    assertEquals(KeyEvent.VK_X, new FileMenuActions.ExitAction().getValue(Action.MNEMONIC_KEY));
    assertEquals(
        "Create New Torrent", new FileMenuActions.CreateTorrentAction().getValue(Action.NAME));
    assertEquals(
        "Send File or Folder...", new FileMenuActions.SendFileAction().getValue(Action.NAME));
    assertNotNull(new FileMenuActions.OpenMagnetTorrentVideoUrlAction().getValue(Action.NAME));
  }
}
