/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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