/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui;

import org.apache.commons.io.IOUtils;
import org.limewire.util.OSUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author gubatron
 * @author aldenml
 */
final class VPNs {
    static boolean isVPNActive() {
        boolean result = false;

        if (OSUtils.isMacOSX() || OSUtils.isLinux()) {
            result = isPosixVPNActive();
        } else if (OSUtils.isWindows()) {
            result = isWindowsVPNActive();
        }

        return result;
    }

    /**
     * <strong>VPN ON (Mac)</strong>
     * <pre>Internet:
     * Destination        Gateway            Flags        Refs      Use   Netif Expire
     * 0/1                10.81.10.5         UGSc            5        0   utun1
     * ...</pre>
     *
     * <strong>VPN ON (Linux)</strong>
     * <pre>Kernel IP routing table
     * Destination     Gateway         Genmask         Flags   MSS Window  irtt Iface
     * 0.0.0.0         10.31.10.5      128.0.0.0       UG        0 0          0 tun0
     * ...</pre>
     *
     * @return true if it finds a line that starts with "0" and contains "tun" in the output of "netstat -nr"
     */
    private static boolean isPosixVPNActive() {
        boolean result = false;
        try {
            String netstatCmd = "netstat";
            String[] output = readProcessOutput(netstatCmd,"-nr").split("\r\n");
            for (String line : output) {
                if (line.startsWith("0") && line.contains("tun")) {
                    result = true;
                    break;
                }
            }
        } catch (Throwable t) {
            result = false;
        }

        return result;
    }

    private static boolean isWindowsVPNActive() {
        boolean result = false;
        try {
            String[] output = readProcessOutput("netstat", "-nr").split("\r\n");
            for (String line : output) {
                // regular VPN case
                if (line.contains("128.0.0.0") ||
                // PIA with kill switch (Hack)
                        (line.contains("0.0.0.0          0.0.0.0       10.") && line.endsWith("21"))) {
                    result = true;
                    break;
                }
                // PIA with kill switch after a restart adds 0.0.0.0 0.0.0.0 10.x.x.x.x 10.x.x.x.x 21 as the first route
                // when VPN is active
            }
        } catch (Throwable t2) {
            result = false;
        }
        return result;
    }


    private static String readProcessOutput(String command, String arguments) {
        String result = "";
        ProcessBuilder pb = new ProcessBuilder(command, arguments);
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            InputStream stdout = process.getInputStream();
            final BufferedReader brstdout = new BufferedReader(new InputStreamReader(stdout));
            String line;

            try {
                StringBuilder stringBuilder = new StringBuilder();
                while ((line = brstdout.readLine()) != null) {
                    stringBuilder.append(line + "\r\n");
                }

                result = stringBuilder.toString();
            } catch (Exception e) {
            } finally {
                IOUtils.closeQuietly(brstdout);
                IOUtils.closeQuietly(stdout);
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }
}
