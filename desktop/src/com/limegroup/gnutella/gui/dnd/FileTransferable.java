/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
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

import com.frostwire.util.OSUtils;

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
                sb.append(uri);
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