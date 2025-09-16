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