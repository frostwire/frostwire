/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.tabs.Tab;
import com.limegroup.gnutella.gui.GUIMediator.Tabs;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * MainFrame builds the Transfers tabs in an EDT event after its constructor. A request to show
 * Transfers posted before the main window was created runs before that event, and found no
 * Transfers tab (NullPointerException in GUIMediator.showTransfers, frostwire/frostwire#1498).
 */
class MainFrameDeferredTabsTest {

  private final Map<Tabs, Tab> tabs = new EnumMap<>(Tabs.class);
  private final Tab transfers = fakeTab();
  private final AtomicInteger builds = new AtomicInteger();
  private final DeferredStep deferred =
      new DeferredStep(
          () -> {
            builds.incrementAndGet();
            tabs.put(Tabs.TRANSFERS, transfers);
          });

  @Test
  void askingForADeferredTabBeforeItsEventBuildsItNow() {
    assertSame(transfers, MainFrame.tabBuildingDeferred(tabs, Tabs.TRANSFERS, deferred));
    assertEquals(1, builds.get());

    deferred.run(); // the scheduled event comes later

    assertEquals(1, builds.get(), "the scheduled event doesn't build the tabs again");
    assertSame(transfers, MainFrame.tabBuildingDeferred(tabs, Tabs.TRANSFERS, deferred));
  }

  @Test
  void afterTheScheduledEventTheTabIsReadWithoutBuildingAgain() {
    deferred.run();

    assertSame(transfers, MainFrame.tabBuildingDeferred(tabs, Tabs.TRANSFERS, deferred));
    assertEquals(1, builds.get());
    assertTrue(deferred.isDone());
  }

  @Test
  void aTabTheDeferredStepDoesNotBuildStaysMissing() {
    assertNull(MainFrame.tabBuildingDeferred(tabs, Tabs.LIBRARY, deferred));
    assertNull(MainFrame.tabBuildingDeferred(tabs, Tabs.LIBRARY, null));
  }

  private static Tab fakeTab() {
    return (Tab)
        Proxy.newProxyInstance(
            Tab.class.getClassLoader(), new Class<?>[] {Tab.class}, (proxy, method, args) -> null);
  }
}
