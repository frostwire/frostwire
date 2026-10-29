/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.SearchableInfo;
import android.view.ContextThemeWrapper;
import androidx.appcompat.widget.SearchView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.gui.util.OffMainQuery;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The music player's menu looked up its search configuration, a binder call into the system's
 * search service, on the main thread (frostwire/frostwire#1374).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class BaseActivitySearchableInfoTest {

  @Test
  public void theSearchConfigurationIsLookedUpOffTheMainThread() throws Exception {
    SearchView searchView =
        new SearchView(
            new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                androidx.appcompat.R.style.Theme_AppCompat));
    List<Thread> lookups = new ArrayList<>();
    List<Runnable> background = new ArrayList<>();
    List<Runnable> ui = new ArrayList<>();

    BaseActivity.bindSearchableInfo(
        searchView,
        () -> {
          lookups.add(Thread.currentThread());
          return (SearchableInfo) null;
        },
        new OffMainQuery<>(background::add, ui::add));

    assertTrue("getSearchableInfo ran on the main thread", lookups.isEmpty());
    Thread worker = new Thread(background.remove(0));
    worker.start();
    worker.join();
    assertEquals(List.of(worker), lookups);
    assertEquals("the result is set on the UI thread", 1, ui.size());
    ui.remove(0).run();
  }
}
