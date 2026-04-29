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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class CidrFilterInputStreamReader implements IPFilterInputStreamReader {
    private final BufferedReader br;
    private final InputStream is;
    private int bytesRead;
    private IPRange nextRange;

    public CidrFilterInputStreamReader(File uncompressedFile) {
        try {
            this.is = new FileInputStream(uncompressedFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        bytesRead = 0;
        nextRange = null;
    }

    @Override
    public IPRange readLine() {
        if (nextRange != null) {
            IPRange range = nextRange;
            nextRange = null;
            return range;
        }
        return parseNextRange();
    }

    private IPRange parseNextRange() {
        try {
            String line;
            while ((line = br.readLine()) != null) {
                int lineLen = line.length();
                bytesRead += lineLen + 1; // +1 for newline character
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }
                if (line.contains(":")) {
                    continue;
                }
                int slashIdx = line.indexOf('/');
                if (slashIdx < 0) {
                    continue;
                }
                String ipAddress = line.substring(0, slashIdx).trim();
                String prefixStr = line.substring(slashIdx + 1).trim();
                int prefixLength;
                try {
                    prefixLength = Integer.parseInt(prefixStr);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (prefixLength < 0 || prefixLength > 32) {
                    continue;
                }
                try {
                    InetAddress inet = InetAddress.getByName(ipAddress);
                    int ip = ByteBuffer.wrap(inet.getAddress()).getInt();
                    int network;
                    int broadcast;
                    if (prefixLength == 0) {
                        network = 0;
                        broadcast = -1; // 0xFFFFFFFF = 255.255.255.255
                    } else if (prefixLength == 32) {
                        network = ip;
                        broadcast = ip;
                    } else {
                        network = ip & (0xFFFFFFFF << (32 - prefixLength));
                        broadcast = network | (0xFFFFFFFF >>> prefixLength);
                    }
                    String startIP = InetAddress.getByAddress(
                            ByteBuffer.allocate(4).putInt(network).array()
                    ).getHostAddress();
                    String endIP = InetAddress.getByAddress(
                            ByteBuffer.allocate(4).putInt(broadcast).array()
                    ).getHostAddress();
                    return new IPRange(ipAddress + "/" + prefixLength, startIP, endIP);
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int bytesRead() {
        return bytesRead;
    }

    @Override
    public int available() {
        try {
            return is.available();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public void close() {
        try {
            br.close();
            is.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
