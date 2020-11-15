package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.tables.DataLineModel;
import com.limegroup.gnutella.gui.tables.DefaultColumnPreferenceHandler;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;

/**
 * Column preference handler for Search columns.
 * <p>
 * Extends DefaultColumnPreferenceHandler to store/read data in memory
 * instead of to/from disk.
 */
final class SearchColumnPreferenceHandler
        extends DefaultColumnPreferenceHandler {
    SearchColumnPreferenceHandler(LimeJTable table) {
        super(table);
    }

    protected void setVisibility(LimeTableColumn col, boolean vis) {
        ((SearchColumn) col).setCurrentVisibility(vis);
    }

    protected void setOrder(LimeTableColumn col, int order) {
        ((SearchColumn) col).setCurrentOrder(order);
    }

    protected void setWidth(LimeTableColumn col, int width) {
        ((SearchColumn) col).setCurrentWidth(width);
    }

    protected boolean getVisibility(LimeTableColumn col) {
        return ((SearchColumn) col).getCurrentVisibility();
    }

    protected int getOrder(LimeTableColumn col) {
        return ((SearchColumn) col).getCurrentOrder();
    }

    protected int getWidth(LimeTableColumn col) {
        return ((SearchColumn) col).getCurrentWidth();
    }

    protected boolean isDefaultWidth(LimeTableColumn col) {
        return ((SearchColumn) col).getCurrentWidth() ==
                col.getDefaultWidth();
    }

    protected boolean isDefaultOrder(LimeTableColumn col) {
        return ((SearchColumn) col).getCurrentOrder() ==
                col.getDefaultOrder();
    }

    protected boolean isDefaultVisibility(LimeTableColumn col) {
        return ((SearchColumn) col).getCurrentOrder() ==
                col.getDefaultOrder();
    }

    protected void save() {
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) table.getModel();
        for (int i = 0; i < dlm.getColumnCount(); i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            super.setVisibility(ltc, getVisibility(ltc));
            super.setOrder(ltc, getOrder(ltc));
            super.setWidth(ltc, getWidth(ltc));
        }
        super.save();
    }
}
    