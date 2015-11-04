package com.limegroup.gnutella.gui.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JComponent;

/**
 * Forwards implemented methods to list of handlers until it finds one
 * that returns <code>true</code>. 
 */
public class MulticastTransferHandler extends LimeTransferHandler {

	/**
     * 
     */
    private static final long serialVersionUID = -400036212482333760L;

    private ArrayList<LimeTransferHandler> handlers = new ArrayList<LimeTransferHandler>();
	
	private LimeTransferHandler lastTransferer;
	
	
	public MulticastTransferHandler() {
	}
	
	public MulticastTransferHandler(LimeTransferHandler handler) {
		handlers.add(handler);
	}
	
	public MulticastTransferHandler(LimeTransferHandler head, 
			Collection<LimeTransferHandler> tail) {
		handlers.add(head);
		handlers.addAll(tail);
	}
	
	public MulticastTransferHandler(Collection<LimeTransferHandler> defaultHandlers) {
		handlers.addAll(defaultHandlers);
	}
	
	public void addTransferHandler(LimeTransferHandler handler) {
		handlers.add(handler);
	}
	
	public void removeTransferHandler(LimeTransferHandler handler) {
		handlers.remove(handler);
	}
	
	private LimeTransferHandler[] getHandlers() {
		return handlers.toArray(new LimeTransferHandler[handlers.size()]);
	}
	
	@Override
	public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
//		// TODO dnd hack to ignore internal file drops 
//		if (DNDUtils.containsLibraryFlavors(flavors)) {
//			return false;
//		}
		for (LimeTransferHandler handler : getHandlers()) {
			if (handler.canImport(c, flavors, ddi)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
//		// TODO dnd hack to ignore internal file drops
//		if (DNDUtils.containsLibraryFlavors(transferFlavors)) {
//			return false;
//		}
		for (LimeTransferHandler handler : getHandlers()) {
			if (handler.canImport(comp, transferFlavors)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	protected Transferable createTransferable(JComponent c) {
		for (LimeTransferHandler handler : getHandlers()) {
			Transferable t = handler.createTransferable(c);
			if (t != null) {
				lastTransferer = handler;
				return t;
			}
		}
		lastTransferer = null;
		return null;
	}
	
	@Override
	public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
		for (LimeTransferHandler handler : getHandlers()) {
			if (handler.importData(c, t, ddi)) {
				return true;
			}
		}
		return false;
	}
	@Override
	public boolean importData(JComponent comp, Transferable t) {
		for (LimeTransferHandler handler : getHandlers()) {
			if (handler.importData(comp, t)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public int getSourceActions(JComponent c) {
		int sourceActions = NONE;
		for (LimeTransferHandler handler : getHandlers()) {
			sourceActions |= handler.getSourceActions(c);
		}
		return sourceActions;
	}
	
	@Override
	protected void exportDone(JComponent source, Transferable data, int action) {
		if (lastTransferer != null) {
			lastTransferer.exportDone(source, data, action);
			lastTransferer = null;
		}
	}
}
