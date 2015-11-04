package com.limegroup.gnutella.gui.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

import com.limegroup.gnutella.gui.dnd.BasicDropTargetListener.DropDragInfo;
import com.limegroup.gnutella.gui.dnd.BasicDropTargetListener.DropDropInfo;

/**
 * DropTargetListener that services a {@link LimeTransferHandler} that is
 * handed to it. This listener can be registered on components that are not
 * JComponents and therefore don't have a {@link JComponent#getTransferHandler()}.
 * 
 * {@link LimeTransferHandler LimeTransferHandlers} should not access the
 * JComponent argument handed to them since it will be <code>null</code>.
 */
public class TransferHandlerDropTargetListener implements DropTargetListener {

	private final LimeTransferHandler handler;
	
	public TransferHandlerDropTargetListener(LimeTransferHandler handler) {
		if (handler == null) {
			throw new NullPointerException("handler must not be null");
		}
		this.handler = handler;
	}
	
	public void dragEnter(DropTargetDragEvent e) {
		DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropDragInfo ddi = new DropDragInfo(e);
        
        if(handler.canImport(null, flavors, ddi) && actionSupported(ddi.action))
            e.acceptDrag(ddi.action);
        else
            e.rejectDrag();
	}

	public void dragExit(DropTargetEvent e) {
		  
	}

	public void dragOver(DropTargetDragEvent e) {
		DataFlavor[] flavors = e.getCurrentDataFlavors();
		DropDragInfo ddi = new DropDragInfo(e);
		
		if(handler.canImport(null, flavors, ddi) && actionSupported(ddi.action))
			e.acceptDrag(ddi.action);
		else
			e.rejectDrag();
	}

	public void drop(DropTargetDropEvent e) {
		DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropDropInfo ddi = new DropDropInfo(e);
        
        if(handler.canImport(null, flavors, ddi) && actionSupported(ddi.action)) {
            e.acceptDrop(ddi.action);
            try {
                Transferable t = e.getTransferable();
                e.dropComplete(handler.importData(null, t, ddi));
            } catch (RuntimeException re) {
                e.dropComplete(false);
            }
        } else {
            e.rejectDrop();
        }
	}

	public void dropActionChanged(DropTargetDragEvent e) {
		DataFlavor[] flavors = e.getCurrentDataFlavors();
		DropDragInfo ddi = new DropDragInfo(e);
		
		if(handler.canImport(null, flavors, ddi) && actionSupported(ddi.action))
			e.acceptDrag(ddi.action);
		else
			e.rejectDrag();
	}

	private boolean actionSupported(int action) {
        return (action & (TransferHandler.COPY_OR_MOVE |DnDConstants.ACTION_LINK )) != TransferHandler.NONE;
    }
	
}
