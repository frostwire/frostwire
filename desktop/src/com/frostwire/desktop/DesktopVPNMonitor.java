/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.desktop;

import com.frostwire.platform.VPNMonitor;
import com.frostwire.regex.Pattern;
import com.frostwire.util.Logger;
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
final class DesktopVPNMonitor implements VPNMonitor {
    private static final Logger LOG = Logger.getLogger(DesktopVPNMonitor.class);
    // PIA with kill switch after a restart adds 0.0.0.0 0.0.0.0 10.x.x.x.x 10.x.x.x.x NN as the first route
    // when VPN is active
    private static final Pattern PIA_KILL_SWITCH_ROUTE_PATTERN =
            Pattern.compile(".*?(0\\.0\\.0\\.0).*?(0\\.0\\.0\\.0).*?(10\\.\\d*\\.\\d*\\.\\d*).*(10\\.\\d*\\.\\d*\\.\\d*).*?(\\d\\d)");
    private boolean active;

    DesktopVPNMonitor() {
        this.active = false;
    }

    private static boolean isWindowsVPNActive() {
        try {
            String[] output = readProcessOutput("netstat", "-nr").split("\r\n");
            for (String line : output) {
                if (line.contains("128.0.0.0") || piaVPNWithKillSwitchOn(line)) {
                    return true;
                }
            }
        } catch (Throwable e) {
            LOG.error("Error detecting VPN", e);
        }
        return false;
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
                StringBuilder sb = new StringBuilder();
                while ((line = brstdout.readLine()) != null) {
                    sb.append(line).append("\r\n");
                }
                result = sb.toString();
            } catch (Throwable e) {
                LOG.error("Error reading routing table command output", e);
            } finally {
                IOUtils.closeQuietly(brstdout);
                IOUtils.closeQuietly(stdout);
            }
        } catch (Throwable e) {
            LOG.error("Error executing routing table command", e);
        }
        return result;
    }

    private static boolean piaVPNWithKillSwitchOn(String line) {
        return PIA_KILL_SWITCH_ROUTE_PATTERN.matcher(line).matches();
    }

    private static String netstatCmd() {
        String cmd = "netstat";
        if (OSUtils.isMacOSX() && new File("/usr/sbin/netstat").exists()) {
            cmd = "/usr/sbin/netstat";
        }
        return cmd;
    }

    @Override
    public boolean active() {
        return active;
    }

    @Override
    public void refresh() {
        if (OSUtils.isMacOSX() || OSUtils.isLinux()) {
            active = isPosixVPNActive();
        } else if (OSUtils.isWindows()) {
            active = isWindowsVPNActive();
        }
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
    private boolean isPosixVPNActive() {
        try {
            String[] output = readProcessOutput(netstatCmd(), "-nr").split("\r\n");
            for (String line : output) {
                boolean _0_0_0_0_tunnel_check = line.startsWith("0") && line.contains("tun");
                boolean default_ipsec0_check = line.startsWith("default") && line.contains("ipsec");
                if (_0_0_0_0_tunnel_check || default_ipsec0_check) {
                    return true;
                }
            }
        } catch (Throwable e) {
            LOG.error("Error detecting VPN", e);
        }
        return false;
    }
}
