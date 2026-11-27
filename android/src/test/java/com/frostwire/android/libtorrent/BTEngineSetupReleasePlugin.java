/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.libtorrent;

import com.frostwire.bittorrent.BTEngine;
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin;

/**
 * Registered with Robolectric (META-INF/services), so it runs in every sandbox before every test.
 *
 * <p>{@link BTEngine#getInstance()} parks its caller until the app's startup code has set up the
 * engine's context. Now that libtorrent loads in unit tests, a test that reaches the engine through
 * production code (a transfer started from a dialog, a service asking for it) would wait for a
 * startup that never comes; before, the missing native library made the same call fail at once.
 * Releasing the latch makes the engine answer (not started, without a context) and leaves {@code
 * BTEngine.ctx} null, so code that checks for a started engine still sees none.
 */
public final class BTEngineSetupReleasePlugin implements TestEnvironmentLifecyclePlugin {
  @Override
  public void onSetupApplicationState() {
    try {
      BTEngine.onCtxSetupComplete();
    } catch (Throwable nativeLibraryMissing) {
      // a platform without a jlibtorrent native jar: the tests then see the old failure
    }
  }
}
