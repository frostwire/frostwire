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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.gui.library.AddLibraryDirectoryAction;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.RecursiveLibraryDirectoryPanel;
import com.frostwire.gui.library.RemoveLibraryDirectoryAction;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.SharingSettings;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class defines the panel in the options window that allows the user
 * to change the directory that are shared.
 */
public final class LibraryFoldersPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Library Included Folders");
    private final static String LABEL = I18n.tr("You can choose the folders for include files when browsing the library.");
    private final JButton buttonRemoveLibraryDirectory;
    private final RecursiveLibraryDirectoryPanel directoryPanel = new RecursiveLibraryDirectoryPanel(true);
    private Set<File> initialFoldersToInclude;
    private Set<File> initialFoldersToExclude;

    public LibraryFoldersPaneItem() {
        super(TITLE, LABEL);
        boolean isPortable = CommonUtils.isPortable();
        JButton buttonAddLibraryDirectory = new JButton(new AddLibraryDirectoryAction(directoryPanel, directoryPanel));
        buttonRemoveLibraryDirectory = new JButton(new RemoveLibraryDirectoryAction(directoryPanel));
        directoryPanel.getTree().setRootVisible(false);
        directoryPanel.getTree().setShowsRootHandles(true);
        if (!isPortable) {
            directoryPanel.getTree().addTreeSelectionListener(e -> {
                Object comp = e.getPath().getLastPathComponent();
                if (comp instanceof File) {
                    if (comp.equals(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue())) {
                        buttonRemoveLibraryDirectory.setEnabled(false);
                        return;
                    }
                }
                buttonRemoveLibraryDirectory.setEnabled(true);
            });
        } else {
            removeTreeSelectionListeners();
        }
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(new EmptyBorder(0, 4, 0, 0));
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(buttonAddLibraryDirectory, BorderLayout.NORTH);
        buttons.add(Box.createVerticalStrut(4), BorderLayout.CENTER);
        buttons.add(buttonRemoveLibraryDirectory, BorderLayout.SOUTH);
        buttonPanel.add(buttons, BorderLayout.NORTH);
        directoryPanel.addEastPanel(buttonPanel);
        add(directoryPanel);
        directoryPanel.getTree().setEnabled(!isPortable);
        buttonAddLibraryDirectory.setEnabled(!isPortable);
        buttonRemoveLibraryDirectory.setEnabled(!isPortable);
    }

    private void removeTreeSelectionListeners() {
        TreeSelectionListener[] treeSelectionListeners = directoryPanel.getTree().getTreeSelectionListeners();
        for (TreeSelectionListener tsl : treeSelectionListeners) {
            directoryPanel.getTree().removeTreeSelectionListener(tsl);
        }
    }

    /**
     * Defines the abstract method in `AbstractPaneItem`.
     * <p>
     * Sets the options for the fields in this `PaneItem` when the
     * window is shown.
     */
    public void initOptions() {
        initialFoldersToInclude = LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue();
        initialFoldersToExclude = LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue();
        List<File> roots = new ArrayList<>(LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue());
        roots.addAll(LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
        directoryPanel.setRoots(roots.toArray(new File[0]));
        directoryPanel.setFoldersToExclude(initialFoldersToExclude);
    }

    /**
     * Defines the abstract method in `AbstractPaneItem`.
     * <p>
     * This makes sure that the shared directories have, in fact, changed to
     * make sure that we don't load the `FileManager` twice.  This is
     * particularly relevant to the case where the save directory has changed,
     * in which case we only want to reload the `FileManager` once for
     * any changes.
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     **/
    public boolean applyOptions() {
        LibrarySettings.DIRECTORIES_TO_INCLUDE.setValue(new HashSet<>());
        LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.setValue(new HashSet<>());
        for (File f : directoryPanel.getRootsToInclude()) {
            if (f != null) {
                LibrarySettings.DIRECTORIES_TO_INCLUDE.add(f);
            }
        }
        for (File f : directoryPanel.getFoldersToExclude()) {
            if (f != null) {
                if (f.equals(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue())) {
                    LibrarySettings.DIRECTORIES_TO_INCLUDE.add(f);
                } else {
                    System.out.println("Not including " + f.getAbsolutePath());
                    LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.add(f);
                }
            }
        }
        LibraryMediator.instance().clearDirectoryHolderCaches();
        return false;
    }

    public boolean isDirty() {
        return !initialFoldersToInclude.equals(directoryPanel.getRootsToInclude())
                || !initialFoldersToExclude.equals(directoryPanel.getFoldersToExclude());
    }
}





