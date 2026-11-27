/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.offers;

import android.content.ContextWrapper;
import android.content.Intent;
import org.robolectric.Shadows;

/** UIUtils.openURL launches from a dedicated background thread; this waits for the launch. */
final class OfferTestSupport {

  private OfferTestSupport() {}

  /** The next activity started on the context, waiting up to 10 seconds for the launch thread. */
  static Intent nextStartedActivity(ContextWrapper context) throws InterruptedException {
    long deadline = System.nanoTime() + 10_000_000_000L;
    Intent started = Shadows.shadowOf(context).getNextStartedActivity();
    while (started == null && System.nanoTime() < deadline) {
      Thread.sleep(5);
      started = Shadows.shadowOf(context).getNextStartedActivity();
    }
    return started;
  }
}
