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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The tips and donations field of the share dialogs: the Bitcoin address checks. */
class CryptoCurrencyTextFieldTest {

  /** A well-formed 34 character legacy address. */
  private static final String ADDRESS = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT";

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

  private interface Check {
    void run(CryptoCurrencyTextField field);
  }

  private static void with(String text, Check check) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          CryptoCurrencyTextField field =
              new CryptoCurrencyTextField(CryptoCurrencyTextField.CurrencyURIPrefix.BITCOIN);
          field.setText(text);
          check.run(field);
        });
  }

  @Test
  void theBitcoinPrefixIsTheUriScheme() {
    assertEquals("bitcoin:", CryptoCurrencyTextField.CurrencyURIPrefix.BITCOIN.toString());
  }

  @Test
  void aLegacyAddressStartingWithOneOrThreeIsValid() throws Exception {
    with(ADDRESS, f -> assertTrue(f.hasValidAddress()));
    with("3" + ADDRESS.substring(1), f -> assertTrue(f.hasValidAddress()));
    with("  " + ADDRESS + "  ", f -> assertTrue(f.hasValidAddress()));
  }

  @Test
  void aPrefixedAddressIsValidToo() throws Exception {
    with("bitcoin:" + ADDRESS, f -> assertTrue(f.hasValidAddress()));
  }

  @Test
  void addressesOfTheWrongLengthOrFirstCharacterAreRefused() throws Exception {
    with("", f -> assertFalse(f.hasValidAddress()));
    with("   ", f -> assertFalse(f.hasValidAddress()));
    with(ADDRESS.substring(0, 25), f -> assertFalse(f.hasValidAddress()));
    with(ADDRESS + "x", f -> assertFalse(f.hasValidAddress()));
    with("2" + ADDRESS.substring(1), f -> assertFalse(f.hasValidAddress()));
    with("L" + ADDRESS.substring(1), f -> assertFalse(f.hasValidAddress()));
  }

  @Test
  void aTextWithNoSchemeOrTheRightSchemeHasAValidPrefix() throws Exception {
    with(ADDRESS, f -> assertTrue(f.hasValidPrefixOrNoPrefix()));
    with("bitcoin:" + ADDRESS, f -> assertTrue(f.hasValidPrefixOrNoPrefix()));
    with("litecoin:" + ADDRESS, f -> assertFalse(f.hasValidPrefixOrNoPrefix()));
    with("http://example.com", f -> assertFalse(f.hasValidPrefixOrNoPrefix()));
    with("", f -> assertTrue(f.hasValidPrefixOrNoPrefix()));
  }

  @Test
  void aValidAddressIsNormalizedToAUriWithThePrefix() throws Exception {
    with(ADDRESS, f -> assertEquals("bitcoin:" + ADDRESS, f.normalizeValidAddress()));
    with(
        " bitcoin:" + ADDRESS + " ",
        f -> assertEquals("bitcoin:" + ADDRESS, f.normalizeValidAddress()));
  }
}
