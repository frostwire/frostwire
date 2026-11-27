/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.swig.settings_pack;
import com.limegroup.gnutella.gui.WholeNumberField;
import com.limegroup.gnutella.gui.options.panes.PaneSupport.FakeEngine;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.Setting;

/** Options &gt; Connection &gt; I2P, built for real, headless. */
class I2PPaneItemTest {
  private static final Setting[] TOUCHED = {
    ConnectionSettings.I2P_ENABLED,
    ConnectionSettings.I2P_HOSTNAME,
    ConnectionSettings.I2P_PORT,
    ConnectionSettings.I2P_ALLOW_MIXED,
    ConnectionSettings.I2P_INBOUND_QUANTITY,
    ConnectionSettings.I2P_OUTBOUND_QUANTITY,
    ConnectionSettings.I2P_INBOUND_LENGTH,
    ConnectionSettings.I2P_OUTBOUND_LENGTH,
  };

  private FakeEngine engine;

  @BeforeEach
  void fakeTheEngine() {
    for (Setting s : TOUCHED) {
      s.revertToDefault();
    }
    engine = new FakeEngine();
  }

  @AfterEach
  void restore() {
    engine.close();
    for (Setting s : TOUCHED) {
      s.revertToDefault();
    }
  }

  private static I2PPaneItem open() throws Exception {
    I2PPaneItem pane = PaneSupport.onEdt(I2PPaneItem::new);
    pane.initOptions();
    return pane;
  }

  /** In the order they were added: port, inbound qty, outbound qty, inbound hops, outbound hops. */
  private static List<WholeNumberField> numbers(I2PPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), WholeNumberField.class);
  }

  private static JTextField host(I2PPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JTextField.class).stream()
        .filter(f -> !(f instanceof WholeNumberField))
        .findFirst()
        .orElseThrow();
  }

  private static JCheckBox box(I2PPaneItem pane, String text) {
    return PaneSupport.all(pane.getContainer(), JCheckBox.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void everythingIsDisabledUntilI2PIsEnabled() throws Exception {
    I2PPaneItem pane = open();
    assertFalse(box(pane, "Enable I2P Support").isSelected());
    assertFalse(host(pane).isEnabled());
    assertFalse(box(pane, "Allow mixed mode (I2P + regular peers)").isEnabled());
    assertEquals(5, numbers(pane).size());
    for (WholeNumberField field : numbers(pane)) {
      assertFalse(field.isEnabled());
    }
    assertFalse(pane.isDirty());

    box(pane, "Enable I2P Support").setSelected(true);
    assertTrue(host(pane).isEnabled());
    assertTrue(box(pane, "Allow mixed mode (I2P + regular peers)").isEnabled());
    for (WholeNumberField field : numbers(pane)) {
      assertTrue(field.isEnabled());
    }
    assertTrue(pane.isDirty());
  }

  @Test
  void theSavedSettingsAreShownOnOpen() throws Exception {
    ConnectionSettings.I2P_ENABLED.setValue(true);
    ConnectionSettings.I2P_HOSTNAME.setValue("sam.example");
    ConnectionSettings.I2P_PORT.setValue(7000);
    ConnectionSettings.I2P_ALLOW_MIXED.setValue(true);
    ConnectionSettings.I2P_INBOUND_QUANTITY.setValue(5);
    ConnectionSettings.I2P_OUTBOUND_QUANTITY.setValue(6);
    ConnectionSettings.I2P_INBOUND_LENGTH.setValue(1);
    ConnectionSettings.I2P_OUTBOUND_LENGTH.setValue(2);
    I2PPaneItem pane = open();
    assertEquals("sam.example", host(pane).getText());
    assertTrue(box(pane, "Allow mixed mode (I2P + regular peers)").isSelected());
    assertEquals(
        List.of(7000, 5, 6, 1, 2), numbers(pane).stream().map(WholeNumberField::getValue).toList());
    assertFalse(pane.isDirty());
  }

  @Test
  void enablingI2PAndApplyingSavesItAndConfiguresTheEngine() throws Exception {
    I2PPaneItem pane = open();
    box(pane, "Enable I2P Support").setSelected(true);
    host(pane).setText("127.0.0.1");
    numbers(pane).get(0).setValue(7657);
    numbers(pane).get(1).setValue(4);
    numbers(pane).get(3).setValue(0);

    assertFalse(pane.applyOptions());
    assertTrue(ConnectionSettings.I2P_ENABLED.getValue());
    assertEquals("127.0.0.1", ConnectionSettings.I2P_HOSTNAME.getValue());
    assertEquals(7657, ConnectionSettings.I2P_PORT.getValue());
    assertEquals(4, ConnectionSettings.I2P_INBOUND_QUANTITY.getValue());
    assertEquals(0, ConnectionSettings.I2P_INBOUND_LENGTH.getValue(), "zero hops is allowed");
    assertFalse(pane.isDirty());
    assertEquals(1, engine.applied.size());
    assertEquals(
        "127.0.0.1",
        engine.applied.get(0).getString(settings_pack.string_types.i2p_hostname.swigValue()));
    assertEquals(
        7657, engine.applied.get(0).getInteger(settings_pack.int_types.i2p_port.swigValue()));
  }

  @Test
  void disablingI2PClearsTheEngineHostname() throws Exception {
    ConnectionSettings.I2P_ENABLED.setValue(true);
    ConnectionSettings.I2P_HOSTNAME.setValue("127.0.0.1");
    I2PPaneItem pane = open();
    box(pane, "Enable I2P Support").setSelected(false);
    pane.applyOptions();
    assertFalse(ConnectionSettings.I2P_ENABLED.getValue());
    assertEquals(1, engine.applied.size());
    assertEquals(
        "", engine.applied.get(0).getString(settings_pack.string_types.i2p_hostname.swigValue()));
  }

  @Test
  void tunnelQuantitiesOutsideOneToSixteenAreRefused() throws Exception {
    for (int[] bad : new int[][] {{0, 3}, {17, 3}, {3, 0}, {3, 17}}) {
      I2PPaneItem pane = open();
      box(pane, "Enable I2P Support").setSelected(true);
      host(pane).setText("127.0.0.1");
      numbers(pane).get(1).setValue(bad[0]);
      numbers(pane).get(2).setValue(bad[1]);
      try (PaneSupport.Errors errors = new PaneSupport.Errors()) {
        assertFalse(pane.applyOptions());
        assertEquals(List.of("Tunnel quantities must be between 1 and 16"), errors.shown);
      }
      assertFalse(ConnectionSettings.I2P_ENABLED.getValue(), "nothing was saved");
    }
    assertTrue(engine.applied.isEmpty());
  }

  @Test
  void tunnelHopsOutsideZeroToSevenAreRefused() throws Exception {
    for (int[] bad : new int[][] {{8, 3}, {3, 8}}) {
      I2PPaneItem pane = open();
      box(pane, "Enable I2P Support").setSelected(true);
      host(pane).setText("127.0.0.1");
      numbers(pane).get(3).setValue(bad[0]);
      numbers(pane).get(4).setValue(bad[1]);
      try (PaneSupport.Errors errors = new PaneSupport.Errors()) {
        assertFalse(pane.applyOptions());
        assertEquals(List.of("Tunnel hop lengths must be between 0 and 7"), errors.shown);
      }
      assertFalse(ConnectionSettings.I2P_ENABLED.getValue());
    }
    assertTrue(engine.applied.isEmpty());
  }
}
