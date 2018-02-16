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

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.limewire.util.OSUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNs {
    private static Pattern piaKillSwitchRoutePattern = null;
    private static String netstatCmd = null;

    // 17...00 ff 37 58 eb 11 ......TAP-Windows Adapter V9
    private static final String WINDOWS_NETWORK_INTERFACE_REGEX = "([\\d]+)\\.\\.\\.([0-9a-f ]+)+([\\. ]{1})+(.*)";
    private static Pattern WINDOWS_NETWORK_INTERFACE_PATTERN = null;

    // 10    266 ::/0                     fe80::9e34:26ff:feef:a506
    private static final String WINDOWS_ACTIVE_ROUTE_REGEX = "([\\d]{1,2}).*([\\d]{2,3})\\s{1}([0-9a-f\\:/]+)\\s{1}(.*)";
    private static Pattern WINDOWS_ACTIVE_ROUTE_PATTERN = null;


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
        try {
            String[] output = readProcessOutput("netstat", "-nr").split("\r\n");
            Interface[] interfaces = parseInterfaces(output);
            Route[] routes = parseActiveRoutes(output);

            if (isWindowsPIAActive(interfaces, routes)) {
                return true;
            }
            
            // TODO: check ExpressVPN
        } catch (Throwable t2) {
            t2.printStackTrace();
            return false;
        }
        return false;
    }

    private static boolean isWindowsPIAActive(final Interface[] interfaces, final Route[] activeRoutes) {
        // Try looking for an active PIA Interface "TAP-Windows Adapter*"
        Interface tapWindowsAdapter = null;
        for (Interface iface : interfaces) {
            if (iface.name.contains("TAP-Windows Adapter")) {
                tapWindowsAdapter = iface;
                break;
            }
        }

        if (tapWindowsAdapter == null) {
            return false;
        }

        // Look for the tapWindowsAdapter in the list of active routes
        for (Route route : activeRoutes) {
            if (route.id == tapWindowsAdapter.id) {
                return true;
            }
        }

        return false;
    }

    private static Interface parseInterface(String line) {
        if (WINDOWS_NETWORK_INTERFACE_PATTERN == null) {
            WINDOWS_NETWORK_INTERFACE_PATTERN = Pattern.compile(WINDOWS_NETWORK_INTERFACE_REGEX);
        }
        Matcher matcher = WINDOWS_NETWORK_INTERFACE_PATTERN.matcher(line);
        if (matcher.find()) {
            return new Interface(Integer.parseInt(matcher.group(1)), matcher.group(4));
        }

        return null;
    }

    private static Interface[] parseInterfaces(String[] output) {
        final String startDelimiter = "Interface List";
        final String endDelimiter = "===";
        boolean startDelimeterRead = false;
        boolean endDelimiterRead = false;
        final ArrayList<Interface> interfaceList = new ArrayList<>();
        for (String line : output) {
            if (!startDelimeterRead) {
                startDelimeterRead = line.startsWith(startDelimiter);
                continue;
            }
            if (!endDelimiterRead) {
                endDelimiterRead = line.startsWith(endDelimiter);
                if (endDelimiterRead) {
                    break;
                }
                Interface iface = parseInterface(line);
                if (iface != null) {
                    interfaceList.add(iface);
                }
            }
        }
        return interfaceList.toArray(new Interface[0]);
    }

    private static Route parseActiveRoute(String line) {
        if (WINDOWS_ACTIVE_ROUTE_PATTERN == null) {
            WINDOWS_ACTIVE_ROUTE_PATTERN = Pattern.compile(WINDOWS_ACTIVE_ROUTE_REGEX);
        }
        Matcher matcher = WINDOWS_ACTIVE_ROUTE_PATTERN.matcher(line);
        if (matcher.find()) {
            return new Route(Integer.parseInt(matcher.group(1)), matcher.group(3), matcher.group(4));
        }
        return null;
    }

    private static Route[] parseActiveRoutes(String[] output) {
        final String startDelimiter = "If Metric Network Destination      Gateway";
        final String endDelimiter = "===";
        boolean startDelimeterRead = false;
        boolean endDelimiterRead = false;
        final ArrayList<Route> routeList = new ArrayList<>();
        for (String line : output) {
            if (!startDelimeterRead) {
                startDelimeterRead = line.contains(startDelimiter);
                continue;
            }
            if (!endDelimiterRead) {
                endDelimiterRead = line.startsWith(endDelimiter);
                if (endDelimiterRead) {
                    break;
                }
                Route iface = parseActiveRoute(line);
                if (iface != null) {
                    routeList.add(iface);
                }
            }
        }
        return routeList.toArray(new Route[0]);
    }

    public static void main(String[] args) {
        String[] netstatTxt = readProcessOutput("cat", "/Users/gubatron/Desktop/netstat.txt").split("\r\n");
        Interface[] interfaces = parseInterfaces(netstatTxt);
        Route[] routes = parseActiveRoutes(netstatTxt);

        System.out.println(routes.length);
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

    private final static class Interface {
        final int id;
        final String name;
        Interface (int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
    
    private final static class Route {
        final int id;
        final String destination;
        final String gateway;
        Route(int id, String destination, String gateway) {
            this.id = id;
            this.destination = destination;
            this.gateway = gateway;
        }
    }

}
