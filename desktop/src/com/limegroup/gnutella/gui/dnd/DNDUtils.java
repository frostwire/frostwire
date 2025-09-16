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

package com.limegroup.gnutella.gui.dnd;

import com.frostwire.gui.player.MediaPlayer;
import com.limegroup.gnutella.util.URIUtils;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Static helper class with DND tasks that provides methods for handling
 * URI and file drops and also provides default transfer handlers.
 */
public class DNDUtils {
    /**
     * Immutable list of default transfer handlers that should be chained
     * after a specific one.
     */
    public static final List<LimeTransferHandler> DEFAULT_TRANSFER_HANDLERS;
    /**
     * Default transfer handler supporting drops for all flavors limewire
     * is interested in.
     */
    public static final LimeTransferHandler DEFAULT_TRANSFER_HANDLER;

    static {
        /*
         * The transfer handlers added here should not access the JComponent
         * given to them since it is null, as the global drop target installed
         * on limewire's JFrame is not a JComponent.
         * See TransferHandlerDropTargetListener to see how they are invoked
         */
        DEFAULT_TRANSFER_HANDLERS = List.of(new MagnetTransferHandler(), new TorrentURITransferHandler(), new TorrentFilesTransferHandler(), new SendFileTransferHandler());
        DEFAULT_TRANSFER_HANDLER = new MulticastTransferHandler(DEFAULT_TRANSFER_HANDLERS);
    }

    /**
     * Returns array of uris extracted from transferable.
     */
    static URI[] getURIs(Transferable transferable) throws UnsupportedFlavorException, IOException {
        String lines = (String) (contains(transferable.getTransferDataFlavors(), FileTransferable.URIFlavor) ? transferable
                .getTransferData(FileTransferable.URIFlavor) : transferable.getTransferData(FileTransferable.URIFlavor16));
        StringTokenizer st = new StringTokenizer(lines, System.getProperty("line.separator"));
        ArrayList<URI> uris = new ArrayList<>();
        while (st.hasMoreTokens()) {
            String line = st.nextToken().trim();
            if (line.length() == 0) {
                continue;
            }
            try {
                URI uri = URIUtils.toURI(line);
                uris.add(uri);
            } catch (URISyntaxException e) {
                // ignore
            }
        }
        return uris.toArray(new URI[0]);
    }

    /**
     * Returns true if the flavor is contained in the array.
     */
    public static boolean contains(DataFlavor[] array, DataFlavor flavor) {
        for (DataFlavor anArray : array) {
            if (flavor.equals(anArray)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks for {@link DataFlavor#javaFileListFlavor} and
     * {@link FileTransferable#URIFlavor} for unix systems.
     */
    public static boolean containsFileFlavors(DataFlavor[] flavors) {
        return flavors != null && (contains(flavors, DataFlavor.javaFileListFlavor) || contains(flavors, FileTransferable.URIFlavor) || contains(flavors, FileTransferable.URIFlavor16));
    }

    /**
     * Extracts the array of files from a transferable
     *
     * @return an empty array if the transferable does not contain any data
     * that can be interpreted as a list of files
     */
    @SuppressWarnings("unchecked")
    public static File[] getFiles(Transferable transferable) throws UnsupportedFlavorException, IOException {
        if (contains(transferable.getTransferDataFlavors(), DataFlavor.javaFileListFlavor)) {
            return ((List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor)).toArray(new File[0]);
        } else if (contains(transferable.getTransferDataFlavors(), FileTransferable.URIFlavor)
                || contains(transferable.getTransferDataFlavors(), FileTransferable.URIFlavor16)) {
            return getFiles(getURIs(transferable));
        }
        return new File[0];
    }

    /**
     * Returns array of files for uris that denote local paths.
     *
     * @return empty array if no uri denotes a local file
     */
    private static File[] getFiles(URI[] uris) {
        ArrayList<File> files = new ArrayList<>(uris.length);
        for (URI uri : uris) {
            String scheme = uri.getScheme();
            if (uri.isAbsolute() && scheme != null && scheme.equalsIgnoreCase("file")) {
                String path = uri.getPath();
                files.add(new File(path));
            }
        }
        return files.toArray(new File[0]);
    }

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
                //noinspection SimplifiableIfStatement
                if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                    return true;
                }
                return (fallback && fallbackTransferHandler != null) && fallbackTransferHandler.canImport(support);
            } catch (InvalidDnDOperationException e) {
                // this case seems to be something special with the OS
                return true;
            } catch (Exception e) {
                return (fallback && fallbackTransferHandler != null) && fallbackTransferHandler.canImport(support);
            }
        }
        return false;
    }

    private static boolean containsPlayableFile(File[] files) {
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
