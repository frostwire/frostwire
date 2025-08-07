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
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.iTunesMediator;

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
        if (OSUtils.isMacOSX() || OSUtils.isWindows()) {
            addMenuItem(new RebuildiTunesPlaylist());
        }
        addMenuItem(new ShowOptionsAction());
        addMenuItem(updateAction);
        addSeparator();
        JMenu switchThemeMenu = new JMenu(I18n.tr("Switch Theme"));
        ButtonGroup themeGroup = new ButtonGroup();
        // Default theme
        JRadioButtonMenuItem defaultItem = new JRadioButtonMenuItem(new AbstractAction(I18n.tr("&Default")) {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                ThemeMediator.switchTheme(ThemeMediator.ThemeEnum.DEFAULT);
            }
        });
        defaultItem.setSelected(ThemeMediator.isDefaultThemeOn());
        themeGroup.add(defaultItem);
        switchThemeMenu.add(defaultItem);
        // (New) Default theme
        JRadioButtonMenuItem lightItem = new JRadioButtonMenuItem(
                new AbstractAction(I18n.tr("&Light (beta)")) {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ThemeMediator.switchTheme(ThemeMediator.ThemeEnum.DEFAULT);
                    }
                }
        );
        lightItem.setSelected(ThemeMediator.isLightLafThemeOn());
        themeGroup.add(lightItem);
        switchThemeMenu.add(lightItem);
        // Dark theme
        JRadioButtonMenuItem darkItem = new JRadioButtonMenuItem(new AbstractAction(I18n.tr("&Dark (beta)")) {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                ThemeMediator.switchTheme(ThemeMediator.ThemeEnum.DARK);
            }
        });
        darkItem.setSelected(ThemeMediator.isDarkThemeOn());
        themeGroup.add(darkItem);
        switchThemeMenu.add(darkItem);
        MENU.add(switchThemeMenu);
    }

    @Override
    protected void refresh() {
        updateAction.refresh();
    }

    private static class RebuildiTunesPlaylist extends AbstractAction {
        private static final long serialVersionUID = 8348355619323878579L;

        private static final String actionTitle = OSUtils.isMacOSCatalina105OrNewer() ?
                I18n.tr("Rebuild Apple Music \"FrostWire\" Playlist") :
                I18n.tr("Rebuild iTunes \"FrostWire\" Playlist");
        private static final String description = OSUtils.isMacOSCatalina105OrNewer() ?
                I18n.tr("Deletes and re-builds the \"FrostWire\" playlist on Apple Music with all the audio files found on your Torrent Data Folder.") :
                I18n.tr("Deletes and re-builds the \"FrostWire\" playlist on iTunes with all the audio files found on your Torrent Data Folder.");

        RebuildiTunesPlaylist() {
            super(actionTitle);
            putValue(LONG_DESCRIPTION, description);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String yesNoMessage = OSUtils.isMacOSCatalina105OrNewer() ?
                    I18n.tr(
                            "This will remove your \"FrostWire\" playlist in Apple Music and replace\n"
                                    + "it with one containing all the Apple Music compatible files in your \n"
                                    + "Frostwire \"Torrent Data Folder\"\n\n"
                                    + "Please note that it will add the files to the Apple Music library as well\n"
                                    + "and this could result in duplicate files on your Apple Music library\n\n"
                                    + "Are you sure you want to continue?") :
                    I18n.tr(
                            "This will remove your \"FrostWire\" playlist in iTunes and replace\n"
                                    + "it with one containing all the iTunes compatible files in your \n"
                                    + "Frostwire \"Torrent Data Folder\"\n\n"
                                    + "Please note that it will add the files to the iTunes library as well\n"
                                    + "and this could result in duplicate files on your iTunes library\n\n"
                                    + "Are you sure you want to continue?");
            DialogOption result = GUIMediator.showYesNoMessage(yesNoMessage,
                    I18n.tr("Warning"), JOptionPane.WARNING_MESSAGE);
            if (result == DialogOption.YES) {
                iTunesMediator.instance().resetFrostWirePlaylist();
            }
        }
    }

    private static class ShowOptionsAction extends AbstractAction {
        private static final long serialVersionUID = 6187597973189408647L;

        ShowOptionsAction() {
            super(I18n.tr("&Options"));
            putValue(LONG_DESCRIPTION, I18n.tr("Display the Options Screen"));
        }

        public void actionPerformed(ActionEvent e) {
            GUIMediator.instance().setOptionsVisible(true);
        }
    }

    private static class UpdateAction extends AbstractAction {
        private static final long serialVersionUID = 2915214339056016808L;

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
