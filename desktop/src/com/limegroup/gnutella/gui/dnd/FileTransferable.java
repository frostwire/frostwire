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

package com.limegroup.gnutella.gui.dnd;

import org.limewire.util.OSUtils;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Transferable that returns a javaFileListFlavor, built up from
 * a list of 'FileTransfer' and 'LazyFileTransfer' items.  The lazy
 * items will be retrieved only when the data of the transfer is requested
 * (in response to a getTransferData call).
 */
public class FileTransferable implements Transferable {
    /**
     * Holds the data flavor used on linux desktops for file drags.
     */
    static final DataFlavor URIFlavor = createURIFlavor();
    static final DataFlavor URIFlavor16 = createURIFlavor16();
    private static final List<? extends FileTransfer> EMPTY_FILE_TRANSFER_LiST =
            Collections.emptyList();
    private final List<File> files;
    private final List<? extends FileTransfer> lazyFiles;

    public FileTransferable(List<File> files) {
        this(files, EMPTY_FILE_TRANSFER_LiST);
    }

    /**
     * @param realFiles
     * @param lazyFiles
     */
    FileTransferable(List<File> realFiles,
                     List<? extends FileTransfer> lazyFiles) {
        if (realFiles == null) {
            throw new NullPointerException("realFiles must not be null");
        }
        if (lazyFiles == null) {
            throw new NullPointerException("lazyFiles must not be empty");
        }
        // copy, given list might not me mutable
        this.files = new ArrayList<>(realFiles);
        this.lazyFiles = new ArrayList<>(lazyFiles);
    }

    private static DataFlavor createURIFlavor() {
        try {
            return new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException cnfe) {
            return null;
        }
    }

    private static DataFlavor createURIFlavor16() {
        try {
            return new DataFlavor("text/uri-list;representationclass=java.lang.String");
        } catch (ClassNotFoundException cnfe) {
            return null;
        }
    }

    private List<File> getFiles() {
        if (!lazyFiles.isEmpty()) {
            for (FileTransfer transfer : lazyFiles) {
                File f = transfer.getFile();
                if (f != null)
                    files.add(f);
            }
            lazyFiles.clear();
        }
        return files;
    }

    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException {
        if (flavor.equals(DataFlavor.javaFileListFlavor)) {
            return getFiles();
        } else if ((URIFlavor != null && URIFlavor.equals(flavor)) ||
                (URIFlavor16 != null && URIFlavor16.equals(flavor))) {
            StringBuilder sb = new StringBuilder();
            String lineSep = System.getProperty("line.separator");
            for (File file : getFiles()) {
                URI uri = file.toURI();
                if (sb.length() > 0) {
                    sb.append(lineSep);
                }
                sb.append(uri.toString());
            }
            return sb.toString();
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }

    public DataFlavor[] getTransferDataFlavors() {
        if (OSUtils.isWindows()) {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        } else {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor, URIFlavor, URIFlavor16};
        }
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(DataFlavor.javaFileListFlavor)
                || flavor.equals(URIFlavor)
                || flavor.equals(URIFlavor16);
    }
}