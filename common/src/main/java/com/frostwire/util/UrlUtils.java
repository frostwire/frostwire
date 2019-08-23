/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UrlUtils {
    public static final String USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS = "tr=udp://tracker.leechers-paradise.org:6969/announce&" +
            "tr=udp://tracker.coppersurfer.tk:6969/announce&" +
            "tr=udp://tracker.internetwarriors.net:1337/announce&" +
            "tr=udp://retracker.akado-ural.ru:80/announce&" +
            "tr=udp://tracker.moeking.me:6969/announce&" +
            "tr=udp://carapax.net:6969/announce&" +
            "tr=udp://retracker.baikal-telecom.net:2710/announce&" +
            "tr=udp://bt.dy20188.com:80/announce&" +
            "tr=udp://tracker.nyaa.uk:6969/announce&" +
            "tr=udp://carapax.net:6969/announce&" +
            "tr=udp://amigacity.xyz:6969/announce&" +
            "tr=udp://tracker.supertracker.net:1337/announce&" +
            "tr=udp://tracker.cyberia.is:6969/announce&" +
            "tr=udp://tracker.openbittorrent.com:80/announce&" +
            "tr=udp://tracker.msm8916.com:6969/announce&" +
            "tr=udp://tracker.sktorrent.net:6969/announce&";
    private UrlUtils() {
    }

    public static String encode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String decode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String buildMagnetUrl(String infoHash, String displayFilename, String trackerParameters) {
        return"magnet:?xt=urn:btih:" + infoHash + "&dn=" + UrlUtils.encode(displayFilename) + "&" + trackerParameters;
    }
}
