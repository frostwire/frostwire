package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.theme.SkinCheckBoxMenuItem;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.QuestionsHandler;
import org.limewire.setting.BooleanSetting;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Simple popup menu that shows the current columns,
 * and allows the user to display/hide them
 *
 * @author Sam Berlin
 * idea from the getColumnSelectionMenu in /search/TableColumnFilter
 */
class ColumnSelectionMenu {
    /**
     * More Options menu item.
     */
    private static final String MORE_OPTIONS =
            I18n.tr("More Options");
    /**
     * Setting for real-time sorting.
     */
    private static final String SORTING =
            I18n.tr("Sort Automatically");
    /**
     * Setting for displaying tooltips.
     */
    private static final String TOOLTIPS =
            I18n.tr("Extended Tooltips");
    /**
     * The string used to signify the columnId property
     */
    private static final String COLUMN_ID = "columnId";
    /**
     * The string used to signify the Setting property
     */
    private static final String SETTING = "setting";
    /**
     * The SettingListener to use for all menus.
     */
    private static final ActionListener SETTING_LISTENER =
            new SettingListener();
    /**
     * Revert to default string
     */
    private static final String REVERT_DEFAULT =
            I18n.tr("Revert To Default");
    /**
     * The actual popup menu.
     */
    private final JPopupMenu _menu = new SkinPopupMenu();
    /**
     * The LimeJTable this menu is associated with
     */
    private final LimeJTable _table;

    /**
     * Constructs the popupmenu & actionlistener associated with the
     * table & model.
     */
    ColumnSelectionMenu(LimeJTable table) {
        _table = table;
        DataLineModel<?, ?> model = (DataLineModel<?, ?>) _table.getModel();
        // add the 'revert to default' option.
        ActionListener reverter = new ReverterListener();
        JMenuItem revert = new SkinMenuItem(REVERT_DEFAULT);
        ColumnPreferenceHandler cph = _table.getColumnPreferenceHandler();
        TableSettings settings = _table.getTableSettings();
        //if there is no preferences handler or the values are already default,
        //disable the option
        if ((cph == null || cph.isDefault()) &&
                (settings == null || settings.isDefault()))
            revert.setEnabled(false);
        else
            revert.addActionListener(reverter);
        _menu.add(revert);
        // Add the options menu.
        if (settings != null) {
            JMenu options = createMoreOptions(settings);
            _menu.add(options);
        }
        _menu.addSeparator();
        addTableColumnChoices(new SelectionActionListener(), model, table);
    }

    /**
     * Returns a JMenu with the 'More Options' options tied to settings.
     */
    private static JMenu createMoreOptions(TableSettings settings) {
        JMenu options = new SkinMenu(MORE_OPTIONS);
        addSetting(options, SORTING, settings.REAL_TIME_SORT);
        addSetting(options, TOOLTIPS, settings.DISPLAY_TOOLTIPS);
        return options;
    }

    /**
     * Creates & adds a checkbox-setting with a listener.
     */
    private static void addSetting(JMenu parent,
                                   final String name,
                                   BooleanSetting setting) {
        JMenuItem item = new SkinCheckBoxMenuItem(name, setting.getValue());
        item.putClientProperty(SETTING, setting);
        item.addActionListener(SETTING_LISTENER);
        parent.add(item);
    }

    /**
     * Adds the table choices.
     */
    private void addTableColumnChoices(ActionListener listener,
                                       DataLineModel<?, ?> model,
                                       LimeJTable table) {
        for (int i = 0; i < model.getColumnCount(); i++) {
            JMenuItem item = createColumnMenuItem(listener, model, table, i);
            _menu.add(item);
        }
    }

    /**
     * Creates a single menu item for a column.
     */
    private JMenuItem createColumnMenuItem(ActionListener listener,
                                           DataLineModel<?, ?> model,
                                           LimeJTable table,
                                           int i) {
        Object id = model.getColumnId(i);
        String name = model.getColumnName(i);
        JCheckBoxMenuItem item =
                new SkinCheckBoxMenuItem(name, table.isColumnVisible(id));
        item.putClientProperty(COLUMN_ID, id);
        item.addActionListener(listener);
        return item;
    }

    /**
     * Returns the popup menu
     */
    public JPopupMenu getComponent() {
        return _menu;
    }

    /**
     * Simple class that deals with setting/unsetting settings.
     */
    static class SettingListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
            BooleanSetting setting =
                    (BooleanSetting) item.getClientProperty(SETTING);
            setting.setValue(item.getState());
        }
    }

    /**
     * Simple ActionListener class that will display/hide a column
     * based on the columnId property of the source.
     */
    class SelectionActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
            try {
                _table.setColumnVisible(item.getClientProperty(COLUMN_ID),
                        item.getState());
                _table.getTableHeader().setDraggedColumn(null);
            } catch (LastColumnException ee) {
                GUIMediator.showError(I18n.tr("You cannot turn off all columns."),
                        QuestionsHandler.REMOVE_LAST_COLUMN);
            }
        }
    }

    /**
     * Simple class that calls 'revertToDefault' on the ColumnPreferenceHandler
     * of the LimeJTable
     */
    class ReverterListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            _table.getColumnPreferenceHandler().revertToDefault();
            _table.getTableSettings().revertToDefault();
        }
    }
}