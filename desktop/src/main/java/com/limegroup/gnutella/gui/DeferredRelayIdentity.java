/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.util.BackgroundPriority;
import com.frostwire.util.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Loads (or, on a first run, mines) the node's relay identity only after the whole startup sequence
 * has finished, on a dedicated daemon thread at below-normal priority (frostwire/frostwire#1534).
 *
 * <p>Mining takes about a million key generations; on a single-CPU computer doing it during startup
 * kept the window unresponsive and held back the restoration of saved downloads. Nothing in startup
 * waits for it: {@link #startAfterStartup()} returns immediately, and the work that needs the
 * identity (installing the torrent indexer, re-indexing the restored downloads, starting the relay
 * services) is done by {@code onReady} once the identity exists.
 */
final class DeferredRelayIdentity {
  private static final Logger LOG = Logger.getLogger(DeferredRelayIdentity.class);

  /** Reads the identity from disk, or mines a new one. */
  interface Loader {
    IdentityKeys load() throws Exception;
  }

  private final Loader loader;
  private final Consumer<IdentityKeys> onReady;
  private final AtomicBoolean started = new AtomicBoolean();

  DeferredRelayIdentity(Loader loader, Consumer<IdentityKeys> onReady) {
    if (loader == null || onReady == null) {
      throw new IllegalArgumentException("loader and onReady are required");
    }
    this.loader = loader;
    this.onReady = onReady;
  }

  /**
   * To be called once, when the startup sequence is over and the UI is responsive. Returns at once;
   * a second call does nothing.
   *
   * @return the worker thread, or null if already started or it could not be started
   */
  Thread startAfterStartup() {
    if (!started.compareAndSet(false, true)) {
      return null;
    }
    Thread worker = new Thread(this::run, "RelayIdentityMiner");
    worker.setDaemon(true);
    try {
      worker.start();
    } catch (Throwable t) {
      LOG.warn("Could not start the identity thread; distributed search disabled", t);
      return null;
    }
    return worker;
  }

  private void run() {
    IdentityKeys identity;
    try {
      identity = BackgroundPriority.call(loader::load);
    } catch (Throwable t) {
      LOG.warn("Failed to load the identity; distributed search disabled", t);
      return;
    }
    if (identity == null) {
      LOG.warn("No identity available; distributed search disabled");
      return;
    }
    try {
      onReady.accept(identity);
    } catch (Throwable t) {
      LOG.warn("Failed to use the identity; distributed search disabled", t);
    }
  }
}
