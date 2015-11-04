package com.limegroup.gnutella.gui.dnd;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JComponent;

/**
 * A simple transferable creator that can created a FileTransferable
 * from the selection of a LimeJTable.
 */
class BasicTransferableCreator {
    
  //  private JComponent component;
    
    BasicTransferableCreator(JComponent component) {
    //    this.component = component;
    }
    
    Transferable getTransferable() {
        List<File> l = new LinkedList<File>();
        List<FileTransfer> lazy = new LinkedList<FileTransfer>();
  
//      TODO dnd
//        if (component instanceof LimeJTable)
//            fillFromTable(component, l, lazy);
        
        if(l.size() == 0 && lazy.size() == 0)
            return null;
        
       return new FileTransferable(l, lazy);
    }
    
    /**
     * Fills up the lists 'l' and 'lazy' with files or Lazy Files.
     */
    // commented out due to use of removed methods
//    private void fillFromTable(JComponent jc, List l, List lazy) {
//        LimeJTable table = (LimeJTable)jc;
//        DataLine[] lines = table.getSelectedDataLines();
//        for(int i = 0; i < lines.length; i++)
//    TODO dnd file transfer/lazy file transfer interfaces slightly changed
//            addFileTransfer((FileTransfer)lines[i], l, lazy);
//    }
    
    /**
     * Adds the specified FileTransfer to the lists.
     */
//    private void addFileTransfer(FileTransfer transfer, List l, List lazy) {
//        File f = transfer.getFile();
//        
//        if(f == null) {
//            if(transfer instanceof LazyFileTransfer)
//                lazy.add(((LazyFileTransfer)transfer).getFileTransfer());
//        } else {
//            addFile(l, f);
//        }
//    }
    
    /**
     * Adds a file to a list as the canonical file.
     */
//    private void addFile(List l, File f) {
//        try {
//            f = f.getCanonicalFile();
//        } catch(IOException ignored) {}
//        l.add(f);
//    }     

}
