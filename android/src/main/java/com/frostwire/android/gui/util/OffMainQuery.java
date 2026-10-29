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

package com.frostwire.android.gui.util;

import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs a query on a background thread and hands its result to the UI thread, one at a time.
 *
 * <p>For the synchronous jlibtorrent handle queries the UI refreshes with ({@code trackers()},
 * {@code peerInfo()}): each waits for libtorrent's network thread, which can be busy with disk I/O
 * for seconds, so on the main thread they froze the app (frostwire/frostwire#1373). While a query
 * is in flight, further requests are dropped instead of queued, so a stalled libtorrent doesn't
 * pile up refreshes on the background thread.
 */
public final class OffMainQuery<T> {
  private static final Logger LOG = Logger.getLogger(OffMainQuery.class);

  private final Executor background;
  private final Executor ui;
  private final AtomicBoolean inFlight = new AtomicBoolean();

  public OffMainQuery(Executor background, Executor ui) {
    this.background = background;
    this.ui = ui;
  }

  /** Queries on the given handler thread and delivers results on the UI thread. */
  public static <T> OffMainQuery<T> on(SystemUtils.HandlerThreadName handlerThread) {
    return new OffMainQuery<>(
        r -> SystemUtils.postToHandler(handlerThread, r), SystemUtils::postToUIThread);
  }

  /**
   * Runs {@code query} in the background and passes its result to {@code onResult} on the UI
   * thread, unless the previous query hasn't delivered yet.
   */
  public void run(Supplier<T> query, Consumer<T> onResult) {
    if (!inFlight.compareAndSet(false, true)) {
      return;
    }
    background.execute(
        () -> {
          final T result;
          try {
            result = query.get();
          } catch (Throwable t) {
            inFlight.set(false);
            LOG.error("OffMainQuery: query failed: " + t.getMessage(), t);
            return;
          }
          ui.execute(
              () -> {
                inFlight.set(false);
                onResult.accept(result);
              });
        });
  }
}
