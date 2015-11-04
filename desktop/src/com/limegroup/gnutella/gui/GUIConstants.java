/*
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

import java.io.File;

/**
 * Constants used by gui classes.
 */
public final class GUIConstants {

    public static final String FROSTWIRE_64x64_ICON = "frostwire64x64";

    /**
     * Constant for the path to the LimeWire Windows launcher.
     */
    public static final File FROSTWIRE_EXE_FILE = new File("FrostWire.exe").getAbsoluteFile();

    /**
     * The number of pixels in the margin of a padded panel.
     */
    public static final int OUTER_MARGIN = 6;

    /**
     * Standard number of pixels that should separate many 
     * different types of gui components.
     */
    public static final int SEPARATOR = 6;
}
