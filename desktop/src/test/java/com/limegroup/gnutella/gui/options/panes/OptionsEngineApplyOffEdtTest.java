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

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.Setting;

/**
 * The Options window's OK button re-applied the I2P, Proxy and Firewall pages to libtorrent on the
 * EDT every time, even when nothing on them changed, and the Firewall and Network Interface pages
 * rebound the listen sockets on the EDT (frostwire/frostwire#1568). Now a page touches the engine
 * only when one of its settings changed, and does it on the background queue.
 *
 * <p>Each test runs the page's apply on the EDT, as the OK button does, and records the thread each
 * engine call is made on. The pane items themselves cannot be built headless (ThemeMediator reads
 * the screen size), so their static apply methods are tested.
 */
class OptionsEngineApplyOffEdtTest {
  private static final String BACKGROUND = "options-engine-test";

  private static final Setting[] TOUCHED = {
    ConnectionSettings.I2P_ENABLED,
    ConnectionSettings.I2P_HOSTNAME,
    ConnectionSettings.I2P_PORT,
    ConnectionSettings.I2P_ALLOW_MIXED,
    ConnectionSettings.I2P_INBOUND_QUANTITY,
    ConnectionSettings.I2P_OUTBOUND_QUANTITY,
    ConnectionSettings.I2P_INBOUND_LENGTH,
    ConnectionSettings.I2P_OUTBOUND_LENGTH,
    ConnectionSettings.CONNECTION_METHOD,
    ConnectionSettings.PROXY_HOST,
    ConnectionSettings.PROXY_PORT,
    ConnectionSettings.PROXY_AUTHENTICATE,
    ConnectionSettings.PROXY_USERNAME,
    ConnectionSettings.PROXY_PASS,
    ConnectionSettings.MANUAL_PORT_RANGE,
    ConnectionSettings.PORT_RANGE_0,
    ConnectionSettings.PORT_RANGE_1,
    ConnectionSettings.NATPMP_GATEWAY,
    ConnectionSettings.NATPMP_LEASE_DURATION,
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE,
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT,
  };

  /** Each engine call, as "method@thread". */
  private final List<String> calls = new CopyOnWriteArrayList<>();

  /** The last settings pack applied. */
  private volatile SettingsPack applied;

  private Consumer<Runnable> savedBackground;
  private Supplier<EngineSettings.Engine> savedEngine;

  @BeforeEach
  void fakeTheEngine() {
    savedBackground = EngineSettings.background;
    savedEngine = EngineSettings.engine;
    EngineSettings.background =
        work -> {
          Thread thread = new Thread(work, BACKGROUND);
          thread.start();
          try {
            thread.join();
          } catch (InterruptedException e) {
            throw new AssertionError(e);
          }
        };
    EngineSettings.Engine fake =
        (EngineSettings.Engine)
            Proxy.newProxyInstance(
                EngineSettings.Engine.class.getClassLoader(),
                new Class<?>[] {EngineSettings.Engine.class},
                (proxy, method, args) -> {
                  String thread =
                      SwingUtilities.isEventDispatchThread()
                          ? "EDT"
                          : Thread.currentThread().getName();
                  calls.add(method.getName() + "@" + thread);
                  if (args[0] instanceof SettingsPack settings) {
                    applied = settings;
                  }
                  return null;
                });
    EngineSettings.engine = () -> fake;
    revertSettings();
  }

  @AfterEach
  void restore() {
    EngineSettings.background = savedBackground;
    EngineSettings.engine = savedEngine;
    revertSettings();
  }

  private static void revertSettings() {
    for (Setting setting : TOUCHED) {
      setting.revertToDefault();
    }
  }

  /** Runs {@code ok} on the EDT, as the Options window's OK button does. */
  private static void onTheEdt(Runnable ok) throws Exception {
    SwingUtilities.invokeAndWait(ok);
  }

  private static void saveI2P() {
    ConnectionSettings.I2P_ENABLED.setValue(true);
    ConnectionSettings.I2P_HOSTNAME.setValue("127.0.0.1");
    ConnectionSettings.I2P_PORT.setValue(7656);
    ConnectionSettings.I2P_ALLOW_MIXED.setValue(false);
    ConnectionSettings.I2P_INBOUND_QUANTITY.setValue(3);
    ConnectionSettings.I2P_OUTBOUND_QUANTITY.setValue(3);
    ConnectionSettings.I2P_INBOUND_LENGTH.setValue(3);
    ConnectionSettings.I2P_OUTBOUND_LENGTH.setValue(3);
  }

  @Test
  void theI2PPageLeavesTheEngineAloneWhenNothingChanged() throws Exception {
    saveI2P();
    onTheEdt(() -> I2PPaneItem.apply(true, "127.0.0.1", 7656, false, 3, 3, 3, 3));
    assertEquals(List.of(), calls);
  }

  @Test
  void theI2PPageAppliesAChangeInTheBackground() throws Exception {
    saveI2P();
    onTheEdt(() -> I2PPaneItem.apply(true, "127.0.0.1", 7657, false, 3, 3, 3, 3));
    assertEquals(List.of("applySettings@" + BACKGROUND), calls);
    assertEquals(7657, ConnectionSettings.I2P_PORT.getValue());
  }

  private static void saveProxy() {
    ConnectionSettings.CONNECTION_METHOD.setValue(ConnectionSettings.C_HTTP_PROXY);
    ConnectionSettings.PROXY_HOST.setValue("proxy.example");
    ConnectionSettings.PROXY_PORT.setValue(3128);
    ConnectionSettings.PROXY_AUTHENTICATE.setValue(true);
    ConnectionSettings.PROXY_USERNAME.setValue("user");
    ConnectionSettings.PROXY_PASS.setValue("secret");
  }

  @Test
  void theProxyPageLeavesTheEngineAloneWhenNothingChanged() throws Exception {
    saveProxy();
    onTheEdt(() -> ProxyPaneItem.apply(ConnectionSettings.C_HTTP_PROXY, "proxy.example", 3128));
    assertEquals(List.of(), calls);
  }

  @Test
  void theProxyPageAppliesAChangeInTheBackgroundWithTheSavedLogin() throws Exception {
    saveProxy();
    onTheEdt(() -> ProxyPaneItem.apply(ConnectionSettings.C_SOCKS5_PROXY, "proxy.example", 1080));
    assertEquals(List.of("applySettings@" + BACKGROUND), calls);
    assertEquals(
        settings_pack.proxy_type_t.socks5_pw.swigValue(),
        applied.getInteger(settings_pack.int_types.proxy_type.swigValue()));
    assertEquals(1080, applied.getInteger(settings_pack.int_types.proxy_port.swigValue()));
    assertEquals("user", applied.getString(settings_pack.string_types.proxy_username.swigValue()));
  }

  @Test
  void theLoginPageLeavesTheEngineAloneWhenNothingChanged() throws Exception {
    saveProxy();
    onTheEdt(() -> ProxyLoginPaneItem.apply("user", "secret", true));
    assertEquals(List.of(), calls);
  }

  @Test
  void theLoginPageAppliesAChangeInTheBackgroundWithTheSavedProxy() throws Exception {
    saveProxy();
    onTheEdt(() -> ProxyLoginPaneItem.apply("user", "secret", false));
    assertEquals(List.of("applySettings@" + BACKGROUND), calls);
    assertEquals(
        settings_pack.proxy_type_t.http.swigValue(),
        applied.getInteger(settings_pack.int_types.proxy_type.swigValue()));
    assertEquals(
        "proxy.example", applied.getString(settings_pack.string_types.proxy_hostname.swigValue()));
  }

  private static void saveFirewall() {
    ConnectionSettings.MANUAL_PORT_RANGE.setValue(true);
    ConnectionSettings.PORT_RANGE_0.setValue(40000);
    ConnectionSettings.PORT_RANGE_1.setValue(40000);
    ConnectionSettings.NATPMP_GATEWAY.setValue("");
    ConnectionSettings.NATPMP_LEASE_DURATION.setValue(3600);
  }

  @Test
  void theFirewallPageLeavesTheEngineAloneWhenNothingChanged() throws Exception {
    saveFirewall();
    onTheEdt(() -> RouterConfigurationPaneItem.apply(true, 40000, 40000, "", 3600));
    assertEquals(List.of(), calls);
  }

  @Test
  void theFirewallPageAppliesANatPmpChangeInTheBackground() throws Exception {
    saveFirewall();
    onTheEdt(() -> RouterConfigurationPaneItem.apply(true, 40000, 40000, "192.168.1.1", 3600));
    assertEquals(List.of("applySettings@" + BACKGROUND), calls);
    assertEquals("192.168.1.1", applied.natpmpGateway());
  }

  @Test
  void theFirewallPageRebindsAPortChangeInTheBackground() throws Exception {
    saveFirewall();
    onTheEdt(() -> RouterConfigurationPaneItem.apply(true, 40001, 40001, "", 3600));
    assertEquals(List.of("listenInterfaces@" + BACKGROUND), calls);
    assertEquals(40001, ConnectionSettings.PORT_RANGE_0.getValue());
  }

  @Test
  void theNetworkInterfacePageLeavesTheEngineAloneWhenNothingChanged() throws Exception {
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.setValue(false);
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.setValue("0.0.0.0");
    // with the box unchecked, the list still selects its first address
    onTheEdt(() -> NetworkInterfacePaneItem.apply(false, "192.168.1.20"));
    assertEquals(List.of(), calls);
    assertEquals("0.0.0.0", ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue());
  }

  @Test
  void theNetworkInterfacePageRebindsAChangeInTheBackground() throws Exception {
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.setValue(false);
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.setValue("0.0.0.0");
    onTheEdt(() -> NetworkInterfacePaneItem.apply(true, "192.168.1.20"));
    assertEquals(List.of("listenInterfaces@" + BACKGROUND), calls);
    assertEquals("192.168.1.20", ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue());
  }
}
