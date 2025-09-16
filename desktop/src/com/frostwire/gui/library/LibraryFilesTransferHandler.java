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
