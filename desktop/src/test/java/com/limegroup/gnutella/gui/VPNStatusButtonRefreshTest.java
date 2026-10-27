/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.limegroup.gnutella.settings.ConnectionSettings;
import java.awt.Component;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Icebase #4e805e1c9aba, frostwire/frostwire#1321: VPNStatusRefresher reports the VPN status on the
 * EDT every refresh, and the button used to removeAll() and re-add its children each time,
 * invalidating and re-laying out the whole status bar even when nothing changed. Repeated reports
 * of the same status must not touch the children or the icon.
 */
class VPNStatusButtonRefreshTest {

  private boolean savedDropProtection;
  private VPNStatusButton button;
  private final AtomicInteger added = new AtomicInteger();
  private final AtomicInteger removed = new AtomicInteger();
  private final AtomicInteger iconChanges = new AtomicInteger();

  @BeforeEach
  void setUp() throws Exception {
    savedDropProtection = ConnectionSettings.VPN_DROP_PROTECTION.getValue();
    SwingUtilities.invokeAndWait(
        () -> {
          button = new VPNStatusButton();
          button.addContainerListener(
              new ContainerListener() {
                @Override
                public void componentAdded(ContainerEvent e) {
                  added.incrementAndGet();
                }

                @Override
                public void componentRemoved(ContainerEvent e) {
                  removed.incrementAndGet();
                }
              });
        });
  }

  @AfterEach
  void tearDown() {
    ConnectionSettings.VPN_DROP_PROTECTION.setValue(savedDropProtection);
  }

  @Test
  void repeatedSameStatusLeavesChildrenAndIconAlone() throws Exception {
    ConnectionSettings.VPN_DROP_PROTECTION.setValue(false);
    onEdt(() -> button.onStatusUpdated(true));
    onEdt(this::watchIcon);
    assertEquals(1, added.get(), "first report adds the icon");

    for (int i = 0; i < 5; i++) {
      onEdt(() -> button.onStatusUpdated(true));
    }
    assertEquals(1, added.get(), "same status must not re-add children");
    assertEquals(0, removed.get(), "same status must not remove children");
    assertEquals(0, iconChanges.get(), "same status must not reset the icon");

    onEdt(() -> button.onStatusUpdated(false));
    assertEquals(1, iconChanges.get(), "a status change updates the icon in place");
    assertEquals(1, added.get());
    assertEquals(0, removed.get());
  }

  @Test
  void dropGuardLabelIsAddedAndRemovedOnlyWhenItsVisibilityChanges() throws Exception {
    ConnectionSettings.VPN_DROP_PROTECTION.setValue(true);
    onEdt(() -> button.onStatusUpdated(false));
    assertEquals(2, added.get(), "icon and the 'BitTorrent disabled' label");

    for (int i = 0; i < 5; i++) {
      onEdt(() -> button.onStatusUpdated(false));
    }
    assertEquals(2, added.get());
    assertEquals(0, removed.get());

    onEdt(() -> button.onStatusUpdated(true));
    assertEquals(2, added.get());
    assertEquals(1, removed.get(), "VPN back on removes only the label");
    assertEquals(1, button.getComponentCount());
  }

  private void watchIcon() {
    Component icon = button.getComponent(0);
    assertSame(IconButton.class, icon.getClass());
    icon.addPropertyChangeListener("icon", e -> iconChanges.incrementAndGet());
  }

  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }
}
