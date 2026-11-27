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

import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.limegroup.gnutella.gui.WholeNumberField;
import com.limegroup.gnutella.gui.options.panes.PaneSupport.FakeEngine;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.Setting;

/** Options &gt; Connection &gt; Proxy and its Login Details pane, built for real, headless. */
class ProxyPanesTest {
  private static final Setting[] TOUCHED = {
    ConnectionSettings.CONNECTION_METHOD,
    ConnectionSettings.PROXY_HOST,
    ConnectionSettings.PROXY_PORT,
    ConnectionSettings.PROXY_AUTHENTICATE,
    ConnectionSettings.PROXY_USERNAME,
    ConnectionSettings.PROXY_PASS,
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

  private static JRadioButton radio(ProxyPaneItem pane, String text) {
    return PaneSupport.all(pane.getContainer(), JRadioButton.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  private static JTextField hostField(ProxyPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JTextField.class).stream()
        .filter(f -> !(f instanceof WholeNumberField))
        .findFirst()
        .orElseThrow();
  }

  private static WholeNumberField portField(ProxyPaneItem pane) {
    return PaneSupport.only(pane.getContainer(), WholeNumberField.class);
  }

  private static int proxyType(SettingsPack pack) {
    return pack.getInteger(settings_pack.int_types.proxy_type.swigValue());
  }

  @Test
  void noProxyLeavesNothingToType() throws Exception {
    ProxyPaneItem pane = PaneSupport.onEdt(ProxyPaneItem::new);
    pane.initOptions();
    assertTrue(radio(pane, "No Proxy").isSelected());
    for (JTextField field : List.of(hostField(pane), portField(pane))) {
      assertFalse(field.isEnabled());
      assertFalse(field.isEditable());
    }
    assertFalse(pane.isDirty());
  }

  @Test
  void choosingAProxyKindAndApplyingSavesItAndTellsTheEngine() throws Exception {
    ProxyPaneItem pane = PaneSupport.onEdt(ProxyPaneItem::new);
    pane.initOptions();

    radio(pane, "Socks v5").setSelected(true);
    assertTrue(pane.isDirty(), "a different kind than the saved one");
    hostField(pane).setText("proxy.example");
    portField(pane).setValue(1080);

    assertFalse(pane.applyOptions());
    assertEquals(
        ConnectionSettings.C_SOCKS5_PROXY, ConnectionSettings.CONNECTION_METHOD.getValue());
    assertEquals("proxy.example", ConnectionSettings.PROXY_HOST.getValue());
    assertEquals(1080, ConnectionSettings.PROXY_PORT.getValue());
    assertFalse(pane.isDirty());
    assertEquals(1, engine.applied.size());
    assertEquals(settings_pack.proxy_type_t.socks5.swigValue(), proxyType(engine.applied.get(0)));
    assertEquals(
        "proxy.example",
        engine.applied.get(0).getString(settings_pack.string_types.proxy_hostname.swigValue()));

    pane.applyOptions();
    assertEquals(1, engine.applied.size(), "nothing changed, the engine is left alone");
  }

  @Test
  void eachKindMapsToItsLibtorrentProxyType() {
    ConnectionSettings.PROXY_AUTHENTICATE.setValue(false);
    int[][] cases = {
      {ConnectionSettings.C_NO_PROXY, settings_pack.proxy_type_t.none.swigValue()},
      {ConnectionSettings.C_SOCKS4_PROXY, settings_pack.proxy_type_t.socks4.swigValue()},
      {ConnectionSettings.C_SOCKS5_PROXY, settings_pack.proxy_type_t.socks5.swigValue()},
      {ConnectionSettings.C_HTTP_PROXY, settings_pack.proxy_type_t.http.swigValue()},
    };
    for (int[] c : cases) {
      ConnectionSettings.CONNECTION_METHOD.setValue(c[0]);
      SettingsPack pack = new SettingsPack();
      ProxyPaneItem.fillProxySettings(pack);
      assertEquals(c[1], proxyType(pack));
    }
    ConnectionSettings.PROXY_AUTHENTICATE.setValue(true);
    SettingsPack pack = new SettingsPack();
    ConnectionSettings.CONNECTION_METHOD.setValue(ConnectionSettings.C_HTTP_PROXY);
    ProxyPaneItem.fillProxySettings(pack);
    assertEquals(settings_pack.proxy_type_t.http_pw.swigValue(), proxyType(pack));
    pack = new SettingsPack();
    ConnectionSettings.CONNECTION_METHOD.setValue(ConnectionSettings.C_SOCKS5_PROXY);
    ProxyPaneItem.fillProxySettings(pack);
    assertEquals(settings_pack.proxy_type_t.socks5_pw.swigValue(), proxyType(pack));
  }

  @Test
  void theSavedKindIsShownOnOpenAndNoProxyDisablesTheFieldsAtOnce() throws Exception {
    ConnectionSettings.CONNECTION_METHOD.setValue(ConnectionSettings.C_HTTP_PROXY);
    ConnectionSettings.PROXY_HOST.setValue("h.example");
    ConnectionSettings.PROXY_PORT.setValue(3128);
    ProxyPaneItem pane = PaneSupport.onEdt(ProxyPaneItem::new);
    pane.initOptions();
    assertTrue(radio(pane, "HTTP").isSelected());
    assertEquals("h.example", hostField(pane).getText());
    assertEquals(3128, portField(pane).getValue());
    assertTrue(hostField(pane).isEnabled());
    assertTrue(portField(pane).isEnabled());
    assertFalse(pane.isDirty());

    radio(pane, "No Proxy").setSelected(true);
    assertTrue(pane.isDirty());
    assertFalse(hostField(pane).isEnabled());
    assertFalse(portField(pane).isEnabled());
  }

  @Test
  void aChangedPortAloneMakesThePaneDirty() throws Exception {
    ProxyPaneItem pane = PaneSupport.onEdt(ProxyPaneItem::new);
    pane.initOptions();
    portField(pane).setValue(ConnectionSettings.PROXY_PORT.getValue() + 1);
    assertTrue(pane.isDirty());
  }

  @Test
  void loginFieldsFollowTheAuthenticateBox() throws Exception {
    ConnectionSettings.PROXY_USERNAME.setValue("user");
    ConnectionSettings.PROXY_PASS.setValue("secret");
    ConnectionSettings.PROXY_AUTHENTICATE.setValue(false);
    ProxyLoginPaneItem pane = PaneSupport.onEdt(ProxyLoginPaneItem::new);
    pane.initOptions();
    JCheckBox authenticate = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    List<JTextField> fields = PaneSupport.all(pane.getContainer(), JTextField.class);
    assertEquals(2, fields.size());
    assertEquals("user", fields.get(0).getText());
    assertEquals("secret", fields.get(1).getText());
    for (JTextField f : fields) {
      assertFalse(f.isEnabled());
    }
    assertFalse(pane.isDirty());

    authenticate.setSelected(true);
    for (JTextField f : fields) {
      assertTrue(f.isEnabled());
      assertTrue(f.isEditable());
    }
    assertTrue(pane.isDirty());
    fields.get(0).setText("other");
    pane.applyOptions();
    assertEquals("other", ConnectionSettings.PROXY_USERNAME.getValue());
    assertEquals("secret", ConnectionSettings.PROXY_PASS.getValue());
    assertTrue(ConnectionSettings.PROXY_AUTHENTICATE.getValue());
    assertFalse(pane.isDirty());
    assertEquals(1, engine.applied.size());
    assertEquals(
        "other",
        engine.applied.get(0).getString(settings_pack.string_types.proxy_username.swigValue()));
  }
}
