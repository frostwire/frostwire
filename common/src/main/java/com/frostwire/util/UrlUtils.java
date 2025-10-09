/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UrlUtils {

    private static final Logger LOG = Logger.getLogger(UrlUtils.class);
    private final static Pattern infoHashPattern = Pattern.compile("([0-9A-Fa-f]{40})");

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

    /**
     * Hot-path method: URL-encodes a string and converts spaces to %20.
     * Optimized to use String.replace instead of replaceAll for literal plus-sign replacement,
     * avoiding regex compilation overhead.
     * 
     * @param s the string to encode, may be null
     * @return the URL-encoded string with spaces as %20, or empty string if input is null
     */
    public static String encode(String s) {
        if (s == null) {
            return "";
        }

        try {
            // gotta keep using the deprecated method because we need android min sdk to be 33, a long way to go
            // Using replace() instead of replaceAll() - it's a literal replacement, no regex needed
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            LOG.error("UrlUtils.encode() -> " + e.getMessage(), e);
            return "";
        }
    }

    public static String decode(String s) {
        if (s == null) {
            return "";
        }

        try {
            // gotta keep using the deprecated method because we need android min sdk to be 33, a long way to go
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            LOG.error("UrlUtils.decode() -> " + e.getMessage(), e);
            return "";
        }
    }

    public static String buildMagnetUrl(String infoHash, String displayFilename, String trackerParameters) {
        return "magnet:?xt=urn:btih:" + infoHash + "&dn=" + UrlUtils.encode(displayFilename) + "&" + trackerParameters;
    }


    // can't use java records yet because of android 11 being the max compatible version
    private static class
    MirrorHeadDuration {
        private final String mirror;
        private final long duration;

        public MirrorHeadDuration(String mirror, long duration) {
            this.mirror = mirror;
            this.duration = duration;
        }

        public String mirror() {
            return mirror;
        }

        public long duration() {
            return duration;
        }
    }

    public static long testHeadRequestDurationInMs(final HttpClient httpClient, String url, final int maxWaitInMs) {
        long t_a = System.currentTimeMillis();
        try {
            int httpCode = httpClient.head("https://" + url, maxWaitInMs, null);
            boolean validHttpCode = 100 <= httpCode && httpCode < 400;
            if (!validHttpCode) {
                LOG.error("UrlUtils.testHeadRequestDurationInMs() -> " + url + " errored HTTP " + httpCode + " in " + (System.currentTimeMillis() - t_a) + "ms");
                return maxWaitInMs * 10L; // return a big number as to never consider it
            }
        } catch (Throwable t) {
            LOG.error("UrlUtils.testHeadRequestDurationInMs() -> " + url + " errored " + t.getMessage());
        }
        return System.currentTimeMillis() - t_a;
    }

    public static String getFastestMirrorDomain(final HttpClient httpClient, final String[] mirrors, final int minResponseTimeInMs, int maxNumberOfMirrorsToTest) {
        String fastestMirror;
        ArrayList<String> mirrorList = new ArrayList<>(Arrays.asList(mirrors));
        Collections.shuffle(mirrorList);
        if (maxNumberOfMirrorsToTest > 0 && mirrorList.size() > maxNumberOfMirrorsToTest) {
            mirrorList = new ArrayList<>(mirrorList.subList(0, maxNumberOfMirrorsToTest));
        }
        final CountDownLatch latch = new CountDownLatch(mirrorList.size());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        final java.util.List<MirrorHeadDuration> synchronizedMirrorDurations = Collections.synchronizedList(new ArrayList<>());
        for (String randomMirror : mirrorList) {
            executor.submit(() -> {
                try {
                    long duration = testHeadRequestDurationInMs(httpClient, randomMirror, minResponseTimeInMs);
                    synchronizedMirrorDurations.add(
                            new MirrorHeadDuration(
                                    randomMirror,
                                    duration
                            )
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            executor.shutdown();
            return mirrors[0];
        } finally {
            executor.shutdown();
        }
        //filter out all null elements from mirrorDurations
        synchronizedMirrorDurations.removeIf(Objects::isNull);
        if (synchronizedMirrorDurations.isEmpty()) {
            return mirrors[0];
        }
        if (synchronizedMirrorDurations.size() > 1) {
            synchronizedMirrorDurations.sort((o1, o2) -> {
                if (o1.duration() < o2.duration()) {
                    return -1;
                } else if (o1.duration() > o2.duration()) {
                    return 1;
                }
                return 0;
            });
        }

        fastestMirror = synchronizedMirrorDurations.get(0).mirror();
        LOG.info("UrlUtils.getFastestMirrorDomain() -> fastest mirror is " + fastestMirror + " in " + synchronizedMirrorDurations.get(0).duration() + "ms");
        return fastestMirror;
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

    public static String extractInfoHash(String url) {
        Matcher matcher = infoHashPattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
