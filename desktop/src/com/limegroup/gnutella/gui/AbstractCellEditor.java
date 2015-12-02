package com.limegroup.gnutella.gui;

import java.util.EventObject;

import javax.swing.CellEditor;
import javax.swing.event.CellEditorListener;
import javax.swing.event.EventListenerList;
public class AbstractCellEditor implements CellEditor {

    public Object getCellEditorValue() { return null; }
    public boolean isCellEditable(EventObject e) { return true; }
    public boolean shouldSelectCell(EventObject anEvent) { return false; }
    public boolean stopCellEditing() { return true; }
    public void cancelCellEditing() {}

    public void addCellEditorListener(CellEditorListener l) {
    }

    public void removeCellEditorListener(CellEditorListener l) {
    }
}
