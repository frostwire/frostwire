/*
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

package org.limewire.util;

import java.net.*;
import java.util.Enumeration;

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
    private NetworkUtils() {}

    /**
     * Returns whether or not the specified port is within the valid range of
     * ports.
     * 
     * @param port
     *            the port number to check
     */
    public static boolean isValidPort(int port) {
        return (port > 0 && port <= 0xFFFF);
    }

    /**
     * @return A non-loopback IPv4 address of a network interface on the local
     *         host.
     * @throws UnknownHostException
     */
    public static InetAddress getLocalAddress() throws UnknownHostException {
        InetAddress addr = InetAddress.getLocalHost();
        
        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
            return addr;
        }
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        addr = addresses.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            return addr;
                        }
                    }
                }
            }
        } catch (SocketException se) {
        }

        throw new UnknownHostException(
                "localhost has no interface with a non-loopback IPv4 address");
    }
    
    /**
     * Returns the IP:Port as byte array.
     * 
     * This method is IPv6 compliant
     */
    public static byte[] getBytes(SocketAddress addr) throws UnknownHostException {
        InetSocketAddress iaddr = (InetSocketAddress)addr;
        if (iaddr.isUnresolved()) {
            throw new UnknownHostException(iaddr.toString());
        }
        
        return getBytes(iaddr.getAddress(), iaddr.getPort());
    }
    
    /**
     * Returns the IP:Port as byte array.
     * 
     * This method is IPv6 compliant
     */
    public static byte[] getBytes(InetAddress addr, int port) {
        if (!isValidPort(port)) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        
        byte[] address = addr.getAddress();

        byte[] dst = new byte[address.length + 2];
        System.arraycopy(address, 0, dst, 0, address.length);
        dst[dst.length-2] = (byte)((port >> 8) & 0xFF);
        dst[dst.length-1] = (byte)((port     ) & 0xFF);
        return dst;
    }
}
