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

package com.limegroup.gnutella.gui.options.panes.ipfilter;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class IPRange {
    private String description;
    private String startAddress;
    private String endAddress;

    public IPRange(String description, String startAddress, String endAddress) {
        if (description == null) {
            throw new IllegalArgumentException("IPRange description can't be null (use empty string)");
        }
        if (startAddress == null || startAddress.isEmpty()) {
            throw new IllegalArgumentException("IPRange startAddress can't be null or empty");
        }
        if (endAddress == null || endAddress.isEmpty()) {
            endAddress = startAddress;
        }
        this.description = description;
        this.startAddress = startAddress;
        this.endAddress = endAddress;
    }

    private static int readByteOrEof(InputStream is) throws EOFException, IOException {
        int b = is.read();
        if (b < 0) {
            throw new EOFException();
        }
        return b;
    }

    private static void readFully(InputStream is, byte[] buf) throws EOFException, IOException {
        int total = 0;
        while (total < buf.length) {
            int read = is.read(buf, total, buf.length - total);
            if (read < 0) {
                throw new EOFException();
            }
            total += read;
        }
    }

    public static IPRange readObjectFrom(InputStream is) throws IOException {
        int descriptionLength = readByteOrEof(is);
        byte[] descBuffer = new byte[descriptionLength];
        readFully(is, descBuffer);
        String description = new String(descBuffer, StandardCharsets.UTF_8);
        String startAddress = null;
        int ipVersionType = readByteOrEof(is);
        if (ipVersionType == 4) {
            byte[] address = new byte[4];
            readFully(is, address);
            startAddress = InetAddress.getByAddress(address).getHostAddress();
        } else if (ipVersionType == 6) {
            byte[] address = new byte[16];
            readFully(is, address);
            startAddress = InetAddress.getByAddress(address).getHostAddress();
        } else {
            throw new EOFException("Corrupted ip_filter.db: invalid start ipVersionType=" + ipVersionType);
        }
        String endAddress = null;
        ipVersionType = readByteOrEof(is);
        if (ipVersionType == 4) {
            byte[] address = new byte[4];
            readFully(is, address);
            endAddress = InetAddress.getByAddress(address).getHostAddress();
        } else if (ipVersionType == 6) {
            byte[] address = new byte[16];
            readFully(is, address);
            endAddress = InetAddress.getByAddress(address).getHostAddress();
        } else {
            throw new EOFException("Corrupted ip_filter.db: invalid end ipVersionType=" + ipVersionType);
        }
        if (startAddress == null || endAddress == null) {
            throw new EOFException("Corrupted ip_filter.db: null address after read");
        }
        return new IPRange(description, startAddress, endAddress);
    }

    public String description() {
        return description;
    }

    public String startAddress() {
        return startAddress;
    }

    public String endAddress() {
        return endAddress;
    }

    public void writeObjectTo(OutputStream os) throws IOException {
        String safeDesc = description.length() > 255 ? description.substring(0, 255) : description;
        os.write(safeDesc.length());        // DESCRIPTION LENGTH (max 255)
        os.write(safeDesc.getBytes(StandardCharsets.UTF_8)); // DESCRIPTION
        InetAddress bufferRange = InetAddress.getByName(startAddress);
        boolean isIPv4 = bufferRange instanceof Inet4Address;
        os.write(isIPv4 ? 4 : 6);           // START RANGE IP VERSION TYPE <4 | 6>
        os.write(bufferRange.getAddress()); // START RANGE IP <4 bytes (32bits)>|[ 16 bytes (128bits)]
        bufferRange = InetAddress.getByName(endAddress);
        isIPv4 = bufferRange instanceof Inet4Address;
        os.write(isIPv4 ? 4 : 6);           // END RANGE IP VERSION TYPE <4 | 6>
        os.write(bufferRange.getAddress()); // END RANGE IP <4 bytes (32bits)>|[ 16 bytes (128bits)]
        os.flush();
    }

    @Override
    public String toString() {
        return "IPRange@" + hashCode() + " { description = \"" + description + "\", startAddress = \"" + startAddress + "\", endAddress = \"" + endAddress + "\" }";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IPRange)) {
            return false;
        }
        IPRange other = (IPRange) obj;
        // NPE should be impossible as these values can't be null or empty strings
        return other.startAddress.equals(startAddress) &&
                other.endAddress.equals(endAddress);
    }

    @Override
    public int hashCode() {
        return startAddress.hashCode() * endAddress.hashCode() * 9419;
    }
}
