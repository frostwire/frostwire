/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1406: isWindowsAppStoreInstall asked the file system about AppxManifest.xml
 * on every call, and the shutdown window, menus and search call it on the EDT. Exiting FrostWire
 * stalled in that file check (Icebase #59508e8b537c). The answer is now read once.
 */
class OSUtilsAppStoreInstallTest {

  @BeforeEach
  @AfterEach
  void forgetTheAnswer() throws Exception {
    Field answer = OSUtils.class.getDeclaredField("windowsAppStoreInstall");
    answer.setAccessible(true);
    answer.set(null, null);
  }

  @Test
  void theManifestIsCheckedOnceHoweverOftenItIsAsked() {
    AtomicInteger diskChecks = new AtomicInteger();

    for (int i = 0; i < 5; i++) {
      assertTrue(
          OSUtils.windowsAppStoreInstall(
              () -> {
                diskChecks.incrementAndGet();
                return true;
              }));
    }

    assertEquals(1, diskChecks.get(), "AppxManifest.xml was looked up on every call");
  }

  @Test
  void aNegativeAnswerIsRememberedToo() {
    AtomicInteger diskChecks = new AtomicInteger();

    for (int i = 0; i < 3; i++) {
      OSUtils.windowsAppStoreInstall(
          () -> {
            diskChecks.incrementAndGet();
            return false;
          });
    }

    assertEquals(1, diskChecks.get());
  }
}
