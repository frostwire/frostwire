/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.frostwire.android.core.Constants;
import com.frostwire.search.engines.SearchEngineID;
import org.junit.Test;

/**
 * Android's side of the shared search engines (frostwire/frostwire#1002): its existing preference
 * keys, and engines FrostWire's servers switch off while the app runs.
 */
public class AndroidSearchEngineAdapterTest {

  @Test
  public void everyEngineKeepsThePreferenceAndroidAlreadySaves() {
    assertEquals(
        Constants.PREF_KEY_SEARCH_USE_TPB,
        AndroidSearchEngineAdapter.preferenceKey(SearchEngineID.TPB_ID));
    assertEquals(
        Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG,
        AndroidSearchEngineAdapter.preferenceKey(SearchEngineID.INTERNET_ARCHIVE_ID));
    assertEquals(
        Constants.PREF_KEY_SEARCH_USE_TELLURIDE_COURIER,
        AndroidSearchEngineAdapter.preferenceKey(SearchEngineID.TELLURIDE_ID));
    for (SearchEngineID id : SearchEngineID.values()) {
      assertTrue(id.name(), AndroidSearchEngineAdapter.preferenceKey(id).startsWith("frostwire."));
    }
  }

  @Test
  public void anEngineSwitchedOffRemotelyIsNotOfferedUntilSwitchedBackOn() {
    AndroidSearchEngineAdapter adapter = new AndroidSearchEngineAdapter();
    assertTrue(adapter.isOffered(SearchEngineID.NYAA_ID));

    adapter.setActive(SearchEngineID.NYAA_ID, false);
    assertFalse(adapter.isOffered(SearchEngineID.NYAA_ID));

    adapter.setActive(SearchEngineID.NYAA_ID, true);
    assertTrue(adapter.isOffered(SearchEngineID.NYAA_ID));
  }
}
