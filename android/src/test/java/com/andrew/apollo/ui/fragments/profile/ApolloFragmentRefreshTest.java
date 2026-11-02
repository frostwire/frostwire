/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments.profile;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import androidx.annotation.NonNull;
import androidx.loader.content.Loader;
import com.andrew.apollo.adapters.SongAdapter;
import com.andrew.apollo.model.Song;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * refresh() emptied the list and then asked for a reload through the 5-second throttle, so a second
 * refresh within 5 seconds (changing the sort order twice, removing two favorites) left the list
 * empty until something else reloaded it (GitHub #1461).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ApolloFragmentRefreshTest {

  /** Records the reloads refresh() asks for instead of running them. */
  public static final class RecordingFragment extends ApolloFragment<SongAdapter, Song> {
    final List<Boolean> restarts = new ArrayList<>();

    public RecordingFragment() {
      super(0, 0);
    }

    @Override
    public void restartLoader(boolean force) {
      restarts.add(force);
    }

    @Override
    protected SongAdapter createAdapter() {
      return null;
    }

    @Override
    protected String getLayoutTypeName() {
      return null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {}

    @NonNull
    @Override
    public Loader<List<Song>> onCreateLoader(int id, Bundle args) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void everyRefreshReloadsTheListItEmptied() {
    RecordingFragment fragment = new RecordingFragment();

    fragment.refresh();
    fragment.refresh();

    assertEquals(List.of(true, true), fragment.restarts);
  }
}
