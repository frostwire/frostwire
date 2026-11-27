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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.CopyrightLicenseBroker;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The license picker of the share dialogs: which license each combination of choices makes. */
class CopyrightLicenseSelectorPanelTest {

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

  private static <T extends Component> void collect(Container parent, Class<T> type, List<T> out) {
    for (Component c : parent.getComponents()) {
      if (type.isInstance(c)) {
        out.add(type.cast(c));
      }
      if (c instanceof Container container) {
        collect(container, type, out);
      }
    }
  }

  private static <T extends Component> List<T> all(Container parent, Class<T> type) {
    List<T> found = new ArrayList<>();
    collect(parent, type, found);
    return found;
  }

  private static LicenseToggleButton toggle(Container panel, String title) {
    return all(panel, LicenseToggleButton.class).stream()
        .filter(b -> b.getTitle().equals(title))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no license button " + title));
  }

  private static JRadioButton radio(Container panel, String text) {
    return all(panel, JRadioButton.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no radio " + text));
  }

  private static void press(LicenseToggleButton button) {
    button.dispatchEvent(
        new MouseEvent(
            button, MouseEvent.MOUSE_PRESSED, 1L, 0, 1, 1, 1, false, MouseEvent.BUTTON1));
  }

  private static CopyrightLicenseSelectorPanel panel() throws Exception {
    CopyrightLicenseSelectorPanel[] holder = new CopyrightLicenseSelectorPanel[1];
    SwingUtilities.invokeAndWait(() -> holder[0] = new CopyrightLicenseSelectorPanel());
    return holder[0];
  }

  private interface Scenario {
    void run(CopyrightLicenseSelectorPanel panel) throws Exception;
  }

  private static void onEdt(CopyrightLicenseSelectorPanel p, Scenario s) throws Exception {
    Throwable[] failure = new Throwable[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            s.run(p);
          } catch (Throwable t) {
            failure[0] = t;
          }
        });
    if (failure[0] instanceof Error error) {
      throw error;
    }
    if (failure[0] != null) {
      throw new AssertionError(failure[0]);
    }
  }

  private static JCheckBox confirm(Container panel) {
    return all(panel, JCheckBox.class).get(0);
  }

  @Test
  void nothingIsChosenUntilTheRightfulUseIsConfirmed() throws Exception {
    CopyrightLicenseSelectorPanel p = panel();
    onEdt(
        p,
        panel -> {
          assertFalse(panel.hasConfirmedRightfulUseOfLicense());
          assertNull(panel.getLicenseBroker());
        });
  }

  @Test
  void confirmingStartsWithAttributionNonCommercial() throws Exception {
    CopyrightLicenseSelectorPanel p = panel();
    onEdt(
        p,
        panel -> {
          confirm(panel).doClick();
          assertTrue(panel.hasConfirmedRightfulUseOfLicense());
          assertTrue(toggle(panel, "NonCommercial").isSelected());
          assertFalse(toggle(panel, "NoDerivatives").isSelected());
          assertFalse(toggle(panel, "Share-Alike").isSelected());
        });
  }

  @Test
  void creativeCommonsOptionsExcludeEachOtherAndPickTheMatchingLicense() throws Exception {
    CopyrightLicenseSelectorPanel p = panel();
    onEdt(
        p,
        panel -> {
          confirm(panel).doClick();
          radio(panel, "Creative Commons").doClick();
          CopyrightLicenseBroker broker = panel.getLicenseBroker();
          assertNotNull(broker);
          assertTrue(broker.getLicenseName().contains("NonCommercial"), broker.getLicenseName());

          press(toggle(panel, "Share-Alike"));
          assertTrue(toggle(panel, "Share-Alike").isSelected());
          assertTrue(panel.getLicenseBroker().getLicenseName().contains("ShareAlike"));

          press(toggle(panel, "NoDerivatives"));
          assertTrue(toggle(panel, "NoDerivatives").isSelected());
          assertFalse(toggle(panel, "Share-Alike").isSelected(), "ND and SA exclude each other");
          assertTrue(panel.getLicenseBroker().getLicenseName().contains("NoDerivs"));

          press(toggle(panel, "Share-Alike"));
          assertFalse(toggle(panel, "NoDerivatives").isSelected());

          press(toggle(panel, "NonCommercial"));
          press(toggle(panel, "Share-Alike"));
          assertFalse(panel.getLicenseBroker().getLicenseName().contains("NonCommercial"));
        });
  }

  @Test
  void theOpenSourceLicensesAreChosenOneAtATime() throws Exception {
    CopyrightLicenseSelectorPanel p = panel();
    onEdt(
        p,
        panel -> {
          confirm(panel).doClick();
          radio(panel, "Open Source").doClick();
          assertTrue(toggle(panel, "Apache 2.0").isSelected(), "Apache is the first choice");
          assertTrue(panel.getLicenseBroker().getLicenseName().contains("Apache"));
          press(toggle(panel, "GPLv3"));
          assertTrue(toggle(panel, "GPLv3").isSelected());
          assertFalse(toggle(panel, "Apache 2.0").isSelected());
          assertTrue(panel.getLicenseBroker().getLicenseName().contains("GPL"));
          press(toggle(panel, "MIT"));
          assertFalse(toggle(panel, "GPLv3").isSelected());
          assertTrue(panel.getLicenseBroker().getLicenseName().contains("MIT"));
        });
  }

  @Test
  void thePublicDomainMarkAndCc0ExcludeEachOther() throws Exception {
    CopyrightLicenseSelectorPanel p = panel();
    onEdt(
        p,
        panel -> {
          confirm(panel).doClick();
          radio(panel, "Public Domain").doClick();
          assertTrue(toggle(panel, "Public Domain Mark 1.0").isSelected());
          assertNotNull(panel.getLicenseBroker());
          press(toggle(panel, "CC0 1.0"));
          assertTrue(toggle(panel, "CC0 1.0").isSelected());
          assertFalse(toggle(panel, "Public Domain Mark 1.0").isSelected());
          assertTrue(panel.getLicenseBroker().getLicenseName().contains("CC0"));
          press(toggle(panel, "Public Domain Mark 1.0"));
          assertFalse(toggle(panel, "CC0 1.0").isSelected());
        });
  }

  @Test
  void withdrawingTheConfirmationFreezesTheLicenseButtons() throws Exception {
    CopyrightLicenseSelectorPanel p = panel();
    onEdt(
        p,
        panel -> {
          JCheckBox confirm = confirm(panel);
          confirm.doClick();
          confirm.doClick();
          assertFalse(panel.hasConfirmedRightfulUseOfLicense());
          assertFalse(toggle(panel, "Apache 2.0").isSelected());
          assertFalse(toggle(panel, "CC0 1.0").isSelected());
          press(toggle(panel, "NoDerivatives"));
          assertFalse(toggle(panel, "NoDerivatives").isSelected(), "not toggleable any more");
        });
  }

  @Test
  void theToggleButtonKeepsItsTitleIconAndState() throws Exception {
    CopyrightLicenseSelectorPanel p = panel();
    onEdt(
        p,
        panel -> {
          LicenseToggleButton mit = toggle(panel, "MIT");
          assertEquals("MIT", mit.getTitle());
          assertEquals(LicenseToggleButton.LicenseIcon.OPENSOURCE, mit.getLicenseIcon());
          assertFalse(mit.isSelected());
          mit.setSelected(true);
          assertTrue(mit.isSelected());
          mit.dispatchEvent(new MouseEvent(mit, MouseEvent.MOUSE_ENTERED, 1L, 0, 1, 1, 0, false));
          mit.dispatchEvent(new MouseEvent(mit, MouseEvent.MOUSE_EXITED, 1L, 0, 1, 1, 0, false));
        });
  }
}
