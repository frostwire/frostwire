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

import com.frostwire.bittorrent.PaymentOptions;
import com.limegroup.gnutella.gui.LimeTextField;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The tips and donations section of the share dialogs. */
class PaymentOptionsPanelTest {

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

  private static void collect(Container parent, List<Component> out) {
    for (Component c : parent.getComponents()) {
      out.add(c);
      if (c instanceof Container container) {
        collect(container, out);
      }
    }
  }

  private static List<Component> everything(Container panel) {
    List<Component> all = new ArrayList<>();
    collect(panel, all);
    return all;
  }

  private interface Scenario {
    void run(
        PaymentOptionsPanel panel,
        JCheckBox confirm,
        CryptoCurrencyTextField bitcoin,
        LimeTextField paypal);
  }

  private static void scenario(Scenario s) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          PaymentOptionsPanel panel = new PaymentOptionsPanel();
          List<Component> all = everything(panel);
          JCheckBox confirm =
              (JCheckBox) all.stream().filter(c -> c instanceof JCheckBox).findFirst().get();
          CryptoCurrencyTextField bitcoin =
              (CryptoCurrencyTextField)
                  all.stream().filter(c -> c instanceof CryptoCurrencyTextField).findFirst().get();
          LimeTextField paypal =
              (LimeTextField)
                  all.stream()
                      .filter(
                          c ->
                              c instanceof LimeTextField && !(c instanceof CryptoCurrencyTextField))
                      .findFirst()
                      .get();
          s.run(panel, confirm, bitcoin, paypal);
        });
  }

  private static void keyReleased(CryptoCurrencyTextField field) {
    KeyEvent released = new KeyEvent(field, KeyEvent.KEY_RELEASED, 1L, 0, KeyEvent.VK_A, 'a');
    for (KeyListener listener : field.getKeyListeners()) {
      listener.keyReleased(released);
    }
  }

  @Test
  void theFieldsAreDisabledUntilTheCreatorConfirmsTheirRights() throws Exception {
    scenario(
        (panel, confirm, bitcoin, paypal) -> {
          assertFalse(bitcoin.isEnabled());
          assertFalse(paypal.isEnabled());
          confirm.doClick();
          assertTrue(bitcoin.isEnabled());
          assertTrue(paypal.isEnabled());
          confirm.doClick();
          assertFalse(bitcoin.isEnabled());
        });
  }

  @Test
  void withoutConfirmationOrAddressesThereAreNoPaymentOptions() throws Exception {
    scenario(
        (panel, confirm, bitcoin, paypal) -> {
          assertNull(panel.getPaymentOptions());
          assertFalse(panel.hasPaymentOptions());
          bitcoin.setText(ADDRESS);
          assertNull(panel.getPaymentOptions(), "not confirmed");
          confirm.doClick();
          bitcoin.setText("");
          assertFalse(panel.hasPaymentOptions(), "nothing entered");
        });
  }

  @Test
  void aValidBitcoinAddressBecomesABitcoinUri() throws Exception {
    scenario(
        (panel, confirm, bitcoin, paypal) -> {
          confirm.doClick();
          bitcoin.setText(ADDRESS);
          PaymentOptions options = panel.getPaymentOptions();
          assertNotNull(options);
          assertEquals("bitcoin:" + ADDRESS, options.bitcoin);
          assertNull(options.paypalUrl);
          assertTrue(panel.hasPaymentOptions());
        });
  }

  @Test
  void aPaypalAddressAloneIsEnoughAndAnInvalidBitcoinOneIsLeftOut() throws Exception {
    scenario(
        (panel, confirm, bitcoin, paypal) -> {
          confirm.doClick();
          bitcoin.setText("not an address");
          paypal.setText("http://paypal.me/someone");
          PaymentOptions options = panel.getPaymentOptions();
          assertNotNull(options);
          assertNull(options.bitcoin);
          assertEquals("http://paypal.me/someone", options.paypalUrl);
        });
  }

  @Test
  void bothAddressesAreKeptWhenBothAreGiven() throws Exception {
    scenario(
        (panel, confirm, bitcoin, paypal) -> {
          confirm.doClick();
          bitcoin.setText("bitcoin:" + ADDRESS);
          paypal.setText("http://paypal.me/someone");
          PaymentOptions options = panel.getPaymentOptions();
          assertEquals("bitcoin:" + ADDRESS, options.bitcoin);
          assertEquals("http://paypal.me/someone", options.paypalUrl);
        });
  }

  @Test
  void typingInTheBitcoinFieldMarksAnInvalidAddressRedAndStripsSpaces() throws Exception {
    scenario(
        (panel, confirm, bitcoin, paypal) -> {
          confirm.doClick();
          bitcoin.setText("1abc");
          keyReleased(bitcoin);
          assertEquals(Color.red, bitcoin.getForeground());
          bitcoin.setText(ADDRESS.substring(0, 10) + " " + ADDRESS.substring(10));
          keyReleased(bitcoin);
          assertEquals(ADDRESS, bitcoin.getText(), "spaces are removed");
          // the next key release judges the cleaned text (the first one judged it with the space)
          keyReleased(bitcoin);
          assertEquals(Color.black, bitcoin.getForeground());
          bitcoin.setText("litecoin:" + ADDRESS);
          keyReleased(bitcoin);
          assertEquals(Color.red, bitcoin.getForeground(), "the wrong scheme");
        });
  }
}
