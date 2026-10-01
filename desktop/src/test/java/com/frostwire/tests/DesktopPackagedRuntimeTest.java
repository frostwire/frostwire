/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DesktopPackagedRuntimeTest {

  private static final String METRICS = "com.frostwire.search.relay.icebridge.IceBridgeMetrics";
  private static final String CLIENT =
      "com.frostwire.search.relay.icebridge.client.IceBridgeClient";
  private static final String TRANSPORT =
      "com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport";
  private static final String SEARCH_ENGINE = "com.limegroup.gnutella.gui.search.SearchEngine";

  @Test
  void packagedStartupCanConstructMetricsAndWireTransportWithoutDaemon() throws Exception {
    try (URLClassLoader loader = packagedLoader()) {
      Class<?> metricsClass = loader.loadClass(METRICS);
      assertFromApplicationJar(metricsClass, loader);
      Object metrics = metricsClass.getConstructor().newInstance();
      metricsClass.getMethod("incrementTransportPollRuns", long.class).invoke(metrics, 1L);
      assertEquals(1L, metricsClass.getMethod("transportPollRunsCount").invoke(metrics));

      Class<?> clientClass = loader.loadClass(CLIENT);
      Class<?> transportClass = loader.loadClass(TRANSPORT);
      assertFromApplicationJar(clientClass, loader);
      assertFromApplicationJar(transportClass, loader);
      // Constructors and metrics injection must work before a daemon exists. Do not start
      // the poller or register an identity: both would issue control-plane HTTP requests.
      try (AutoCloseable client =
          (AutoCloseable) clientClass.getConstructor(int.class).newInstance(1)) {
        try (AutoCloseable transport =
            (AutoCloseable) transportClass.getConstructor(clientClass).newInstance(client)) {
          transportClass.getMethod("setMetrics", metricsClass).invoke(transport, metrics);
          assertSame(client, transportClass.getMethod("client").invoke(transport));
        }
      }
    }
  }

  @Test
  void packagedTransportAndSearchEngineResolveMetricsSignatures() throws Exception {
    try (URLClassLoader loader = packagedLoader()) {
      // Resolve real bytecode signatures without initializing the UI/settings enum.
      // With metrics excluded, getDeclaredMethods fails with NoClassDefFoundError.
      Class<?> searchEngine = Class.forName(SEARCH_ENGINE, false, loader);
      Class<?> transport = Class.forName(TRANSPORT, false, loader);
      assertTrue(searchEngine.getDeclaredMethods().length > 0);
      assertTrue(transport.getDeclaredMethods().length > 0);
      Class<?> metrics = loader.loadClass(METRICS);
      assertFromApplicationJar(searchEngine, loader);
      assertFromApplicationJar(transport, loader);
      assertFromApplicationJar(metrics, loader);
      assertEquals(void.class, transport.getMethod("setMetrics", metrics).getReturnType());
      assertEquals(
          void.class,
          searchEngine.getMethod("setDistributedTransportMetrics", metrics).getReturnType());
      assertSame(metrics, searchEngine.getMethod("getDistributedTransportMetrics").getReturnType());
      assertSame(metrics, searchEngine.getDeclaredField("transportMetrics").getType());
    }
  }

  private static URLClassLoader packagedLoader() throws Exception {
    String jarPath = System.getProperty("fw.test.packagedJar");
    assertNotNull(jarPath, "The test task must supply its freshly built application JAR");
    Path jar = Path.of(jarPath);
    assertTrue(Files.isRegularFile(jar), "Missing packaged application: " + jar);
    // Only frostwire.jar is visible: neither development output nor icebridge.jar can
    // mask a missing in-process dependency through the test worker's classpath.
    return new URLClassLoader(
        new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
  }

  private static void assertFromApplicationJar(Class<?> type, URLClassLoader loader) {
    assertSame(loader, type.getClassLoader());
    assertEquals(loader.getURLs()[0], type.getProtectionDomain().getCodeSource().getLocation());
  }
}
