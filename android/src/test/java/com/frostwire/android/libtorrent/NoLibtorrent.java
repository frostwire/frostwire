/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.libtorrent;

import static org.mockito.Mockito.mockStatic;

import com.frostwire.bittorrent.BTEngine;
import java.util.function.Supplier;
import org.mockito.MockedStatic;

/**
 * What the app does on a device whose libtorrent library could not be loaded. Unit tests do load
 * libtorrent, so the failure is simulated where the app asks for the engine: {@link
 * BTEngine#getInstance()} fails with the link error the missing library causes.
 */
public final class NoLibtorrent {
  private NoLibtorrent() {}

  /**
   * Runs {@code action} while {@link BTEngine#getInstance()} throws {@link UnsatisfiedLinkError}.
   */
  public static <T> T call(Supplier<T> action) {
    try (MockedStatic<BTEngine> engines = mockStatic(BTEngine.class)) {
      engines
          .when(BTEngine::getInstance)
          .thenThrow(new UnsatisfiedLinkError("jlibtorrent native library is not available"));
      return action.get();
    }
  }

  /** {@link #call(Supplier)} for an action without a result. */
  public static void run(Runnable action) {
    call(
        () -> {
          action.run();
          return null;
        });
  }
}
