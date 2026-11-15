/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

import android.app.Application;
import com.frostwire.search.relay.IdentityKeys;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Distributed Search screen loads its identity in the background and posts the result to the
 * main thread; by then the screen may be gone (frostwire/frostwire#1569, Crashlytics
 * 525517e6fe2a17eed95afcd4317a8127: getString() on the detached fragment threw "not attached to a
 * context"). A fragment that isn't added has no host, the state a detached one is in.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class DistributedSearchPreferenceFragmentDetachedTest {

  @Test
  public void identityLoadedAfterDetachDoesNothing() {
    DistributedSearchPreferenceFragment fragment = new DistributedSearchPreferenceFragment();
    assertFalse(fragment.isAdded());

    // A loaded identity builds the karma row with getString(), with or without karma (a mock:
    // real keys need jlibtorrent's native library).
    fragment.onIdentityLoaded(mock(IdentityKeys.class), true, null, null, 3);
    fragment.onIdentityLoaded(mock(IdentityKeys.class), true, null, 7L, -1);
  }
}
