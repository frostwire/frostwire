/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.platform;

/**
 * @author gubatron
 * @author aldenml
 */
public interface VPNMonitor {
    /**
     * This indicates if the OS networking is under active VPN or not.
     * It's based on routing heuristics, and not 100% reliable, but it's usable.
     * The value could be cached for speed and optimization purposes. The actual
     * implementation should not block for a long period of time and should be
     * safe to call it from the UI.
     *
     * @return if under VPN or not
     */
    boolean active();

    /**
     * Perform a manual status refresh, don't do this from the UI since a blocking
     * code could be used in any given implementation.
     */
    void refresh();
}
