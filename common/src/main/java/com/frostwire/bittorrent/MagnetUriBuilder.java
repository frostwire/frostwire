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
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.transfers.BittorrentDownload;

import java.io.File;
import java.net.*;
import java.util.Enumeration;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MagnetUriBuilder {

    private static Logger LOG = Logger.getLogger(MagnetUriBuilder.class);

    /**
     * The x.pe-less magnet
     */
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

    public String getMagnet() {

        List<TcpEndpoint> listenEndpoints = BTEngine.getInstance().listenEndpoints();
        if (listenEndpoints.isEmpty()) {
            return "";
        }

        Address external = BTEngine.getInstance().externalAddress();

        String magnetUriEx = magnetUri + buildPeersParameters(listenEndpoints, external);
        //System.out.println(magnetUriEx);
        return magnetUriEx;
    }

    private static String buildPeersParameters(List<TcpEndpoint> endpoints, Address external) {
        if (endpoints.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (TcpEndpoint endp : endpoints) {
            try {
                processEndpoint(endp, external, sb);
            } catch (Throwable e) {
                LOG.error("Error processing listen endpoint", e);
            }
        }

        return sb.toString();
    }

    private static void processEndpoint(TcpEndpoint endp, Address external, StringBuilder sb) {
        Address address = endp.address();
        if (address.isLoopback() || address.isMulticast()) {
            return;
        }

        if (address.isUnspecified()) {
            try {
                addAllInterfaces(endp, sb);
            } catch (Throwable e) {
                LOG.error("Error adding all listen interfaces", e);
            }
        } else {
            addEndpoint(endp.address(), endp.port(), sb);
        }

        if (external != null) {
            addEndpoint(external, endp.port(), sb);
        }
    }

    private static void addAllInterfaces(TcpEndpoint endp, StringBuilder sb) throws SocketException {
        Address address = endp.address();

        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface iface = networkInterfaces.nextElement();
            if (iface.isLoopback()) {
                continue;
            }
            List<InterfaceAddress> interfaceAddresses = iface.getInterfaceAddresses();
            for (InterfaceAddress ifaceAddress : interfaceAddresses) {
                InetAddress inetAddress = ifaceAddress.getAddress();

                if (inetAddress.isLoopbackAddress()) {
                    continue;
                }

                boolean isIPv6 = inetAddress instanceof Inet6Address;

                // same family?
                if (!isIPv6 && !address.isV4()) {
                    continue;
                }
                if (isIPv6 && !address.isV6()) {
                    continue;
                }

                String hostAddress = ifaceAddress.getAddress().getHostAddress();

                // IPv6 address should be expressed as [address]:port
                if (isIPv6) {
                    // remove the %22 or whatever mask at the end.
                    if (hostAddress.contains("%")) {
                        hostAddress = hostAddress.substring(0, hostAddress.indexOf("%"));
                    }
                    // surround with brackets.
                    hostAddress = "[" + hostAddress + "]";
                }
                sb.append("&x.pe=" + hostAddress + ":" + endp.port());
            }
        }
    }

    private static void addEndpoint(Address address, int port, StringBuilder sb) {
        String hostAddress = address.toString();
        if (hostAddress.contains("invalid")) {
            return;
        }

        if (address.isV6()) {
            hostAddress = "[" + hostAddress + "]";
        }
        sb.append("&x.pe=" + hostAddress + ":" + port);
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
