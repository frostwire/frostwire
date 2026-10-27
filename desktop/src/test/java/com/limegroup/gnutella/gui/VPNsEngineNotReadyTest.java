/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.frostwire.bittorrent.BTEngine;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1321: VPNs.isVPNActive() is called on the EDT by VPN-Drop protection checks
 * on torrent actions. It used to reach the engine through BTEngine.getInstance(), which parks the
 * caller on a latch until startup sets up the engine. It must answer without waiting, and the VPN
 * status refresher must not report a guessed status to its EDT listeners before the engine is
 * ready.
 */
class VPNsEngineNotReadyTest {

  private static final long MAX_WAIT_MS = 3_000;

  @Test
  void vpnCheckDoesNotWaitForTheEngine() throws InterruptedException {
    assumeTrue(BTEngine.ctx == null, "engine context already set up in this JVM");
    AtomicBoolean active = new AtomicBoolean(true);
    CountDownLatch done = new CountDownLatch(1);
    // A daemon thread rather than the EDT: if the check ever waits for the engine again, the test
    // fails without leaving the shared EDT parked for the other tests in this JVM.
    Thread checker =
        new Thread(
            () -> {
              active.set(VPNs.isVPNActive());
              done.countDown();
            },
            "VPNsEngineNotReadyTest-checker");
    checker.setDaemon(true);
    checker.start();

    assertTrue(
        done.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS),
        "VPNs.isVPNActive() waited for BTEngine before startup set it up; thread state: "
            + checker.getState());
    assertFalse(active.get());
  }

  @Test
  void refresherReportsNothingBeforeTheEngineIsReady() throws Exception {
    assumeTrue(BTEngine.ctx == null, "engine context already set up in this JVM");
    List<Boolean> reports = new CopyOnWriteArrayList<>();
    VPNStatusRefresher refresher = VPNStatusRefresher.getInstance();
    SwingUtilities.invokeAndWait(
        () -> {
          refresher.addRefreshListener(reports::add);
          refresher.refresh();
        });
    // The refresher's check runs on the serial background queue; once a later task has run, the
    // check is done and any report it made is queued on the EDT ahead of the invokeAndWait below.
    BackgroundExecutorService.submit(() -> null).get(MAX_WAIT_MS, TimeUnit.MILLISECONDS);
    SwingUtilities.invokeAndWait(() -> {});
    assertEquals(List.of(), reports, "no VPN status may be reported before the engine is set up");
  }

  @Test
  void dropProtectionDoesNotWaitForTheEngine() throws InterruptedException {
    assumeTrue(BTEngine.ctx == null, "engine context already set up in this JVM");
    boolean saved = ConnectionSettings.VPN_DROP_PROTECTION.getValue();
    ConnectionSettings.VPN_DROP_PROTECTION.setValue(true);
    try {
      CountDownLatch done = new CountDownLatch(1);
      // Runs on the shared background queue from the status bar checkbox; a daemon thread here so
      // a regression fails the test without parking that queue for the other tests.
      Thread applier =
          new Thread(
              () -> {
                VPNDropGuard.applyVPNDropProtection();
                done.countDown();
              },
              "VPNsEngineNotReadyTest-applier");
      applier.setDaemon(true);
      applier.start();
      assertTrue(
          done.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS),
          "VPN-Drop protection waited for BTEngine before startup set it up; thread state: "
              + applier.getState());
    } finally {
      ConnectionSettings.VPN_DROP_PROTECTION.setValue(saved);
    }
  }
}
