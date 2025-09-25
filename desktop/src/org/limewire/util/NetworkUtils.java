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

package org.limewire.util;

import com.frostwire.jlibtorrent.Address;
import com.limegroup.gnutella.settings.ConnectionSettings;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Random;

import static com.limegroup.gnutella.settings.ConnectionSettings.PORT_RANGE_0;
import static com.limegroup.gnutella.settings.ConnectionSettings.PORT_RANGE_1;

/**
 * Provides methods for network programming.
 * <code>NetworkUtils</code>' methods check the validity of IP addresses, ports
 * and socket addresses. <code>NetworkUtils</code> includes both
 * IPv4 and
 * <a href="http://en.wikipedia.org/wiki/IPv6">IPv6</a> compliant methods.
 */
public final class NetworkUtils {
    /**
     * Ensure that this class cannot be constructed.
     */
    private NetworkUtils() {
    }

    /**
     * Returns whether or not the specified port is within the valid range of
     * ports. Given ranges are inclusive on both ends.
     * @param port the port number to check
     */
    public static boolean isValidPort(int port, int minValidPort, int maxValidPort) {
        return (port >= minValidPort && port <= maxValidPort);
    }

    /**
     * Returns the IP:Port as byte array.
     * <p>
     * This method is IPv6 compliant
     */
    public static byte[] getBytes(SocketAddress addr) throws UnknownHostException {
        InetSocketAddress iaddr = (InetSocketAddress) addr;
        if (iaddr.isUnresolved()) {
            throw new UnknownHostException(iaddr.toString());
        }
        return getBytes(iaddr.getAddress(), iaddr.getPort());
    }

    /**
     * Returns the IP:Port as byte array.
     * <p>
     * This method is IPv6 compliant
     */
    private static byte[] getBytes(InetAddress addr, int port) {
        if (!isValidPort(port, PORT_RANGE_0.getDefaultValue(), PORT_RANGE_1.getDefaultValue())) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        byte[] address = addr.getAddress();
        byte[] dst = new byte[address.length + 2];
        System.arraycopy(address, 0, dst, 0, address.length);
        dst[dst.length - 2] = (byte) ((port >> 8) & 0xFF);
        dst[dst.length - 1] = (byte) ((port) & 0xFF);
        return dst;
    }

    /**
     * Returns a port within the default port range as specified by PORT_RANGE_0, PORT_RANGE_1 [37000,57000]
     * if MANUAL_PORT_RANGE setting is false, otherwise, use whatever the user has set for the range.
     * @return a random port between the range to be used.
     */
    public static int getPortInRange(boolean useManualRange,
                                     int defaultPort0,
                                     int defaultPort1,
                                     int manualPort0,
                                     int manualPort1) {
        // port range [37000, 57000]
        int port;
        int port0 = defaultPort0;
        int port1 = defaultPort1;
        // If not using the manual port range set by the user in RouterConfigurationPane
        // then use default range port values.
        if (useManualRange) {
            port0 = manualPort0;
            port1 = manualPort1;
        }
        port = port0 + new Random().nextInt((port1 - port0) + 1);
        return port;
    }

    /**
     * If the user has decided to use a custom network interface it will use it, along with the given port.
     * @return "0.0.0.0:[port],[::]:[port]" if !useCustomNetworkInterface, otherwise  [customInetAddress:[port]]
     */
    public static String getLibtorrentFormattedNetworkInterface(boolean useCustomNetworkInterface,
                                                                String defaultInetAddress,
                                                                String customInetAddress,
                                                                final int port) {
        String iface = defaultInetAddress;
        if (useCustomNetworkInterface) {
            iface = customInetAddress;
        }
        // IPv4
        if (iface.equals("0.0.0.0")) {
            iface = "0.0.0.0:" + port + ",[::]:" + port;
        } else {
            // quick IPv6 test
            if (iface.contains(":")) {
                iface = "[" + iface + "]";
            }
            iface = iface + ":" + port;
        }
        return iface;
    }

    public static boolean isLinkLocal(Address address) {
        String address_str = address.toString();
        return "fe80::1%lo0".equals(address_str);
    }
}
