/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.tcp_endpoint_vector;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class LibTorrentMagnetDownloader implements MagnetDownloader {

    private static final Logger LOG = Logger.getLogger(LibTorrentMagnetDownloader.class);

    public LibTorrentMagnetDownloader() {
    }

    public byte[] download(String magnet, int timeout) {
        return BTEngine.getInstance().fetchMagnet(magnet, timeout, getTempDir());
    }

    /**
     * Extracts the {@code x.pe} bootstrap peers from a magnet URI.
     * Fails closed: any null/empty/http input or parse error yields an empty list.
     */
    public static List<TcpEndpoint> parsePeers(String magnetUri) {
        if (StringUtils.isNullOrEmpty(magnetUri) || magnetUri.startsWith("http")) {
            return Collections.emptyList();
        }
        try {
            error_code ec = new error_code();
            add_torrent_params params = add_torrent_params.parse_magnet_uri(magnetUri, ec);
            tcp_endpoint_vector v = params.get_peers();
            int size = (int) v.size();
            List<TcpEndpoint> peers = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                peers.add(new TcpEndpoint(v.get(i)));
            }
            return peers;
        } catch (Throwable t) {
            LOG.warn("Failed to parse peers from magnet URI", t);
            return Collections.emptyList();
        }
    }

    public static File getTempDir() {
        File fwDummy = null;
        try {
            fwDummy = File.createTempFile("fw_dummy_", ".txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fwDummy.getParentFile();
    }
}
