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

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.LimeWireCore;
import com.frostwire.util.OSUtils;
import org.limewire.util.SystemUtils;

/**
 * Control the firewall that ships with the computer's operating system.
 */
class FirewallUtils {
    /**
     * Add ourselves to the firewall exceptions list.
     * This will let code in this process listen on a socket without the firewall showing the user a security warning.
     * Call this method on startup before the program listens on a socket.
     * Unlike UPnP, this returns quickly and works very reliably.
     * <p>
     * Returns true if this is added (or already on) the firewall exception list.
     */
    static boolean addToFirewall() {
        if (!OSUtils.isWindows())
            return false;
        // Get the path of this running instance, like "C:\Program Files\LimeWire\LimeWire.exe"
        String path = SystemUtils.getRunningPath();
        if (path == null)
            return false;
        // Only add us if the LimeWire Windows launcher ran, not Java in a development environment
        if (!path.equalsIgnoreCase(GUIConstants.FROSTWIRE_EXE_FILE.getAbsolutePath()))
            return false;
        // Only add a listing for us if the Windows Firewall Exceptions list doesn't have one yet
        if (SystemUtils.isProgramListedOnFirewall(path))
            return true;

        /* The name of this program, "FrostWire". */
        if (SystemUtils.addProgramToFirewall(path, "FrostWire")) {
            scheduleRemovalOnShutdown();
            return true;
        }
        return false;
    }

    /**
     * Remove the listing we made on the firewall exceptions list on startup.
     * This cleans up after ourselves so the program is only listed while it's running.
     * It also best protects the computer, only opening a hole in the firewall when the program is running and needs it.
     * Call this method before the program exits.
     * Unlike UPnP, this returns quickly and works very reliably.
     */
    private static void removeFromFirewall() {
        // Only do something if we're running on Windows
        if (!OSUtils.isWindows())
            return;
        // Get the path of this running instance, like "C:\Program Files\LimeWire\LimeWire.exe"
        String path = SystemUtils.getRunningPath();
        // Only do something if the LimeWire Windows launcher ran, not Java in a development environment
        if (path == null || !path.equalsIgnoreCase(GUIConstants.FROSTWIRE_EXE_FILE.getPath()))
            return;
        // Only remove our listing if it's there
        if (SystemUtils.isProgramListedOnFirewall(path)) {
            SystemUtils.removeProgramFromFirewall(path);
        }
    }

    /**
     * Schedules a shutdown hook which will clear the listing we created this session.
     */
    private static void scheduleRemovalOnShutdown() {
        Thread waiter = new Thread("Platform Firewall Waiter") {
            public void run() {
                removeFromFirewall();
            }
        };
        LimeWireCore.instance().getLifecycleManager().addShutdownItem(waiter);
    }
}
