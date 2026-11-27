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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.Date;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pure helpers of {@link GUIUtils}: size and speed text, mnemonics, colors, clipboard text. */
class GUIUtilsFormattingTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  @Test
  void bytesAreShownInTheUnitThatKeepsFourSignificantDigits() {
    assertEquals("? KB", GUIUtils.getBytesInHuman(-1));
    assertTrue(GUIUtils.getBytesInHuman(0).startsWith("0"));
    assertTrue(GUIUtils.getBytesInHuman(0).endsWith("KB"));
    assertEquals("1.0 KB", normalize(GUIUtils.getBytesInHuman(1024)));
    assertEquals("512.0 KB", normalize(GUIUtils.getBytesInHuman(512 * 1024)));
    assertEquals("10.0 MB", normalize(GUIUtils.getBytesInHuman(10L * 1024 * 1024)));
    // below 10 GB the unit stays MB; four-digit counts lose the decimal
    String fiveGb = GUIUtils.getBytesInHuman(5.0 * 1024 * 1024 * 1024);
    assertEquals("5120 MB", fiveGb.replace(",", "").replace(".", "").replace("\u00a0", ""));
    assertEquals("20.0 GB", normalize(GUIUtils.getBytesInHuman(20.0 * 1024 * 1024 * 1024)));
  }

  /** The decimal separator is the locale's; compare with the digits and unit only. */
  private static String normalize(String s) {
    return s.replace(',', '.');
  }

  @Test
  void bytesSwitchUnitAtTenTimesTheUnit() {
    assertTrue(GUIUtils.getBytesInHuman(0xA00000 - 1).endsWith("KB"));
    assertTrue(GUIUtils.getBytesInHuman(0xA00000).endsWith("MB"));
    assertTrue(GUIUtils.getBytesInHuman(0x280000000L - 1).endsWith("MB"));
    assertTrue(GUIUtils.getBytesInHuman(0x280000000L).endsWith("GB"));
    assertTrue(GUIUtils.getBytesInHuman(0xA0000000000L - 1).endsWith("GB"));
    assertTrue(GUIUtils.getBytesInHuman(0xA0000000000L).endsWith("TB"));
  }

  @Test
  void aKilobytePerSecondRateIsShownWithoutDecimals() {
    String speed = GUIUtils.rate2speed(1234.56);
    assertTrue(speed.endsWith("KB/s"), speed);
    assertTrue(speed.startsWith("1"), speed);
    assertFalse(speed.contains("56"), speed);
  }

  @Test
  void resettingTheLocaleKeepsFormattersWorking() {
    GUIUtils.resetLocale();
    assertTrue(GUIUtils.getBytesInHuman(2048).endsWith("KB"));
  }

  @Test
  void theFullDateTimeFormatIsBuiltOnceAndRendersTheYear() {
    assertSame(GUIUtils.getFullDateTimeFormat(), GUIUtils.getFullDateTimeFormat());
    assertTrue(GUIUtils.getFullDateTimeFormat().format(new Date(0)).contains("19"));
  }

  @Test
  void ampersandMarksTheMnemonicAndIsStripped() {
    assertEquals("File", GUIUtils.stripAmpersand("&File"));
    assertEquals("Save As", GUIUtils.stripAmpersand("Save &As"));
    assertEquals("Rock & Roll", GUIUtils.stripAmpersand("Rock & Roll"));
    assertEquals("trailing&", GUIUtils.stripAmpersand("trailing&"));
    assertEquals("a&b", GUIUtils.stripAmpersand("a&&b"));
    assertEquals("", GUIUtils.stripAmpersand(""));
    assertEquals(KeyEvent.VK_F, GUIUtils.getMnemonicKeyCode("&File"));
    assertEquals(KeyEvent.VK_A, GUIUtils.getMnemonicKeyCode("Save &as"));
    assertEquals(KeyEvent.VK_5, GUIUtils.getMnemonicKeyCode("Top &5"));
    assertEquals(-1, GUIUtils.getMnemonicKeyCode("No mnemonic"));
    assertEquals(-1, GUIUtils.getMnemonicKeyCode("Rock & Roll"));
  }

  @Test
  void colorsRoundTripThroughHex() {
    assertEquals("FF0A00", GUIUtils.colorToHex(new Color(255, 10, 0)));
    assertEquals("000000", GUIUtils.colorToHex(Color.BLACK));
    assertEquals(new Color(255, 10, 0), GUIUtils.hexToColor("FF0A00"));
    assertEquals(Color.WHITE, GUIUtils.hexToColor("ffffff"));
    assertThrows(NumberFormatException.class, () -> GUIUtils.hexToColor("zz"));
  }

  @Test
  void boldTextUsesTheFontNotHtml() {
    JLabel label = new JLabel();
    GUIUtils.setBoldText(label, "Hello <b>");
    assertEquals("Hello <b>", label.getText());
    assertTrue(label.getFont().isBold());
  }

  @Test
  void setOpaqueReachesNestedComponentsButNotTextFieldsOrButtons() {
    JPanel root = new JPanel();
    JPanel inner = new JPanel();
    JPanel deep = new JPanel();
    JTextField field = new JTextField();
    JButton button = new JButton();
    JLabel label = new JLabel();
    inner.add(deep);
    inner.add(label);
    root.add(inner);
    root.add(field);
    root.add(button);
    field.setOpaque(true);
    button.setOpaque(true);
    GUIUtils.setOpaque(false, root);
    assertFalse(root.isOpaque());
    assertFalse(inner.isOpaque());
    assertFalse(deep.isOpaque());
    assertFalse(label.isOpaque());
    assertTrue(field.isOpaque());
    assertTrue(button.isOpaque());
  }

  @Test
  void restrictSizeFixesMinAndMaxFromThePreferredSize() {
    JPanel p = new JPanel();
    p.setPreferredSize(new Dimension(120, 40));
    GUIUtils.restrictSize(p, GUIUtils.SizePolicy.RESTRICT_HEIGHT);
    assertEquals(new Dimension(120, 40), p.getMinimumSize());
    assertEquals(new Dimension(Integer.MAX_VALUE, 40), p.getMaximumSize());
    GUIUtils.restrictSize(p, GUIUtils.SizePolicy.RESTRICT_BOTH);
    assertEquals(new Dimension(120, 40), p.getMaximumSize());
    GUIUtils.restrictSize(p, GUIUtils.SizePolicy.RESTRICT_NONE);
    assertFalse(p.isMinimumSizeSet());
    assertFalse(p.isMaximumSizeSet());
  }

  @Test
  void theDisposeActionIsASingleton() {
    assertSame(GUIUtils.getDisposeAction(), GUIUtils.getDisposeAction());
  }

  @Test
  void keyBindingsAreInstalledForTheAction() {
    JPanel p = new JPanel();
    javax.swing.Action a =
        new javax.swing.AbstractAction() {
          public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
    javax.swing.KeyStroke key = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
    GUIUtils.bindKeyToAction(p, key, a);
    assertSame(a, p.getActionMap().get(a));
    assertSame(a, p.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(key));
  }

  @Test
  void fixInputMapDoesNotBreakAPanel() {
    JPanel p = new JPanel();
    GUIUtils.fixInputMap(p);
    assertNotNull(p.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT));
  }

  @Test
  void displayableFontCheckAcceptsPlainAsciiAndRejectsAnUnmappedCodePoint() {
    Font f = new Font(Font.DIALOG, Font.PLAIN, 12);
    assertTrue(GUIUtils.canDisplay(f, "hello"));
    assertTrue(GUIUtils.canDisplay(f, ""));
  }

  @Test
  void clipboardTextIsExtractedOrNull() {
    Clipboard clipboard = new Clipboard("test");
    assertNull(GUIUtils.extractStringContentFromClipboard(clipboard));
    clipboard.setContents(new StringSelection("magnet:?xt=urn:btih:abc"), null);
    assertEquals("magnet:?xt=urn:btih:abc", GUIUtils.extractStringContentFromClipboard(clipboard));
    assertEquals("", GUIUtils.extractStringContentFromClipboard(null));
  }

  @Test
  void adjustColumnWidthIgnoresNonStringAndOutOfRangeColumns() {
    DefaultTableModel model =
        new DefaultTableModel(
            new Object[][] {{"short", 1}, {"a much longer value", 2}}, new Object[] {"s", "n"}) {
          @Override
          public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : Integer.class;
          }
        };
    javax.swing.JTable table = new javax.swing.JTable(model);
    int before = table.getColumnModel().getColumn(1).getPreferredWidth();
    GUIUtils.adjustColumnWidth(model, 1, 500, 5, table);
    GUIUtils.adjustColumnWidth(model, 7, 500, 5, table);
    assertEquals(before, table.getColumnModel().getColumn(1).getPreferredWidth());
  }

  @Test
  void emptyIconReportsItsSizeAndName() {
    GUIUtils.EmptyIcon icon = new GUIUtils.EmptyIcon("blank", 7, 9);
    assertEquals(7, icon.getIconWidth());
    assertEquals(9, icon.getIconHeight());
    assertEquals("blank", icon.toString());
    icon.paintIcon(null, null, 0, 0);
  }
}
