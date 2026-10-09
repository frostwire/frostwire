/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/**
 * The LAN beacon needs a wifi multicast lock to receive anything on Android, and the lock drains
 * battery if it leaks. These tests pin the lifecycle wiring: the beacon is only started for the
 * embedded node, it is a discovery source, and it is closed on both the normal and failed paths.
 */
public class AndroidLanBeaconWiringTest {

  @Test
  public void beaconIsStartedOnlyForTheEmbeddedNodeAndOffersEndpointsToDiscovery()
      throws Exception {
    String source = stack();

    assertTrue(source.contains("if(srv!=null&&meshRudpPort>0){"));
    assertTrue(source.contains("lanBeacon=AndroidLanBeacon.start(context,meshRudpPort);"));
    assertTrue(source.contains("discoverySources.add(lanBeacon);"));
    assertTrue(
        "the DHT stays a discovery source next to the beacon",
        source.contains("discoverySources.add(dhtDiscoverySource);"));
  }

  @Test
  public void beaconIsClosedWhenStartupFailsAndWhenTheStackCloses() throws Exception {
    String source = stack();

    assertEquals(
        "failed startup and closeBlocking each close the beacon",
        2,
        count(source, "lanBeacon.close()"));
    assertTrue("a successfully started stack takes ownership", source.contains("lanBeacon=null;"));
  }

  @Test
  public void multicastLockIsReleasedWithTheBeacon() throws Exception {
    String source = read("src/main/java/com/frostwire/android/search/AndroidLanBeacon.java");

    assertTrue(source.contains("lock.setReferenceCounted(false);"));
    assertTrue(source.contains("multicastLock.release();"));
    assertFalse(
        "the lock must not outlive close()",
        source.indexOf("release()") < source.indexOf("beacon.close()"));
  }

  private static String stack() throws Exception {
    return read("src/main/java/com/frostwire/android/search/AndroidRelayStack.java")
        .replaceAll("\\s+", "");
  }

  private static int count(String text, String needle) {
    int n = 0;
    for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
    return n;
  }

  private static String read(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
