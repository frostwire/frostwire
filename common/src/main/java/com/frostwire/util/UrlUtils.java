/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

import com.frostwire.util.http.HttpClient;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

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
        return "magnet:?xt=urn:btih:" + infoHash + "&dn=" + UrlUtils.encode(displayFilename) + "&" + trackerParameters;
    }

    public static String getFastestMirrorDomain(final HttpClient httpClient, final String[] mirrors, final int minResponseTimeInMs) {
        int httpCode;
        // shuffle mirrors, keep the fastest valid one
        long lowest_delta = minResponseTimeInMs * 10;
        long t_a, t_delta;
        String fastestMirror = null;
        ArrayList<String> mirrorList = new ArrayList(Arrays.asList(mirrors));
        Collections.shuffle(mirrorList);

        for (String randomMirror : mirrorList) {
            try {
                t_a = System.currentTimeMillis();
                httpCode = httpClient.head("https://" + randomMirror, minResponseTimeInMs, null);
                boolean validHttpCode = 100 <= httpCode && httpCode < 400;
                t_delta = System.currentTimeMillis() - t_a;
                if (!validHttpCode) {
                    System.err.println("UrlUtils.getFastestMirrorDomain() -> " + randomMirror + " errored HTTP " + httpCode + " in " + t_delta + "ms");
                } else if (validHttpCode && t_delta < minResponseTimeInMs) {
                    System.out.println("UrlUtils.getFastestMirrorDomain() -> " + randomMirror + " responded in " + t_delta + "ms");
                    if (t_delta < lowest_delta) {
                        lowest_delta = t_delta;
                        fastestMirror = randomMirror;
                        System.out.println("UrlUtils.getFastestMirrorDomain() -> " + randomMirror + " is new fastest mirror (" + t_delta + "ms)");
                    }
                } else {
                    System.out.println("UrlUtils.getFastestMirrorDomain() -> " + randomMirror + " too slow, responded in " + t_delta + "ms");
                }
            } catch (Throwable t) {
                System.err.println("UrlUtils.getFastestMirrorDomain(): " + randomMirror + " unreachable, not considered");
            }
        }

        if (fastestMirror != null) {
            System.out.println("UrlUtils.getFastestMirrorDomain() -> Winner: " + fastestMirror + " " + lowest_delta + " ms");
            return fastestMirror;
        }
        System.out.println("UrlUtils.getFastestMirrorDomain() -> Falling back to: " + mirrors[0]);
        return mirrors[0];
    }

    public static String extractDomainName(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
        return uri.getHost();
    }
}
