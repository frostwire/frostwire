/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.library;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public class StarredDirectoryHolder implements DirectoryHolder {
    private final Icon icon;

    public StarredDirectoryHolder() {
        icon = GUIMediator.getThemeImage("star_on");
    }

    public boolean accept(File pathname) {
        return true;
    }

    public String getName() {
        return I18n.tr("Starred");
    }

    public String getDescription() {
        return I18n.tr("Starred");
    }

    public File getDirectory() {
        return null;
    }

    public File[] getFiles() {
        return null;
    }

    public int size() {
        return 0;
    }

    public Icon getIcon() {
        return icon;
    }
}
