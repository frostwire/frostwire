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
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNs {
    public static boolean isVPNActive() {
        boolean result = false;

        if (OSUtils.isMacOSX() || OSUtils.isLinux()) {
            result = isPosixVPNActive();
        } else if (OSUtils.isWindows()) {
            result = isWindowsVPNActive();
        }

        return result;
    }

    private static boolean isPosixVPNActive() {
        boolean result = false;
        try {
            result = isAnyNetworkInterfaceATunnel();
        } catch (Throwable t) {
            result = false;
            /**
             try {
             result = readProcessOutput("netstat","-nr").indexOf(" tun") != -1;
             } catch (Throwable t2) {
             result = false;
             }
             */
        }

        return result;
    }

    private static boolean isAnyNetworkInterfaceATunnel() {
        boolean result = false;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface iface = networkInterfaces.nextElement();
                if (iface.getDisplayName().contains("tun")) {
                    result = true;
                    break;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return result;
    }

    private static boolean isWindowsVPNActive() {
        boolean result = false;
        try {
            result = readProcessOutput("netstat", "-nr").indexOf("128.0.0.0") != -1;
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
            String line = null;

            try {
                StringBuilder stringBuilder = new StringBuilder();
                while ((line = brstdout.readLine()) != null) {
                    stringBuilder.append(line);
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
