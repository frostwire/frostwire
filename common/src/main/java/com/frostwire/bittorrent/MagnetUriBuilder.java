/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.Address;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.transfers.BittorrentDownload;

import java.io.File;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MagnetUriBuilder {

    private static Logger LOG = Logger.getLogger(MagnetUriBuilder.class);

    /** The x.pe-less magnet */
    private final String magnetUri;

    public MagnetUriBuilder(String torrentFilePathOrMagnetUri) {
        torrentFilePathOrMagnetUri = torrentFilePathOrMagnetUri.trim();
        if (torrentFilePathOrMagnetUri.startsWith("magnet:?") ||
            torrentFilePathOrMagnetUri.contains("xt=") ||
            torrentFilePathOrMagnetUri.contains("dn=") ||
            torrentFilePathOrMagnetUri.contains("kt=") ||
            torrentFilePathOrMagnetUri.contains("mt=") ||
            torrentFilePathOrMagnetUri.contains("xs=") ||
            torrentFilePathOrMagnetUri.contains("as=") ||
            torrentFilePathOrMagnetUri.contains("tr=")) {
            magnetUri = torrentFilePathOrMagnetUri;
        } else {
            magnetUri = extractMagnetUri(torrentFilePathOrMagnetUri);
        }
    }

    @SuppressWarnings("unused")
    public MagnetUriBuilder(BittorrentDownload download) {
         magnetUri = download.magnetUri();
    }

    // WIP: We still need to figure out the right ports for the right network interfaces.
    public String getMagnet() {
        String xpe = null;
        String resultUri = null;
        final BTEngine btEngine = BTEngine.getInstance();
        if (btEngine.getExternalAddress() != null && btEngine.getListenPort() != -1) {
            Address externalAddress = btEngine.getExternalAddress();
            xpe = externalAddress.toString() + ":" + btEngine.getListenPort();
        }
        LOG.info("creating magnet with:");
        if (xpe != null) {
            LOG.info("external address: " + xpe);
            resultUri = magnetUri + "&x.pe=" + xpe;
        }

        // since I don't know what the internal port is and usually
        // when ports are mapped they match, I'll also go through the list
        // of internal addresses
        if (btEngine.getListenPort() != -1) {
            if (resultUri == null) {
                resultUri = magnetUri;
            }
            try {
                final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

                if (networkInterfaces != null) {
                    while (networkInterfaces.hasMoreElements()) {
                        NetworkInterface iface = networkInterfaces.nextElement();
                        final List<InterfaceAddress> interfaceAddresses = iface.getInterfaceAddresses();
                        for (InterfaceAddress ifaceAddress : interfaceAddresses) {
                            String address = ifaceAddress.getAddress().getHostAddress();
                            if (!address.startsWith("::") &&
                                !address.equals("127.0.0.1") &&
                                !address.contains("dummy") &&
                                !address.contains("wlan") &&
                                !address.contains("0.0.0.0")) {
                                // IPv6 address should be expressed as [address]:port
                                if (address.contains(":")) {
                                    // remove the %22 or whatever mask at the end.
                                    if (address.contains("%")) {
                                        address = address.substring(0,address.indexOf("%"));
                                    }
                                    // surround with brackets.
                                    address = "[" + address + "]";
                                }
                                resultUri = resultUri + "&x.pe=" + address +":" + btEngine.getListenPort();
                                LOG.info(address + " : " + btEngine.getListenPort());
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        LOG.info(resultUri);
        return resultUri;
    }

    private static String extractMagnetUri(String torrentFilePath) {
        try {
            return new TorrentInfo(new File(torrentFilePath)).makeMagnetUri();
        } catch (Throwable e) {
            LOG.warn("Error trying to get magnet", e);
        }
        return null;
    }
}
