/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.tpb;

/**
 * TPB mirror list utility class
 * Extracted from legacy TPBSearchPerformer for V2 architecture
 *
 * @author gubatron
 */
public class TPBMirrors {
    public static String[] getMirrors() {
        return new String[]{
                "pirate-bay.info",
                "piratebay.live",
                "pirateproxylive.org",
                "thehiddenbay.com",
                "thepiratebay-unblocked.org",
                "thepiratebay.party",
                "thepiratebay.zone",
                "thepiratebay0.org",
                "thepiratebay10.org",
                "thepiratebay7.com",
                "tpb.party",
        };
    }
}
