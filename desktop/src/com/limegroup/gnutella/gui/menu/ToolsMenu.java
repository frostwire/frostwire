/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.menu;

import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.gui.updates.UpdateMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.AbstractAction;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Contains all the menu items for the tools menu.
 */
final class ToolsMenu extends AbstractMenu {
    private final UpdateAction updateAction;

    /**
     * Creates a new <tt>ToolsMenu</tt>, using the <tt>key</tt>
     * argument for setting the locale-specific title and
     * accessibility text.
     */
    ToolsMenu() {
        super(I18n.tr("&Tools"));
        this.updateAction = new UpdateAction();
        addMenuItem(new ShowOptionsAction());
        addMenuItem(updateAction);
        addSeparator();
        JMenu switchThemeMenu = new JMenu(I18n.tr("Switch Theme"));
        ButtonGroup themeGroup = new ButtonGroup();
        // Default theme
        JRadioButtonMenuItem defaultItem = new JRadioButtonMenuItem(new AbstractAction(I18n.tr("&Default")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                ThemeMediator.switchTheme(ThemeMediator.ThemeEnum.DEFAULT);
            }
        });
        defaultItem.setSelected(ThemeMediator.isDefaultThemeOn());
        themeGroup.add(defaultItem);
        switchThemeMenu.add(defaultItem);
        // Light flat theme
        JRadioButtonMenuItem lightItem = new JRadioButtonMenuItem(
                new AbstractAction(I18n.tr("&Light Flat (beta)")) {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ThemeMediator.switchTheme(ThemeMediator.ThemeEnum.LIGHT_FLAT_LAF);
                    }
                }
        );
        lightItem.setSelected(ThemeMediator.isLightLafThemeOn());
        themeGroup.add(lightItem);
        switchThemeMenu.add(lightItem);
        // Dark theme
        JRadioButtonMenuItem darkItem = new JRadioButtonMenuItem(new AbstractAction(I18n.tr("&Dark Flat (beta)")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                ThemeMediator.switchTheme(ThemeMediator.ThemeEnum.DARK_FLAT_LAF);
            }
        });
        darkItem.setSelected(ThemeMediator.isDarkLafThemeOn());
        themeGroup.add(darkItem);
        switchThemeMenu.add(darkItem);
        MENU.add(switchThemeMenu);
    }

    @Override
    protected void refresh() {
        updateAction.refresh();
    }

    private static class ShowOptionsAction extends AbstractAction {
        ShowOptionsAction() {
            super(I18n.tr("&Options"));
            putValue(LONG_DESCRIPTION, I18n.tr("Display the Options Screen"));
        }

        public void actionPerformed(ActionEvent e) {
            GUIMediator.instance().setOptionsVisible(true);
        }
    }

    private static class UpdateAction extends AbstractAction {
        UpdateAction() {
            super(I18n.tr("&Update FrostWire"));
            putValue(LONG_DESCRIPTION, I18n.tr("Update FrostWire to the latest version"));
        }

        public void actionPerformed(ActionEvent e) {
            if (UpdateMediator.instance().isUpdateDownloaded()) {
                UpdateMediator.instance().startUpdate();
            } else {
                UpdateMediator.instance().checkForUpdate();
            }
        }

        void refresh() {
            String text;
            boolean enabled = true;
            if (UpdateMediator.instance().isUpdated()) {
                text = I18n.tr("You are up to date with FrostWire") + " v." + UpdateMediator.instance().getLatestVersion();
            } else if (UpdateMediator.instance().isUpdateDownloading()) {
                text = I18n.tr("Downloading update...") + "(" + UpdateMediator.instance().getUpdateDownloadProgress() + "%)";
                enabled = false;
            } else if (UpdateMediator.instance().isUpdateDownloaded()) {
                text = I18n.tr("Install update") + " v." + UpdateMediator.instance().getLatestVersion();
            } else {
                text = I18n.tr("Check for update");
            }
            putValue(NAME, text);
            putValue(LONG_DESCRIPTION, text);
            setEnabled(enabled);
        }
    }
}
