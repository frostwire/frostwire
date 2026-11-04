/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.swig.torrent_handle;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes a torrent alert to the listeners registered for its torrent, so the engine needs one
 * session listener for all downloads. A torrent can have several: the engine makes a new {@link
 * BTDownload} for it on each update, and the transfers list keeps listening to the first one.
 *
 * <p>Each download used to register its own session listener and drop the other torrents' alerts.
 * With N transfers every alert made N listener calls and N JNI handle comparisons, and jlibtorrent
 * chains its listeners N deep: removing one recursed N levels and threw {@code StackOverflowError}
 * for users with thousands of transfers (GitHub #1441).
 *
 * <p>Lookup is by the handle's id, confirmed with a handle comparison because an id is the torrent
 * object's address and can be reused. An alert whose torrent is already gone (id 0, as for a
 * removed torrent) or whose id isn't found falls back to comparing it with every registered handle,
 * which is what each listener did before.
 *
 * <p>Thread-safe: listeners are added and removed from any thread while the alert thread
 * dispatches.
 *
 * @param <H> the native handle type; {@link Handles} reads ids and compares handles
 */
final class TorrentAlertRouter<H> {

  /** Reads a handle's id and compares handles; native calls in production, fakes in tests. */
  interface Handles<H> {
    /** The handle's id, or 0 when its torrent no longer exists. */
    long id(H handle);

    /** True when both handles point to the same torrent, even after it was removed. */
    boolean same(H a, H b);
  }

  /**
   * libtorrent's handles. {@code id()} is the torrent object's address shifted right by 10 bits,
   * and 0 once the torrent is gone. {@code op_eq} compares the handles' {@code weak_ptr} owners, so
   * it still matches after the torrent is removed, and no new torrent can share an owner while a
   * handle to the old one exists.
   */
  static final Handles<torrent_handle> NATIVE_HANDLES =
      new Handles<>() {
        @Override
        public long id(torrent_handle handle) {
          return handle.id();
        }

        @Override
        public boolean same(torrent_handle a, torrent_handle b) {
          return a.op_eq(b);
        }
      };

  private static final class Route<H> {
    final H handle;
    final long id;
    final AlertListener listener;

    Route(H handle, long id, AlertListener listener) {
      this.handle = handle;
      this.id = id;
      this.listener = listener;
    }
  }

  private final Handles<H> handles;
  // A torrent can have several downloads: the engine makes a new one on each update.
  private final Map<Long, Set<Route<H>>> byId = new ConcurrentHashMap<>();
  private final Set<Route<H>> routes = ConcurrentHashMap.newKeySet();

  TorrentAlertRouter(Handles<H> handles) {
    this.handles = handles;
  }

  /** Sends {@code handle}'s alerts to {@code listener} until {@link #remove} is called. */
  void add(H handle, AlertListener listener) {
    long id = handles.id(handle);
    Route<H> route = new Route<>(handle, id, listener);
    routes.add(route);
    if (id != 0) {
      byId.compute(
          id,
          (k, sameId) -> {
            Set<Route<H>> routesOfId = sameId != null ? sameId : ConcurrentHashMap.newKeySet();
            routesOfId.add(route);
            return routesOfId;
          });
    }
  }

  /** Stops routing alerts to {@code listener}. Unknown listeners are ignored. */
  void remove(AlertListener listener) {
    for (Route<H> route : routes) {
      if (route.listener == listener) {
        routes.remove(route);
        if (route.id != 0) {
          byId.computeIfPresent(
              route.id,
              (id, sameId) -> {
                sameId.remove(route);
                return sameId.isEmpty() ? null : sameId;
              });
        }
      }
    }
  }

  /** Delivers {@code alert}, raised for {@code handle}, to the listeners of that torrent. */
  void dispatch(H handle, Alert<?> alert) {
    long id = handles.id(handle);
    Set<Route<H>> sameId = id != 0 ? byId.get(id) : null;
    boolean delivered = false;
    if (sameId != null) {
      for (Route<H> route : sameId) {
        if (handles.same(route.handle, handle)) {
          route.listener.alert(alert);
          delivered = true;
        }
      }
    }
    if (delivered) {
      return;
    }
    for (Route<H> candidate : routes) {
      if (handles.same(candidate.handle, handle)) {
        candidate.listener.alert(alert);
      }
    }
  }

  /** The number of registered listeners. */
  int size() {
    return routes.size();
  }
}
