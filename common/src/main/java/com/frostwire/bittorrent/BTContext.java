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

package com.frostwire.bittorrent;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTContext {
    public final int[] version = {0, 0, 0, 0};
    public File homeDir;
    public File torrentsDir;
    public File dataDir;
    public String interfaces;
    public int retries;
    public boolean optimizeMemory;
    /**
     * Indicates if the engine starts with the DHT enable.
     */
    public boolean enableDht = true;
}
