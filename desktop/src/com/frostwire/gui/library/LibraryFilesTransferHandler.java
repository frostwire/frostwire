/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.File;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

import org.limewire.util.OSUtils;

import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.settings.LibrarySettings;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
final class LibraryFilesTransferHandler extends TransferHandler {

    private static final long serialVersionUID = -3874985752229848555L;

    private static final Logger LOG = Logger.getLogger(LibraryFilesTransferHandler.class);

    private final JTree tree;

    public LibraryFilesTransferHandler(JTree tree) {
        this.tree = tree;
    }

    @Override
    public boolean canImport(TransferSupport support) {

        try {
            LibraryNode node = getNodeFromLocation(support.getDropLocation());

            if (!(node instanceof DirectoryHolderNode)) {
                return false;
            }

            if (node instanceof DirectoryHolderNode) {
                DirectoryHolder dirHolder = ((DirectoryHolderNode) node).getDirectoryHolder();
                
                //dropping folder or folders on file types and finished downloads.
                if (droppingFoldersToAddToLibrary(support, dirHolder,true)) {
                   return true;
                }
                
                if ((!(dirHolder instanceof MediaTypeSavedFilesDirectoryHolder) || !((MediaTypeSavedFilesDirectoryHolder) dirHolder).getMediaType().equals(MediaType.getAudioMediaType())) && !(dirHolder instanceof StarredDirectoryHolder)) {
                    return false;
                }
            }

            if (support.isDataFlavorSupported(LibraryPlaylistsTableTransferable.ITEM_ARRAY)) {
                return true;
            } else if (DNDUtils.containsFileFlavors(support.getDataFlavors())) {
                if (OSUtils.isMacOSX()) {
                    return true;
                }

                try {
                    File[] files = DNDUtils.getFiles(support.getTransferable());
                    for (File file : files) {
                        if (MediaPlayer.isPlayableFile(file)) {
                            return true;
                        } else if (file.isDirectory()) {
                            if (LibraryUtils.directoryContainsAudio(file)) {
                                return true;
                            }
                        }
                    }
                    if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                        return true;
                    }
                } catch (InvalidDnDOperationException e) {
                    // this case seems to be something special with the OS
                    return true;
                }
            }
        } catch (Throwable e) {
            LOG.error("Error in LibraryFilesTransferHandler processing", e);
        }

        return false;
    }

    private boolean droppingFoldersToAddToLibrary(TransferSupport support, DirectoryHolder dirHolder, boolean invokingFromCanImport) throws UnsupportedFlavorException, IOException {
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
        for (File f: files) {
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
            Transferable transferable = support.getTransferable();
            LibraryNode node = getNodeFromLocation(support.getDropLocation());

             {

                if (node instanceof DirectoryHolderNode) {
                    DirectoryHolder dirHolder = ((DirectoryHolderNode)node).getDirectoryHolder();
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
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else if (DNDUtils.contains(transferable.getTransferDataFlavors(), LibraryPlaylistsTableTransferable.ITEM_ARRAY)) {
                    PlaylistItem[] playlistItems = LibraryUtils.convertToPlaylistItems((LibraryPlaylistsTableTransferable.Item[]) transferable.getTransferData(LibraryPlaylistsTableTransferable.ITEM_ARRAY));
                    LibraryUtils.createNewPlaylist(playlistItems, isStarredDirectoryHolder(support.getDropLocation()));
                } else {
                    File[] files = DNDUtils.getFiles(support.getTransferable());
                    if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                        LibraryUtils.createNewPlaylist(files[0], isStarredDirectoryHolder(support.getDropLocation()));
                    } else {
                        LibraryUtils.createNewPlaylist(files, isStarredDirectoryHolder(support.getDropLocation()));
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

    private boolean isStarredDirectoryHolder(DropLocation location) {
        LibraryNode node = getNodeFromLocation(location);
        if (node instanceof DirectoryHolderNode) {
            DirectoryHolder dirHolder = ((DirectoryHolderNode) node).getDirectoryHolder();
            return dirHolder instanceof StarredDirectoryHolder;
        } else {
            return false;
        }
    }

    private LibraryNode getNodeFromLocation(DropLocation location) {
        TreePath path = tree.getUI().getClosestPathForLocation(tree, location.getDropPoint().x, location.getDropPoint().y);
        return path != null ? (LibraryNode) path.getLastPathComponent() : null;
    }
}
