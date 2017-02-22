/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import com.frostwire.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.limewire.util.OSUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNs {
    private static Pattern piaKillSwitchRoutePattern = null;
    private static String netstatCmd = null;

    public static boolean isVPNActive() {
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
            String netstatCmd = getNetstatPath();
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
                if (line.contains("128.0.0.0") || piaVPNwithKillSwitchOn(line)) {
                    result = true;
                    break;
                }
            }
        } catch (Throwable t2) {
            result = false;
            t2.printStackTrace();
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

    private static boolean piaVPNwithKillSwitchOn(String line) {
        compilePIAKillSwitchPattern(); // lazy-compiles the pattern only once per session
        return piaKillSwitchRoutePattern.matcher(line).matches();
    }

    private static void compilePIAKillSwitchPattern() {
        if (piaKillSwitchRoutePattern == null) {
            // PIA with kill switch after a restart adds 0.0.0.0 0.0.0.0 10.x.x.x.x 10.x.x.x.x NN as the first route
            // when VPN is active
            piaKillSwitchRoutePattern = Pattern.compile(".*?(0\\.0\\.0\\.0).*?(0\\.0\\.0\\.0).*?(10\\.\\d*\\.\\d*\\.\\d*).*(10\\.\\d*\\.\\d*\\.\\d*).*?(\\d\\d)");
        }
    }

    private static String getNetstatPath() {
        if (netstatCmd != null) {
            return netstatCmd;
        }
        String candidate = "netstat";
        if (OSUtils.isMacOSX() && new File("/usr/sbin/netstat").exists()) {
            candidate = "/usr/sbin/netstat";
        }
        netstatCmd = candidate;
        return netstatCmd;
    }

}
