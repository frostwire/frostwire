package com.frostwire.gui.theme;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

public class FilePaneEx extends FilePane {

    /**
     * 
     */
    private static final long serialVersionUID = 153183794490651820L;

    private SkinPopupMenu contextMenu;
    private SkinMenu viewMenu;

    private String viewMenuLabelText;

    private boolean listViewWindowsStyle;

    private static final int VIEWTYPE_COUNT = 2;

    private String[] viewTypeActionNames;

    public FilePaneEx(FileChooserUIAccessor fileChooserUIAccessor) {
        super(fileChooserUIAccessor);
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        Locale l = getFileChooser().getLocale();

        listViewWindowsStyle = UIManager.getBoolean("FileChooser.listViewWindowsStyle");

        viewMenuLabelText = UIManager.getString("FileChooser.viewMenuLabelText", l);

        viewTypeActionNames = new String[VIEWTYPE_COUNT];
        viewTypeActionNames[VIEWTYPE_LIST] = UIManager.getString("FileChooser.listViewActionLabelText", l);
        viewTypeActionNames[VIEWTYPE_DETAILS] = UIManager.getString("FileChooser.detailsViewActionLabelText", l);
    }

    @Override
    public JMenu getViewMenu() {
        if (viewMenu == null) {
            viewMenu = new SkinMenu(viewMenuLabelText);
            ButtonGroup viewButtonGroup = new ButtonGroup();

            for (int i = 0; i < VIEWTYPE_COUNT; i++) {
                SkinRadioButtonMenuItem mi = new SkinRadioButtonMenuItem(new ViewTypeAction(i));
                viewButtonGroup.add(mi);
                viewMenu.add(mi);
            }
            updateViewMenu();
        }
        return viewMenu;
    }

    private void updateViewMenu() {
        if (viewMenu != null) {
            Component[] comps = viewMenu.getMenuComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i] instanceof SkinRadioButtonMenuItem) {
                    SkinRadioButtonMenuItem mi = (SkinRadioButtonMenuItem) comps[i];
                    if (((ViewTypeAction) mi.getAction()).viewType == getViewType()) {
                        mi.setSelected(true);
                    }
                }
            }
        }
    }

    @Override
    public JPopupMenu getComponentPopupMenu() {
        SkinMenu viewMenu = (SkinMenu) getViewMenu();
        if (contextMenu == null) {
            contextMenu = new SkinPopupMenu();
            if (viewMenu != null) {
                contextMenu.add(viewMenu);
                if (listViewWindowsStyle) {
                    contextMenu.addSeparator();
                }
            }
            ActionMap actionMap = getActionMap();
            Action refreshAction = actionMap.get(ACTION_REFRESH);
            Action newFolderAction = actionMap.get(ACTION_NEW_FOLDER);
            if (refreshAction != null) {
                contextMenu.add(refreshAction);
                if (listViewWindowsStyle && newFolderAction != null) {
                    contextMenu.addSeparator();
                }
            }
            if (newFolderAction != null) {
                contextMenu.add(newFolderAction);
            }
        }
        if (viewMenu != null) {
            viewMenu.getPopupMenu().setInvoker(viewMenu);
        }
        return contextMenu;
    }

    class ViewTypeAction extends AbstractAction {

        /**
         * 
         */
        private static final long serialVersionUID = 3795594739034737323L;

        private int viewType;

        ViewTypeAction(int viewType) {
            super(viewTypeActionNames[viewType]);
            this.viewType = viewType;

            String cmd;
            switch (viewType) {
            case VIEWTYPE_LIST:
                cmd = ACTION_VIEW_LIST;
                break;
            case VIEWTYPE_DETAILS:
                cmd = ACTION_VIEW_DETAILS;
                break;
            default:
                cmd = (String) getValue(Action.NAME);
            }
            putValue(Action.ACTION_COMMAND_KEY, cmd);
        }

        public void actionPerformed(ActionEvent e) {
            setViewType(viewType);
        }
    }
}
