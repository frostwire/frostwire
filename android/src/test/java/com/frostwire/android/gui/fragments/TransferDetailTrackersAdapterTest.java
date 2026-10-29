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

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import com.frostwire.android.gui.util.OffMainQuery;
import com.frostwire.jlibtorrent.AnnounceEntry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The trackers tab refreshes every second; reading the torrent's trackers waits for libtorrent's
 * network thread and froze the app on the main thread (frostwire/frostwire#1373).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailTrackersAdapterTest {

  @Test
  public void refreshingTheListReadsTheTrackersOffTheMainThread() throws Exception {
    List<Thread> readers = new ArrayList<>();
    List<Runnable> background = new ArrayList<>();
    TransferDetailTrackersFragment.TrackerListAdapter adapter =
        new TransferDetailTrackersFragment.TrackerListAdapter(
            null,
            null,
            new OffMainQuery<>(background::add, Runnable::run),
            () -> {
              readers.add(Thread.currentThread());
              return new ArrayList<AnnounceEntry>();
            });

    adapter.refreshFromTorrentHandle();

    assertTrue("the trackers were read on the main thread", readers.isEmpty());
    Thread worker = new Thread(background.remove(0));
    worker.start();
    worker.join();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(List.of(worker), readers);
  }
}
