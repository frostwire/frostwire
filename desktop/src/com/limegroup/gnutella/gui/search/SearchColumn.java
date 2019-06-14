package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.settings.TablesHandlerSettings;

import javax.swing.*;

/**
 * Extends LimeTableColumn to store current width/order/visibility information.
 * <p>
 * Necessary for SearchColumnPreferenceHandler, to store data in memory instead
 * of disk, since multiple tables are active at once.
 */
class SearchColumn extends LimeTableColumn {
    /**
     *
     */
    private static final long serialVersionUID = -5131410067087443020L;
    private int _width;
    private int _order;
    private boolean _visible;

    /**
     * Creates a new column.
     */
    public SearchColumn(int model, final String id, final String name,
                        int width, boolean vis, Class<?> clazz) {
        this(model, id, name, null, width, vis, clazz);
    }

    /**
     * Creates a new column.
     */
    private SearchColumn(int model, final String id, final String name,
                         Icon icon, int width, boolean vis, Class<?> clazz) {
        super(model, id, name, icon, width, vis, clazz);
        _visible = TablesHandlerSettings.getVisibility(id, vis).getValue();
        _order = TablesHandlerSettings.getOrder(id, model).getValue();
        _width = TablesHandlerSettings.getWidth(id, width).getValue();
    }

    int getCurrentWidth() {
        return _width;
    }

    void setCurrentWidth(int width) {
        _width = width;
    }

    int getCurrentOrder() {
        return _order;
    }

    void setCurrentOrder(int order) {
        _order = order;
    }

    boolean getCurrentVisibility() {
        return _visible;
    }

    void setCurrentVisibility(boolean visible) {
        _visible = visible;
    }
}
    
    