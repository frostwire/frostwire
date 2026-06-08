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

package com.frostwire.bittorrent;

import java.util.List;

/**
 * Default UDP BitTorrent trackers FrostWire uses when creating new torrents
 * and as fallbacks on magnet URLs from search results.
 *
 * <p>Single source of truth for the default announce list. Callers must
 * consume either {@link #ANNOUNCE_URLS} (bare URLs, for libtorrent
 * {@code create_torrent.add_tracker}) or {@link #MAGNET_URL_PARAMETERS}
 * (the {@code "tr=<url>&..."} form for magnet links). Do not duplicate this
 * list elsewhere.
 *
 * <p>The 16 URLs below responded to a BEP 15 connect_request probe on
 * 2026-06-08. See {@code DefaultTrackerListTest} for the regression that
 * keeps this list honest. To replace a dead tracker: remove it from
 * {@link #ANNOUNCE_URLS} and the derived magnet form updates for free.
 */
public final class DefaultTrackers {
    public static final List<String> ANNOUNCE_URLS = List.of(
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.publictracker.xyz:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://wepzone.net:6969/announce",
            "udp://uabits.today:6990/announce",
            "udp://tracker.wildkat.net:6969/announce",
            "udp://tracker.tryhackx.org:6969/announce",
            "udp://tracker.theoks.net:6969/announce",
            "udp://tracker.t-1.org:6969/announce",
            "udp://tracker.qu.ax:6969/announce",
            "udp://tracker.opentorrent.top:6969/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://tracker.corpscorp.online:80/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://tracker.auctor.tv:6969/announce"
    );

    public static final String MAGNET_URL_PARAMETERS = buildMagnetParameters();

    private DefaultTrackers() {
    }

    private static String buildMagnetParameters() {
        StringBuilder sb = new StringBuilder(ANNOUNCE_URLS.size() * 64);
        for (String url : ANNOUNCE_URLS) {
            sb.append("tr=").append(url).append('&');
        }
        return sb.toString();
    }
}
