/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.menu;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LanguageWindow;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.ToggleSettingAction;
import com.limegroup.gnutella.settings.UISettings;
import org.limewire.setting.BooleanSetting;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static com.limegroup.gnutella.settings.UISettings.UI_SEARCH_TRANSFERS_SPLIT_VIEW;

/**
 * This class manages the "view" menu that allows the user to dynamically select
 * which tabs should be viewable at runtime & themes to use.
 */
final class ViewMenu extends AbstractMenu {
    ViewMenu() {
        super(I18n.tr("&View"));
        addToggleMenuItem(new SearchTransfersSplitTabsAction(UI_SEARCH_TRANSFERS_SPLIT_VIEW, I18n.tr("&Search/Transfers split screen")));
        addToggleMenuItem(new ToggleIconSettingAction(UISettings.SMALL_ICONS, I18n.tr("Use &Small Icons"), I18n.tr("Use Small Icons")));
        addToggleMenuItem(new ToggleIconSettingAction(UISettings.TEXT_WITH_ICONS, I18n.tr("Show Icon &Text"), I18n.tr("Show Text Below Icons")));
        MENU.addSeparator();
        addMenuItem(new ChangeFontSizeAction(1, I18n.tr("&Increase Font Size"), I18n.tr("Increases the Table Font Size")), KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, (OSUtils.isMacOSX() ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)));
        addMenuItem(new ChangeFontSizeAction(-1, I18n.tr("&Decrease Font Size"), I18n.tr("Decreases the Table Font Size")), KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, (OSUtils.isMacOSX() ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)));
        MENU.addSeparator();
        addMenuItem(new ShowLanguageWindowAction());
    }

    private static class ShowLanguageWindowAction extends AbstractAction {
        ShowLanguageWindowAction() {
            super(I18n.tr("C&hange Language"));
            putValue(LONG_DESCRIPTION, I18n.tr("Select your Language Prefereces"));
        }

        public void actionPerformed(ActionEvent e) {
            LanguageWindow lw = new LanguageWindow();
            GUIUtils.centerOnScreen(lw);
            lw.setVisible(true);
        }
    }

    private static class ToggleIconSettingAction extends ToggleSettingAction {
        ToggleIconSettingAction(BooleanSetting setting, String name, String description) {
            super(setting, name, description);
        }

        public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            GUIMediator.instance().buttonViewChanged();
        }
    }

    private static class ChangeFontSizeAction extends AbstractAction {
        private final int increment;

        ChangeFontSizeAction(int inc, String name, String description) {
            super(name);
            putValue(LONG_DESCRIPTION, description);
            increment = inc;
        }

        public void actionPerformed(ActionEvent e) {
            ThemeMediator.modifyTablesFont(increment);
        }
    }

    private static class SearchTransfersSplitTabsAction extends ToggleSettingAction {
        SearchTransfersSplitTabsAction(BooleanSetting setting, String name) {
            super(setting, name);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            GUIMediator.showMessage(I18n.tr("This preference will take effect next time you restart FrostWire"));
        }
    }
}