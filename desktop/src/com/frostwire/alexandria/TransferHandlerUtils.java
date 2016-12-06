/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.alexandria;

import com.frostwire.gui.player.MediaPlayer;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.File;

/**
 * Created on 12/6/16.
 * @author gubatron
 * @author aldenml
 */
public class TransferHandlerUtils {
    public static boolean supportCanImport(DataFlavor dataFlavor, TransferHandler.TransferSupport support, TransferHandler fallbackTransferHandler, boolean fallback) {
        if (support.isDataFlavorSupported(dataFlavor)) {
            return true;
        } else if (DNDUtils.containsFileFlavors(support.getDataFlavors())) {
            if (OSUtils.isMacOSX()) {
                return true;
            }
            try {
                File[] files = DNDUtils.getFiles(support.getTransferable());
                if (containsPlayableFile(files)) {
                    return true;
                }
                if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                    return true;
                }
                return fallback && fallbackTransferHandler != null ? fallbackTransferHandler.canImport(support) : false;
            } catch (InvalidDnDOperationException e) {
                // this case seems to be something special with the OS
                return true;
            } catch (Exception e) {
                return fallback && fallbackTransferHandler != null ? fallbackTransferHandler.canImport(support) : false;
            }
        }
        return false;
    }

    public static boolean containsPlayableFile(File[] files) {
        for (File file : files) {
            if (MediaPlayer.isPlayableFile(file)) {
                return true;
            } else if (file.isDirectory()) {
                if (com.frostwire.gui.library.LibraryUtils.directoryContainsAudio(file)) {
                    return true;
                }
            }
        }
        return false;
    }

}
