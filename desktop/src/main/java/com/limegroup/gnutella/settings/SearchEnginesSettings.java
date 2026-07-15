/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.StringSetting;

/**
 * @author gubatron
 * @author aldenml
 */
public class SearchEnginesSettings extends LimeProps {
  // In the near future, we will refactor this code to allow a configurable amount of
  // search providers.
  public static final BooleanSetting TPB_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("TPB_SEARCH2_ENABLED", true);
  public static final BooleanSetting SOUNDCLOUD_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("SOUNDCLOUD_SEARCH2_ENABLED", true);
  public static final BooleanSetting INTERNET_ARCHIVE_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("ARCHIVEORG_SEARCH2_ENABLED", true);
  public static final BooleanSetting FROSTCLICK_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("FROSTCLICK_SEARCH_ENABLED", true);
  public static final BooleanSetting TORRENTDOWNLOADS_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("TORRENTDOWNLOADS_SEARCH_ENABLED", true);
  public static final BooleanSetting ONE337X_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("ONE337X_SEARCH_ENABLED", true);
  public static final BooleanSetting TORRENTZ2_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("TORRENTZ2_SEARCH_ENABLED", true);
  public static final BooleanSetting NYAA_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("NYAA_SEARCH_ENABLED", true);
  public static final BooleanSetting MAGNETDL_ENABLED =
      FACTORY.createBooleanSetting("MAGNETDL_ENABLED", true);
  public static final BooleanSetting TELLURIDE_ENABLED =
      (BooleanSetting) FACTORY.createBooleanSetting("TELLURIDE_ENABLED", true).setAlwaysSave(true);
  public static final BooleanSetting YT_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("YT_SEARCH_ENABLED", true);
  public static final BooleanSetting TORRENTSCSV_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("TORRENTSCSV_SEARCH_ENABLED", true);
  public static final BooleanSetting KNABEN_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("KNABEN_SEARCH_ENABLED", false);
  public static final BooleanSetting BITSEARCH_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("BITSEARCH_SEARCH_ENABLED", true);
  /**
   * Diagnostic only: Local search queries this node's {@code LocalIndex} — the
   * same index that answers inbound IceBridge distributed search requests. It
   * does not contact peers. Default off; enable when testing what this node
   * would serve remotely.
   */
  public static final BooleanSetting LOCAL_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("LOCAL_SEARCH_ENABLED", false);
  /**
   * When false (default), Local search and answers to remote distributed
   * search only include torrents still in the transfer table with metadata
   * that are seeding or actively downloading (in the swarm). When true,
   * historical LocalIndex rows may appear even if no longer transferring.
   */
  public static final BooleanSetting LOCAL_SEARCH_INCLUDE_INACTIVE =
      FACTORY.createBooleanSetting("LOCAL_SEARCH_INCLUDE_INACTIVE", false);
  public static final BooleanSetting DISTRIBUTED_SEARCH_ENABLED =
      FACTORY.createBooleanSetting("DISTRIBUTED_SEARCH_ENABLED", false);

  /**
   * Bind host for the IceBridge rUDP server. Use "0.0.0.0" (default) to accept rUDP from remote
   * peers (cloud forwarder mode / public relay). Use "127.0.0.1" for local-only daemon mode. The
   * control HTTP server always binds to 127.0.0.1 regardless of this setting.
   */
  public static final StringSetting ICEBRIDGE_BIND_HOST =
      (StringSetting)
          FACTORY.createStringSetting("ICEBRIDGE_BIND_HOST", "0.0.0.0").setAlwaysSave(true);

  public static final BooleanSetting ICEBRIDGE_ENABLED =
      FACTORY.createBooleanSetting("ICEBRIDGE_ENABLED", true);

  public static final BooleanSetting ICEBRIDGE_USE_REMOTE =
      FACTORY.createBooleanSetting("ICEBRIDGE_USE_REMOTE", false);

  public static final StringSetting ICEBRIDGE_REMOTE_URL =
      FACTORY.createStringSetting("ICEBRIDGE_REMOTE_URL", "");

  public static final StringSetting ICEBRIDGE_REMOTE_AUTH_TOKEN =
      FACTORY.createStringSetting("ICEBRIDGE_REMOTE_AUTH_TOKEN", "");

  public static final IntSetting ICEBRIDGE_RUDP_PORT =
      FACTORY.createIntSetting("ICEBRIDGE_RUDP_PORT", 6889);

  public static final StringSetting ICEBRIDGE_ROLE =
      FACTORY.createStringSetting("ICEBRIDGE_ROLE", "BOTH");

  public static final IntSetting ICEBRIDGE_CONTROL_HTTP_PORT =
      FACTORY.createIntSetting("ICEBRIDGE_CONTROL_HTTP_PORT", 0); // 0 = auto

  /**
   * TCP port for the direct relay identity handshake server (also advertised in IdentityRecord).
   * Default 6888. Must be different from bittorrent ports.
   */
  public static final IntSetting ICEBRIDGE_RELAY_LISTEN_PORT =
      FACTORY.createIntSetting("ICEBRIDGE_RELAY_LISTEN_PORT", 6888);
}
