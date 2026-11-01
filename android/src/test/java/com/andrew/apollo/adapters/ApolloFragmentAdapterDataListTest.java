/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.adapters;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The music lists went blank when the user came back to the app: the loader delivers its cached
 * list again, and the adapter, which kept that list by reference, had just cleared it while
 * unloading (GitHub #1444). The adapter now shows a copy.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ApolloFragmentAdapterDataListTest {

  private static final class Adapter extends ApolloFragmentAdapter<String> {
    Adapter() {
      super(ApplicationProvider.getApplicationContext(), 0, 0);
    }

    /** No header row, like the music lists. */
    @Override
    public int getItemViewType(int position) {
      return ITEM_VIEW_TYPE_MUSIC;
    }
  }

  @Test
  public void theLoadersListSurvivesUnloadAndShowsAgainWhenRedelivered() {
    List<String> loaderList = new ArrayList<>(List.of("a", "b", "c"));
    Adapter adapter = new Adapter();

    adapter.setDataList(loaderList);
    assertEquals(3, adapter.getCount());

    // ApolloFragment.onLoadFinished unloads, then sets the same list again.
    adapter.unload();
    assertEquals("unloading must not empty the loader's list", 3, loaderList.size());
    adapter.setDataList(loaderList);
    assertEquals(3, adapter.getCount());
  }

  @Test
  public void removingFromTheAdapterLeavesTheLoadersListAlone() {
    List<String> loaderList = new ArrayList<>(List.of("a", "b"));
    Adapter adapter = new Adapter();
    adapter.setDataList(loaderList);

    adapter.mDataList.remove("a");

    assertEquals(2, loaderList.size());
  }
}
