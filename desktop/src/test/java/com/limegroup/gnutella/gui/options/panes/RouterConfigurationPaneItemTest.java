/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.WholeNumberField;
import com.limegroup.gnutella.gui.options.panes.PaneSupport.FakeEngine;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.io.IOException;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.Setting;

/** Options &gt; Connection &gt; Router Configuration, built for real, headless. */
class RouterConfigurationPaneItemTest {
  private static final Setting[] TOUCHED = {
    ConnectionSettings.MANUAL_PORT_RANGE,
    ConnectionSettings.PORT_RANGE_0,
    ConnectionSettings.PORT_RANGE_1,
    ConnectionSettings.NATPMP_GATEWAY,
    ConnectionSettings.NATPMP_LEASE_DURATION,
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE,
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT,
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

  private static JRadioButton radio(RouterConfigurationPaneItem pane, String text) {
    return PaneSupport.all(pane.getContainer(), JRadioButton.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  private static List<WholeNumberField> numbers(RouterConfigurationPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), WholeNumberField.class);
  }

  private static JTextField gateway(RouterConfigurationPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JTextField.class).stream()
        .filter(f -> !(f instanceof WholeNumberField))
        .findFirst()
        .orElseThrow();
  }

  private static RouterConfigurationPaneItem open() throws Exception {
    RouterConfigurationPaneItem pane = PaneSupport.onEdt(RouterConfigurationPaneItem::new);
    pane.initOptions();
    return pane;
  }

  @Test
  void aRandomPortDisablesTheRangeFieldsAndLabels() throws Exception {
    RouterConfigurationPaneItem pane = open();
    assertTrue(radio(pane, "Use random port (Recommended)").isSelected());
    // fields in order: port start, port end, NAT-PMP lease
    assertFalse(numbers(pane).get(0).isEnabled());
    assertFalse(numbers(pane).get(1).isEnabled());
    assertTrue(numbers(pane).get(2).isEnabled(), "NAT-PMP lease is always editable");
    for (JLabel label : PaneSupport.all(pane.getContainer(), JLabel.class)) {
      if (label.getText() != null && label.getText().startsWith("TCP port")) {
        assertFalse(label.isEnabled());
      }
    }
    assertFalse(pane.isDirty());

    radio(pane, "Manual port range").setSelected(true);
    assertTrue(numbers(pane).get(0).isEnabled());
    assertTrue(numbers(pane).get(1).isEnabled());
    assertTrue(pane.isDirty(), "switching to manual is a change");
  }

  @Test
  void aManualRangeIsSavedAndTheListenSocketsRebound() throws Exception {
    RouterConfigurationPaneItem pane = open();
    radio(pane, "Manual port range").setSelected(true);
    numbers(pane).get(0).setValue(40000);
    numbers(pane).get(1).setValue(40010);

    assertFalse(pane.applyOptions());
    assertTrue(ConnectionSettings.MANUAL_PORT_RANGE.getValue());
    assertEquals(40000, ConnectionSettings.PORT_RANGE_0.getValue());
    assertEquals(40010, ConnectionSettings.PORT_RANGE_1.getValue());
    assertEquals(1, engine.listenInterfaces.size(), "the sockets were rebound once");
    java.util.regex.Matcher bound =
        java.util.regex.Pattern.compile(":(\\d+)").matcher(engine.listenInterfaces.get(0));
    assertTrue(bound.find(), engine.listenInterfaces.get(0));
    int port = Integer.parseInt(bound.group(1));
    assertTrue(
        port >= 40000 && port <= 40010,
        "bound in the chosen range: " + engine.listenInterfaces.get(0));
    assertTrue(engine.applied.isEmpty(), "NAT-PMP did not change");
    assertFalse(pane.isDirty());
  }

  @Test
  void natPmpChangesAreAppliedWithoutReboundingTheSockets() throws Exception {
    RouterConfigurationPaneItem pane = open();
    gateway(pane).setText("  192.168.1.1  ");
    numbers(pane).get(2).setValue(1200);
    assertTrue(pane.isDirty());

    pane.applyOptions();
    assertEquals("192.168.1.1", ConnectionSettings.NATPMP_GATEWAY.getValue(), "trimmed");
    assertEquals(1200, ConnectionSettings.NATPMP_LEASE_DURATION.getValue());
    assertEquals(1, engine.applied.size());
    assertTrue(engine.listenInterfaces.isEmpty());
    assertFalse(pane.isDirty());
  }

  @Test
  void aNewRangeInRandomModeIsIgnored() throws Exception {
    RouterConfigurationPaneItem pane = open();
    numbers(pane).get(0).setValue(41000);
    numbers(pane).get(1).setValue(41010);
    assertFalse(pane.isDirty(), "the range does not matter while the port is random");
    pane.applyOptions();
    assertEquals(
        ConnectionSettings.PORT_RANGE_0.getDefaultValue(),
        ConnectionSettings.PORT_RANGE_0.getValue());
    assertTrue(engine.listenInterfaces.isEmpty());
  }

  @Test
  void aManualPortOutsideTheAllowedRangeIsRefusedAndNothingIsSaved() throws Exception {
    RouterConfigurationPaneItem pane = open();
    radio(pane, "Manual port range").setSelected(true);
    numbers(pane).get(0).setValue(80);
    numbers(pane).get(1).setValue(90);
    try (PaneSupport.Errors errors = new PaneSupport.Errors()) {
      assertThrows(IOException.class, pane::applyOptions);
      assertEquals(1, errors.shown.size());
    }
    assertFalse(ConnectionSettings.MANUAL_PORT_RANGE.getValue());
    assertTrue(engine.listenInterfaces.isEmpty());
  }

  @Test
  void aRangeThatEndsBeforeItStartsIsRefused() throws Exception {
    RouterConfigurationPaneItem pane = open();
    radio(pane, "Manual port range").setSelected(true);
    int start = ConnectionSettings.PORT_RANGE_0.getDefaultValue() + 100;
    numbers(pane).get(0).setValue(start);
    numbers(pane).get(1).setValue(start - 1);
    try (PaneSupport.Errors errors = new PaneSupport.Errors()) {
      assertThrows(IOException.class, pane::applyOptions);
      assertEquals(List.of("You must enter a valid port range."), errors.shown);
    }
    assertFalse(ConnectionSettings.MANUAL_PORT_RANGE.getValue());
  }

  @Test
  void aNatPmpLeaseOfZeroIsRefused() throws Exception {
    RouterConfigurationPaneItem pane = open();
    numbers(pane).get(2).setValue(0);
    try (PaneSupport.Errors errors = new PaneSupport.Errors()) {
      assertThrows(IOException.class, pane::applyOptions);
      assertEquals(List.of("NAT-PMP lease duration must be greater than 0 seconds."), errors.shown);
    }
    assertTrue(ConnectionSettings.NATPMP_LEASE_DURATION.getValue() > 0);
    assertTrue(engine.applied.isEmpty());
  }

  @Test
  void theSavedManualRangeIsShownOnOpen() throws Exception {
    ConnectionSettings.MANUAL_PORT_RANGE.setValue(true);
    ConnectionSettings.PORT_RANGE_0.setValue(45000);
    ConnectionSettings.PORT_RANGE_1.setValue(45005);
    RouterConfigurationPaneItem pane = open();
    assertTrue(radio(pane, "Manual port range").isSelected());
    assertEquals(45000, numbers(pane).get(0).getValue());
    assertEquals(45005, numbers(pane).get(1).getValue());
    assertTrue(numbers(pane).get(0).isEnabled());
    numbers(pane).get(1).setValue(45006);
    assertTrue(pane.isDirty());
  }
}
