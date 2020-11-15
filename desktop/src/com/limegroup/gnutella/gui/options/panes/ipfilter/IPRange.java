/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options.panes.ipfilter;

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

    public static IPRange readObjectFrom(InputStream is) throws IOException {
        int descriptionLength = is.read(); // DESCRIPTION LENGTH
        byte[] descBuffer = new byte[descriptionLength];
        is.read(descBuffer); // DESCRIPTION
        String description = new String(descBuffer, StandardCharsets.UTF_8);
        String startAddress = null;
        int ipVersionType = is.read(); // START RANGE IP VERSION TYPE <4 | 6>
        if (ipVersionType == 4) {
            byte[] address = new byte[4];
            is.read(address); // START RANGE IP <4 bytes (32bits)>
            startAddress = InetAddress.getByAddress(address).getHostAddress();
        } else if (ipVersionType == 6) {
            byte[] address = new byte[16];
            is.read(address); // START RANGE IP <16 bytes (128bits)>
            startAddress = InetAddress.getByAddress(address).getHostAddress();
        }
        String endAddress = null;
        ipVersionType = is.read(); // END RANGE IP VERSION TYPE <4 | 6>
        if (ipVersionType == 4) {
            byte[] address = new byte[4];
            is.read(address); // END RANGE IP <4 bytes (32bits)>
            endAddress = InetAddress.getByAddress(address).getHostAddress();
        } else if (ipVersionType == 6) {
            byte[] address = new byte[16];
            is.read(address); // END RANGE IP <16 bytes (128bits)>
            endAddress = InetAddress.getByAddress(address).getHostAddress();
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
        os.write(description.length());     // DESCRIPTION LENGTH
        os.write(description.getBytes(StandardCharsets.UTF_8)); // DESCRIPTION
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
