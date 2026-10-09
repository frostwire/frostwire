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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.search;

import android.content.Context;
import android.net.wifi.WifiManager;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.DiscoveredEndpoint;
import com.frostwire.search.relay.LanPeerBeacon;
import com.frostwire.search.relay.PeerDiscoverySource;
import com.frostwire.util.Logger;
import java.util.List;

/**
 * Finds FrostWire nodes on the same wifi network by wrapping the shared {@link LanPeerBeacon} with
 * the {@link WifiManager.MulticastLock} Android needs.
 *
 * <p>Android's wifi driver filters multicast packets to save battery, so a phone can send beacons
 * but never receives the desktop's unless a multicast lock is held. The lock lives exactly as long
 * as the beacon: it is acquired before the beacon starts and released when the relay stack closes.
 * Like the desktop, the beacon is only a hint; every endpoint it offers still has to pass the rUDP
 * identity handshake before it is trusted.
 */
final class AndroidLanBeacon implements PeerDiscoverySource, AutoCloseable {

  private static final Logger LOG = Logger.getLogger(AndroidLanBeacon.class);
  private static final String LOCK_TAG = "frostwire-lan-beacon";

  private final LanPeerBeacon beacon;
  private final WifiManager.MulticastLock multicastLock;

  private AndroidLanBeacon(LanPeerBeacon beacon, WifiManager.MulticastLock multicastLock) {
    this.beacon = beacon;
    this.multicastLock = multicastLock;
  }

  /**
   * Acquires the multicast lock and starts announcing {@code rudpPort} on the local network. Never
   * throws: a phone without wifi or multicast simply has LAN discovery off.
   */
  static AndroidLanBeacon start(Context context, int rudpPort) {
    WifiManager.MulticastLock lock = acquireMulticastLock(context);
    LanPeerBeacon beacon = new LanPeerBeacon(rudpPort);
    beacon.start();
    return new AndroidLanBeacon(beacon, lock);
  }

  private static WifiManager.MulticastLock acquireMulticastLock(Context context) {
    try {
      WifiManager wifi =
          (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      if (wifi == null) {
        return null;
      }
      WifiManager.MulticastLock lock = wifi.createMulticastLock(LOCK_TAG);
      lock.setReferenceCounted(false);
      lock.acquire();
      return lock;
    } catch (Throwable t) {
      LOG.warn("AndroidLanBeacon: could not acquire the wifi multicast lock", t);
      return null;
    }
  }

  @Override
  public List<DiscoveredEndpoint> fetchEndpoints() {
    return beacon.fetchEndpoints();
  }

  @Override
  public Entry fetchIdentityEntry(byte[] peerPub) {
    return beacon.fetchIdentityEntry(peerPub);
  }

  @Override
  public void close() {
    try {
      beacon.close();
    } finally {
      try {
        if (multicastLock != null && multicastLock.isHeld()) {
          multicastLock.release();
        }
      } catch (Throwable t) {
        LOG.warn("AndroidLanBeacon: could not release the wifi multicast lock", t);
      }
    }
  }
}
