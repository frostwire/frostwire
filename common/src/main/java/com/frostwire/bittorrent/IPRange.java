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

package com.frostwire.bittorrent;

/**
 * Interface for IP ranges used in IP filtering.
 * This provides a common interface for both desktop and Android implementations.
 * 
 * @author gubatron
 * @author aldenml
 */
public interface IPRange {
    /**
     * Get the description/label for this IP range
     * @return Description string
     */
    String description();
    
    /**
     * Get the start IP address of this range
     * @return Start IP address as string
     */
    String startAddress();
    
    /**
     * Get the end IP address of this range
     * @return End IP address as string
     */
    String endAddress();
}