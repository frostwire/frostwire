/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui;

import android.app.Application;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;

/**
 * What the Apollo screens expect from {@code MainApplication} when a test runs them under a plain
 * {@link Application}: the platform that knows the UI thread and the configuration manager.
 */
public final class ApolloUiTestSupport {

  private ApolloUiTestSupport() {}

  /** A platform whose only behavior is telling the main looper's thread from the others. */
  public static void installPlatform() {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
  }

  /** The real configuration manager, created once per test JVM sandbox as the app does. */
  public static void installConfiguration() {
    try {
      ConfigurationManager.create(ApplicationProvider.getApplicationContext());
    } catch (RuntimeException alreadyCreated) {
      // created by an earlier test in this sandbox
    }
    ConfigurationManager.instance();
  }

  /**
   * Runs the main looper until the condition holds, for work that hops to a background handler
   * thread and back; fails after ten seconds.
   */
  public static void waitUntil(String what, java.util.function.BooleanSupplier condition) {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      try {
        Thread.sleep(2);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (!condition.getAsBoolean()) {
      throw new AssertionError("timed out waiting for " + what);
    }
  }

  /** Lets work already posted to the main looper, and what it posts in turn, run. */
  public static void settle() {
    for (int i = 0; i < 5; i++) {
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
    }
  }

  public static void installAll() {
    installPlatform();
    installConfiguration();
  }
}
