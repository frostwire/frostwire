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

import com.limegroup.gnutella.gui.FileChooserHandler;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.AbstractAction;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * This class shows the <tt>JFileChooser</tt> when the user presses
 * the button to add a new directory to the shared directories.  It
 * adds the directory only if does not already exist in the list.
 */
public class AddLibraryDirectoryAction extends AbstractAction {
    private static final long serialVersionUID = 7930650059331836863L;
    private final RecursiveLibraryDirectoryPanel recursiveSharingPanel;
    private final Component parent;

    /**
     * @param parent the owner of the dialog, the dialog is centered on it.
     */
    public AddLibraryDirectoryAction(RecursiveLibraryDirectoryPanel recursiveSharingPanel, Component parent) {
        super(I18n.tr("Add") + "...");
        this.recursiveSharingPanel = recursiveSharingPanel;
        this.parent = parent;
    }

    public void actionPerformed(ActionEvent ae) {
        File dir = FileChooserHandler.getInputDirectory(parent);
        if (dir != null) {
            recursiveSharingPanel.addRoot(dir);
        }
    }
}