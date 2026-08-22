/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import com.frostwire.jlibtorrent.TcpEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LibTorrentMagnetDownloaderTest {

    private static final String INFOHASH = "d8e8fca2dc0f896fd7cb4cb0031ba24900000000";

    @Test
    public void parsesXpeEndpointsFromMagnet() {
        String magnet = "magnet:?xt=urn:btih:" + INFOHASH
                + "&dn=test.bin"
                + "&x.pe=192.168.1.10:45321"
                + "&x.pe=76.130.145.63:45321";
        List<TcpEndpoint> peers = LibTorrentMagnetDownloader.parsePeers(magnet);
        assertEquals(2, peers.size());
        assertTrue(peers.stream().anyMatch(p -> "192.168.1.10:45321".equals(p.toString())));
        assertTrue(peers.stream().anyMatch(p -> "76.130.145.63:45321".equals(p.toString())));
    }

    @Test
    public void magnetWithoutXpeYieldsEmptyList() {
        String magnet = "magnet:?xt=urn:btih:" + INFOHASH + "&dn=test.bin";
        assertTrue(LibTorrentMagnetDownloader.parsePeers(magnet).isEmpty());
    }

    @Test
    public void nullEmptyAndHttpInputYieldEmptyList() {
        assertTrue(LibTorrentMagnetDownloader.parsePeers(null).isEmpty());
        assertTrue(LibTorrentMagnetDownloader.parsePeers("").isEmpty());
        assertTrue(LibTorrentMagnetDownloader.parsePeers("http://example.com/file.torrent").isEmpty());
    }

    @Test
    public void malformedMagnetFailsClosed() {
        assertTrue(LibTorrentMagnetDownloader.parsePeers("magnet:?not-a-valid-uri").isEmpty());
    }
}
