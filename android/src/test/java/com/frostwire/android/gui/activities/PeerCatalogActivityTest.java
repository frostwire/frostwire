/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.app.Application;
import com.frostwire.android.util.Debug;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PeerCatalogActivityTest {

  @Test
  public void catalogLoadTaskIsAcceptedByTheEngineThreadPoolContextCheck() {
    // "Browse Shared Torrents" crashed: EngineThreadPool rejects tasks that pin a Context, and
    // the fetch lambda captured the activity.
    assertTrue("debug builds run the context-leak check", Debug.isEnabled());
    PeerCatalogActivity activity = mock(PeerCatalogActivity.class);
    Runnable capturing = () -> activity.getTitle();
    assertTrue("sanity: a task holding the activity is flagged", Debug.hasContext(capturing));

    assertFalse(Debug.hasContext(new PeerCatalogActivity.CatalogLoadTask(activity, new byte[32])));
  }
}
