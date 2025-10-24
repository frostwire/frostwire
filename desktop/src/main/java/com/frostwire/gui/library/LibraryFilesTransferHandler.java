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

package com.frostwire.gui.library;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.settings.LibrarySettings;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.io.File;

/**
 * The Transfer Handler on the left hand side tree node that help us navigate downloaded files by media type.
 *
 * @author gubatron
 * @author aldenml
 * @see LibraryFilesTableTransferHandler for the handler of the corresponding right hand side table.
 */
final class LibraryFilesTransferHandler extends TransferHandler {
    private static final Logger LOG = Logger.getLogger(LibraryFilesTransferHandler.class);
    private final JTree tree;

    LibraryFilesTransferHandler(JTree tree) {
        this.tree = tree;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        try {
            LibraryNode node = getNodeFromLocation(support.getDropLocation());
            if (!(node instanceof DirectoryHolderNode)) {
                return false;
            }
            DirectoryHolder dirHolder = ((DirectoryHolderNode) node).getDirectoryHolder();
            // dropping folder or folders on file types and finished downloads.
            if (droppingFoldersToAddToLibrary(support, dirHolder, true)) {
                return true;
            }
            if (!(dirHolder instanceof MediaTypeSavedFilesDirectoryHolder)) {
                return false;
            }
            MediaTypeSavedFilesDirectoryHolder mediaTypeSavedFilesDirHolder = (MediaTypeSavedFilesDirectoryHolder) dirHolder;
            MediaType mt = mediaTypeSavedFilesDirHolder.getMediaType();
            return mt.equals(MediaType.getAudioMediaType()) &&
                    DNDUtils.supportCanImport(support.getDataFlavors()[0], support, null, false);
        } catch (Throwable e) {
            LOG.error("Error in LibraryFilesTransferHandler processing", e);
        }
        return false;
    }

    private boolean droppingFoldersToAddToLibrary(TransferSupport support, DirectoryHolder dirHolder, boolean invokingFromCanImport) {
        try {
            //Mac doesn't have the data flavors until importData() is invoked.
            if (invokingFromCanImport && OSUtils.isMacOSX()) {
                return true;
            }
            return isSharedFolderReceiver(dirHolder) &&
                    DNDUtils.containsFileFlavors(support.getDataFlavors()) &&
                    areAllFilesDirectories(DNDUtils.getFiles(support.getTransferable()));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean areAllFilesDirectories(File[] files) {
        boolean result = true;
        for (File f : files) {
            if (!f.isDirectory()) {
                result = false;
                break;
            }
        }
        return result;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        try {
            LibraryNode node = getNodeFromLocation(support.getDropLocation());
            {
                if (node instanceof DirectoryHolderNode) {
                    DirectoryHolder dirHolder = ((DirectoryHolderNode) node).getDirectoryHolder();
                    if (droppingFoldersToAddToLibrary(support, dirHolder, false)) {
                        try {
                            //add to library
                            File[] files = DNDUtils.getFiles(support.getTransferable());
                            for (File f : files) {
                                LibrarySettings.DIRECTORIES_TO_INCLUDE.add(f);
                                LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.remove(f);
                            }
                            LibraryMediator.instance().clearDirectoryHolderCaches();
                            //show tools -> library option pane
                            GUIMediator.instance().setOptionsVisible(true, OptionsConstructor.LIBRARY_KEY);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.error("Error in LibraryFilesTransferHandler processing", e);
        }
        return false;
    }

    /**
     * Checks if this directory holder is either one of:
     * Audio,Video,Images,Documents,Apps,Torrents or Finished Downloads directory holder.
     *
     * @param directoryHolder
     * @return
     */
    private boolean isSharedFolderReceiver(DirectoryHolder directoryHolder) {
        return directoryHolder instanceof MediaTypeSavedFilesDirectoryHolder ||
                directoryHolder instanceof FileSettingDirectoryHolder;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE | LINK;
    }

    private LibraryNode getNodeFromLocation(DropLocation location) {
        TreePath path = tree.getUI().getClosestPathForLocation(tree, location.getDropPoint().x, location.getDropPoint().y);
        return path != null ? (LibraryNode) path.getLastPathComponent() : null;
    }
}
