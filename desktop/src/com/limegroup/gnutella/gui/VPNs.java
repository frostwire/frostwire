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
            String[] output = readProcessOutput("netstat", "-anr").split("\r\n");
            Interface[] interfaces = parseInterfaces(output);
            Route[] routes = parseActiveRoutes(output);
            return isWindowsPIAActive(interfaces, routes) || isExpressVPNActive(interfaces, routes);
        } catch (Throwable t2) {
            t2.printStackTrace();
            return false;
        }
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

    private static boolean isExpressVPNActive(final Interface[] interfaces, final Route[] activeRoutes) {
        boolean expressVPNTapAdapterPresent = false;
        for (Interface iface : interfaces) {
            if (iface.name.contains("ExpressVPN Tap Adapter")) {
                expressVPNTapAdapterPresent = true;
                break;
            }
        }
        return expressVPNTapAdapterPresent && activeRoutes != null && activeRoutes.length == 2;
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
