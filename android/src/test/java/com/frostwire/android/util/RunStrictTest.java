/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.StrictMode;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class RunStrictTest {

  @Test
  public void runStrictRunsTheRunnableOnce() {
    AtomicInteger runs = new AtomicInteger();
    RunStrict.runStrict(runs::incrementAndGet);
    assertEquals(1, runs.get());
  }

  @Test
  public void runStrictReturnsTheValue() {
    String result = RunStrict.runStrict((RunStrict<String>) () -> "value");
    assertEquals("value", result);
  }

  @Test
  public void exceptionsPropagateAndStrictPolicyIsStillRestored() {
    assertThrows(
        IllegalStateException.class,
        () ->
            RunStrict.runStrict(
                (Runnable)
                    () -> {
                      throw new IllegalStateException("boom");
                    }));
    // The finally block disables the policy, so a later strict run starts clean.
    assertEquals(7, (int) RunStrict.runStrict((RunStrict<Integer>) () -> 7));
  }

  @Test
  public void setStrictPolicyTogglesTheThreadPolicy() {
    String lax = StrictMode.getThreadPolicy().toString();
    RunStrict.setStrictPolicy(true);
    String strict = StrictMode.getThreadPolicy().toString();
    RunStrict.setStrictPolicy(false);
    assertNotEquals(lax, strict);
    assertEquals(lax, StrictMode.getThreadPolicy().toString());
  }

  @Test
  public void strictModePoliciesCanBeToggledOnTiramisuAndAbove() {
    String before = StrictMode.getThreadPolicy().toString();
    RunStrict.enableStrictModePolicies(true);
    String enabled = StrictMode.getThreadPolicy().toString();
    RunStrict.disableStrictModePolicyForUnbufferedIO();
    RunStrict.enableStrictModePolicies(false);
    assertNotEquals(before, enabled);
    assertTrue(enabled.length() > 0);
  }
}
