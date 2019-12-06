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

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.desktop.DesktopPlatform;
import com.frostwire.jlibtorrent.EnumNet;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.VPNMonitor;
import org.limewire.util.OSUtils;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNs {
    public static boolean isVPNActive() {
        boolean result = false;
        if (BTEngine.getInstance().swig() == null) {
            // still not started or already stopped
            return false;
        }
        if (OSUtils.isMacOSX() || OSUtils.isLinux()) {
            DesktopPlatform platform = (DesktopPlatform) Platforms.get();

            result = isPosixVPNActive();
            if (!result) {
                // not returning all the interfaces on macos, could be a false positive
                platform.vpn().refresh();
                result = result || platform.vpn().active();
            }
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
     * <p>
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
            List<EnumNet.IpRoute> routes = EnumNet.enumRoutes(BTEngine.getInstance());
            for (EnumNet.IpRoute route : routes) {
                if (route.destination().toString().equals("0.0.0.0") && route.name().contains("tun")) {
                    result = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static boolean isWindowsVPNActive() {
        try {
            List<EnumNet.IpInterface> interfaces = EnumNet.enumInterfaces(BTEngine.getInstance());
            List<EnumNet.IpRoute> routes = EnumNet.enumRoutes(BTEngine.getInstance());
            return isWindowsVPNAdapterActive(interfaces, routes, "TAP-Windows Adapter") || // PIA
                    isWindowsVPNAdapterActive(interfaces, routes, "Private Internet Access Network Adapter") ||
                    isWindowsVPNAdapterActive(interfaces, null, "ExpressVPN Tap Adapter") ||
                    isWindowsVPNAdapterActive(interfaces, routes, "CactusVPN") ||
                    isWindowsVPNAdapterActive(interfaces, routes, "TAP-NordVPN") ||
                    isWindowsVPNAdapterActive(interfaces, routes, "AVG TAP") ||
                    isWindowsVPNAdapterActive(interfaces, routes, "SecureLine TAP") || // avast!
                    isWindowsVPNAdapterActive(interfaces, null, "TAP-Windows Adapter V9") || // IPVanish
                    isWindowsVPNAdapterActive(interfaces, routes, "CyberGhost") || // CyberGhost
                    isWindowsVPNAdapterActive(interfaces, routes, "Windscribe VPN") || isWindowsVPNAdapterActive(interfaces, routes, "Windscribe IKEv2") ||
                    isWindowsVPNAdapterActive(interfaces, routes, "PureVPN");
        } catch (Throwable t2) {
            t2.printStackTrace();
            return false;
        }
    }

    private static boolean isWindowsVPNAdapterActive(List<EnumNet.IpInterface> interfaces,
                                                     List<EnumNet.IpRoute> routes, String description) {
        EnumNet.IpInterface adapter = null;
        for (EnumNet.IpInterface iface : interfaces) {
            if (iface.description().contains(description) && iface.preferred()) {
                adapter = iface;
                break;
            }
        }
        if (adapter == null) {
            return false;
        }
        if (routes == null) { // don't lookup at routes
            return true;
        }
        for (EnumNet.IpRoute route : routes) {
            if (route.name().contains(adapter.name())) {
                return true;
            }
        }
        return false;
    }
}
