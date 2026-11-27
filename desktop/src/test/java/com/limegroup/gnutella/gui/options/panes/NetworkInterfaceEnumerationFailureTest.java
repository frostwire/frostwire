/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.EnumNet;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A failed interface enumeration must not turn off a saved custom interface (#1657). */
class NetworkInterfaceEnumerationFailureTest {
  private final Supplier<List<EnumNet.IpInterface>> savedInterfaces =
      NetworkInterfacePaneItem.interfaces;

  @AfterEach
  void restore() {
    NetworkInterfacePaneItem.interfaces = savedInterfaces;
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.revertToDefault();
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.revertToDefault();
  }

  @Test
  void aSavedCustomInterfaceSurvivesAFailedEnumeration() throws Exception {
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.setValue(true);
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.setValue("10.0.0.2");
    NetworkInterfacePaneItem.interfaces =
        () -> {
          throw new IllegalStateException("no interfaces");
        };
    NetworkInterfacePaneItem pane = PaneSupport.onEdt(NetworkInterfacePaneItem::new);
    pane.initOptions();
    assertFalse(pane.isDirty(), "not dirty on open");
    assertFalse(pane.applyOptions());
    assertTrue(ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.getValue(), "still on");
    assertTrue(PaneSupport.only(pane.getContainer(), JCheckBox.class).isSelected());
  }
}
