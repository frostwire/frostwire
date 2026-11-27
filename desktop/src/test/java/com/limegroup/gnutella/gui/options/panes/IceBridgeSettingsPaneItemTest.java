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

import com.limegroup.gnutella.gui.WholeNumberField;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.awt.Component;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.Setting;

/** Options &gt; IceBridge: the network switches, local daemon fields and remote relay fields. */
class IceBridgeSettingsPaneItemTest {
  private static final Setting[] TOUCHED = {
    SearchEnginesSettings.ICEBRIDGE_ENABLED,
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG,
    SearchEnginesSettings.ICEBRIDGE_USE_REMOTE,
    SearchEnginesSettings.ICEBRIDGE_BIND_HOST,
    SearchEnginesSettings.ICEBRIDGE_RUDP_PORT,
    SearchEnginesSettings.ICEBRIDGE_ROLE,
    SearchEnginesSettings.ICEBRIDGE_REMOTE_URL,
    SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN,
  };

  @BeforeEach
  void defaults() {
    for (Setting s : TOUCHED) {
      s.revertToDefault();
    }
  }

  @AfterEach
  void restore() {
    for (Setting s : TOUCHED) {
      s.revertToDefault();
    }
  }

  private static IceBridgeSettingsPaneItem open() throws Exception {
    IceBridgeSettingsPaneItem pane = PaneSupport.onEdt(IceBridgeSettingsPaneItem::new);
    pane.initOptions();
    return pane;
  }

  private static JCheckBox network(IceBridgeSettingsPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JCheckBox.class).get(0);
  }

  private static JCheckBox catalog(IceBridgeSettingsPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JCheckBox.class).get(1);
  }

  private static JRadioButton local(IceBridgeSettingsPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JRadioButton.class).get(0);
  }

  private static JRadioButton remote(IceBridgeSettingsPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JRadioButton.class).get(1);
  }

  /** Bind host, remote URL and remote token, in the order they sit on the page. */
  private static List<JTextField> texts(IceBridgeSettingsPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JTextField.class).stream()
        .filter(f -> !(f instanceof WholeNumberField))
        .toList();
  }

  private static WholeNumberField rudpPort(IceBridgeSettingsPaneItem pane) {
    return PaneSupport.only(pane.getContainer(), WholeNumberField.class);
  }

  @SuppressWarnings("unchecked")
  private static JComboBox<String> role(IceBridgeSettingsPaneItem pane) {
    return PaneSupport.only(pane.getContainer(), JComboBox.class);
  }

  /** A component is on screen only when it and everything between it and the pane is visible. */
  private static boolean shown(IceBridgeSettingsPaneItem pane, Component c) {
    for (Component p = c; p != null && p != pane.getContainer(); p = p.getParent()) {
      if (!p.isVisible()) {
        return false;
      }
    }
    return true;
  }

  @Test
  void theStoredSettingsAreShownOnOpen() throws Exception {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(true);
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(false);
    SearchEnginesSettings.ICEBRIDGE_BIND_HOST.setValue("10.0.0.5");
    SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.setValue(6890);
    SearchEnginesSettings.ICEBRIDGE_ROLE.setValue("FORWARDER");
    SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.setValue("https://relay.example.com");
    SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.setValue("secret");
    IceBridgeSettingsPaneItem pane = open();
    assertTrue(network(pane).isSelected());
    assertFalse(catalog(pane).isSelected());
    assertTrue(local(pane).isSelected());
    assertEquals("10.0.0.5", texts(pane).get(0).getText());
    assertEquals(6890, rudpPort(pane).getValue());
    assertEquals("FORWARDER", role(pane).getSelectedItem());
    assertEquals("https://relay.example.com", texts(pane).get(1).getText());
    assertEquals("secret", texts(pane).get(2).getText());
    assertFalse(pane.isDirty());
  }

  @Test
  void leavingTheNetworkDisablesEverythingElse() throws Exception {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(false);
    IceBridgeSettingsPaneItem pane = open();
    assertFalse(catalog(pane).isEnabled());
    assertFalse(local(pane).isEnabled());
    assertFalse(remote(pane).isEnabled());
    assertFalse(texts(pane).get(0).isEnabled());
    assertFalse(rudpPort(pane).isEnabled());
    assertFalse(role(pane).isEnabled());

    network(pane).setSelected(true);
    assertTrue(catalog(pane).isEnabled());
    assertTrue(local(pane).isEnabled());
    assertTrue(remote(pane).isEnabled());
    assertTrue(texts(pane).get(0).isEnabled(), "local mode: the bind host is editable");
    assertTrue(rudpPort(pane).isEnabled());
    assertTrue(role(pane).isEnabled());
    assertFalse(texts(pane).get(1).isEnabled(), "local mode: no remote URL");
    assertFalse(texts(pane).get(2).isEnabled());
  }

  @Test
  void theLocalAndRemoteModesShowTheirOwnFieldsOnly() throws Exception {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(true);
    IceBridgeSettingsPaneItem pane = open();
    assertTrue(shown(pane, texts(pane).get(0)));
    assertFalse(shown(pane, texts(pane).get(1)));
    assertFalse(shown(pane, texts(pane).get(2)));

    remote(pane).setSelected(true);
    assertFalse(shown(pane, texts(pane).get(0)));
    assertTrue(shown(pane, texts(pane).get(1)));
    assertTrue(shown(pane, texts(pane).get(2)));
    assertFalse(texts(pane).get(0).isEnabled());
    assertFalse(rudpPort(pane).isEnabled());
    assertFalse(role(pane).isEnabled());
    assertTrue(texts(pane).get(1).isEnabled());
    assertTrue(texts(pane).get(2).isEnabled());

    local(pane).setSelected(true);
    assertTrue(shown(pane, texts(pane).get(0)));
    assertFalse(shown(pane, texts(pane).get(1)));
  }

  @Test
  void aStoredRemoteChoiceOpensInRemoteMode() throws Exception {
    SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.setValue(true);
    IceBridgeSettingsPaneItem pane = open();
    assertTrue(remote(pane).isSelected());
    assertFalse(local(pane).isSelected());
    assertFalse(pane.isDirty());
  }

  @Test
  void everyFieldMakesThePaneDirty() throws Exception {
    IceBridgeSettingsPaneItem pane = open();
    assertFalse(pane.isDirty());

    network(pane).setSelected(!network(pane).isSelected());
    assertTrue(pane.isDirty());
    network(pane).setSelected(!network(pane).isSelected());
    assertFalse(pane.isDirty());

    catalog(pane).setSelected(!catalog(pane).isSelected());
    assertTrue(pane.isDirty());
    catalog(pane).setSelected(!catalog(pane).isSelected());

    remote(pane).setSelected(true);
    assertTrue(pane.isDirty());
    local(pane).setSelected(true);

    texts(pane).get(0).setText("1.2.3.4");
    assertTrue(pane.isDirty());
    texts(pane).get(0).setText(SearchEnginesSettings.ICEBRIDGE_BIND_HOST.getValue() + " ");
    assertFalse(pane.isDirty(), "trailing blanks do not count");

    rudpPort(pane).setValue(rudpPort(pane).getValue() + 1);
    assertTrue(pane.isDirty());
    rudpPort(pane).setValue(SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue());

    role(pane).setSelectedItem("CLIENT");
    assertTrue(pane.isDirty());
    role(pane).setSelectedItem("BOTH");

    texts(pane).get(1).setText("http://127.0.0.1:8080");
    assertTrue(pane.isDirty());
    texts(pane).get(1).setText("");

    texts(pane).get(2).setText("token");
    assertTrue(pane.isDirty());
  }

  @Test
  void applyingSavesEverythingTrimmedAndAsksForARestartOnlyWhenSomethingChanged() throws Exception {
    IceBridgeSettingsPaneItem pane = open();
    assertFalse(pane.applyOptions(), "nothing changed, no restart");

    network(pane).setSelected(true);
    catalog(pane).setSelected(false);
    remote(pane).setSelected(true);
    texts(pane).get(0).setText(" 192.168.0.9 ");
    rudpPort(pane).setValue(6901);
    role(pane).setSelectedItem("CLIENT");
    texts(pane).get(1).setText(" https://relay.example.com ");
    texts(pane).get(2).setText(" tok ");
    assertTrue(pane.applyOptions(), "IceBridge changes need a restart");

    assertTrue(SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    assertFalse(SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
    assertTrue(SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue());
    assertEquals("192.168.0.9", SearchEnginesSettings.ICEBRIDGE_BIND_HOST.getValue());
    assertEquals(6901, SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue());
    assertEquals("CLIENT", SearchEnginesSettings.ICEBRIDGE_ROLE.getValue());
    assertEquals(
        "https://relay.example.com", SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue());
    assertEquals("tok", SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue());
    assertFalse(pane.isDirty());
    assertFalse(pane.applyOptions(), "applied, so the next apply has nothing to restart for");
  }

  @Test
  void theRoleChoicesAreTheThreeRelayRoles() throws Exception {
    JComboBox<String> role = role(open());
    assertEquals(3, role.getItemCount());
    assertEquals("BOTH", role.getItemAt(0));
    assertEquals("CLIENT", role.getItemAt(1));
    assertEquals("FORWARDER", role.getItemAt(2));
  }
}
