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

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.AbstractAction;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Removes selected root folders from a {@link RecursiveLibraryDirectoryPanel}.
 */
public class RemoveLibraryDirectoryAction extends AbstractAction {
    private static final long serialVersionUID = -6729288511779797455L;
    private final RecursiveLibraryDirectoryPanel recursiveSharingPanel;

    public RemoveLibraryDirectoryAction(RecursiveLibraryDirectoryPanel recursiveSharingPanel) {
        super(I18n.tr("Remove"));
        this.recursiveSharingPanel = recursiveSharingPanel;
        setEnabled(false);
        recursiveSharingPanel.getTree().addTreeSelectionListener(new EnablementSelectionListener());
    }

    public void actionPerformed(ActionEvent e) {
        File dir = (File) recursiveSharingPanel.getTree().getSelectionPath().getLastPathComponent();
        recursiveSharingPanel.removeRoot(dir);
    }

    /**
     * Enables action when a root folder is selected.
     */
    private class EnablementSelectionListener implements TreeSelectionListener {
        public void valueChanged(TreeSelectionEvent e) {
            Object obj = e.getPath().getLastPathComponent();
            if (obj instanceof File) {
                setEnabled(recursiveSharingPanel.isRoot(((File) obj)));
            } else {
                setEnabled(false);
            }
        }
    }
}