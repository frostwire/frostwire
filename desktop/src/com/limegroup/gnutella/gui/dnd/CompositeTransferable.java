package com.limegroup.gnutella.gui.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A Transferable that composes two different ones, knowing
 * which to retrieve based on the supported flavor.
 */
public class CompositeTransferable implements Transferable {
    
    private final Transferable t1;
    private final Transferable t2;

    public CompositeTransferable(Transferable t1, Transferable t2) {
        this.t1 = t1;
        this.t2 = t2;
    }

    public DataFlavor[] getTransferDataFlavors() {
        List<DataFlavor> flavors = new LinkedList<DataFlavor>();
        flavors.addAll(Arrays.asList(t1.getTransferDataFlavors()));
        flavors.addAll(Arrays.asList(t2.getTransferDataFlavors()));
        return flavors.toArray(new DataFlavor[flavors.size()]);
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return t1.isDataFlavorSupported(flavor) || t2.isDataFlavorSupported(flavor);
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if(t1.isDataFlavorSupported(flavor))
            return t1.getTransferData(flavor);
        else if(t2.isDataFlavorSupported(flavor))
            return t2.getTransferData(flavor);
        else
            throw new UnsupportedFlavorException(flavor);
    }

}
