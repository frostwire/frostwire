package com.limegroup.gnutella.gui.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.limewire.util.OSUtils;

/**
 * A Transferable that returns a javaFileListFlavor, built up from
 * a list of 'FileTransfer' and 'LazyFileTransfer' items.  The lazy
 * items will be retrieved only when the data of the transfer is requested
 * (in response to a getTransferData call).
 */
public class FileTransferable implements Transferable {
    
	private final List<File> files;
	
    private final List<? extends FileTransfer> lazyFiles;
	
    /**
	 * Holds the data flavor used on linux desktops for file drags.
	 */
	public static final DataFlavor URIFlavor = createURIFlavor();
	
	public static final DataFlavor URIFlavor16 = createURIFlavor16();
	
	public static final List<? extends FileTransfer> EMPTY_FILE_TRANSFER_LiST =
		Collections.emptyList();
	
	public static final List<File> EMPTY_FILE_LIST = Collections.emptyList();
	
	static DataFlavor createURIFlavor() {
		try {
			return new DataFlavor("text/uri-list;class=java.lang.String");
		} catch (ClassNotFoundException cnfe) {
			return null;
		}
	}

	static DataFlavor createURIFlavor16() {
		try {
			return new DataFlavor("text/uri-list;representationclass=java.lang.String");
		} catch (ClassNotFoundException cnfe) {
			return null;
		}
	}
	
	
	public FileTransferable(List<File> files) {
        this(files, EMPTY_FILE_TRANSFER_LiST);
    }
	
    /**
     * @param realFiles
     * @param lazyFiles
     */
	public FileTransferable(List<File> realFiles,
			List<? extends FileTransfer> lazyFiles) {
		if (realFiles == null) { 
			throw new NullPointerException("realFiles must not be null");
		}
		if (lazyFiles == null) {
			throw new NullPointerException("lazyFiles must not be empty");
		}
		// copy, given list might not me mutable
        this.files = new ArrayList<File>(realFiles);
        this.lazyFiles = new ArrayList<FileTransfer>(lazyFiles);
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
      throws UnsupportedFlavorException, IOException {
    	if (flavor.equals(DataFlavor.javaFileListFlavor)) {
    		return getFiles();
    	} else if (URIFlavor.equals(flavor) || URIFlavor16.equals(flavor)) {
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
    	}
    	else {
    		throw new UnsupportedFlavorException(flavor);
    	}
    }

    public DataFlavor[] getTransferDataFlavors() {
        if(OSUtils.isWindows()) {
            return new DataFlavor[] { DataFlavor.javaFileListFlavor };
        } else {
            return new DataFlavor[] { DataFlavor.javaFileListFlavor, URIFlavor, URIFlavor16 };
        }
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(DataFlavor.javaFileListFlavor)
        	|| flavor.equals(URIFlavor)
        	|| flavor.equals(URIFlavor16);
    }
}