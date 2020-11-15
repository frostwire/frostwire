/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Locale;

public class FilePaneEx extends FilePane {
    private static final int VIEWTYPE_COUNT = 2;
    private SkinPopupMenu contextMenu;
    private SkinMenu viewMenu;
    private String viewMenuLabelText;
    private boolean listViewWindowsStyle;
    private String[] viewTypeActionNames;

    @SuppressWarnings("unused")
    FilePaneEx(FileChooserUIAccessor fileChooserUIAccessor) {
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
            for (Component comp : comps) {
                if (comp instanceof SkinRadioButtonMenuItem) {
                    SkinRadioButtonMenuItem mi = (SkinRadioButtonMenuItem) comp;
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
        private final int viewType;

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
