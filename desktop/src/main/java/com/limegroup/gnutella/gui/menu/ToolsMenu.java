/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
     * Creates a new `ToolsMenu`, using the `key`
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
