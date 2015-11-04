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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import org.limewire.setting.BooleanSetting;
import org.limewire.util.OSUtils;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LanguageWindow;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.ToggleSettingAction;
import com.limegroup.gnutella.settings.UISettings;

/**
 * This class manages the "view" menu that allows the user to dynamically select
 * which tabs should be viewable at runtime & themes to use.
 */
final class ViewMenu extends AbstractMenu {

    ViewMenu(final String key) {
        super(I18n.tr("&View"));

        ToggleSettingAction toggleAction = new ToggleIconSettingAction(UISettings.SMALL_ICONS, I18n.tr("Use &Small Icons"), I18n.tr("Use Small Icons"));
        addToggleMenuItem(toggleAction);

        toggleAction = new ToggleIconSettingAction(UISettings.TEXT_WITH_ICONS, I18n.tr("Show Icon &Text"), I18n.tr("Show Text Below Icons"));
        addToggleMenuItem(toggleAction);

        MENU.addSeparator();

        addMenuItem(new ChangeFontSizeAction(1, I18n.tr("&Increase Font Size"), I18n.tr("Increases the Table Font Size")),KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, (OSUtils.isMacOSX() ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)));

        addMenuItem(new ChangeFontSizeAction(-1, I18n.tr("&Decrease Font Size"), I18n.tr("Decreases the Table Font Size")),KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, (OSUtils.isMacOSX() ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)));

        addMenuItem(new ChangeFontSizeAction(0, I18n.tr("&Reset Font Size"), I18n.tr("Reset the Table Font Size")),KeyStroke.getKeyStroke(KeyEvent.VK_0, (OSUtils.isMacOSX() ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)));

        MENU.addSeparator();

        addMenuItem(new ShowLanguageWindowAction());
    }

    private static class ShowLanguageWindowAction extends AbstractAction {

        public ShowLanguageWindowAction() {
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

        public ToggleIconSettingAction(BooleanSetting setting, String name, String description) {
            super(setting, name, description);
        }

        public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            GUIMediator.instance().buttonViewChanged();
        }
    }

    private static class ChangeFontSizeAction extends AbstractAction {

        private final int increment;

        public ChangeFontSizeAction(int inc, String name, String description) {
            super(name);
            putValue(LONG_DESCRIPTION, description);
            increment = inc;
        }

        public void actionPerformed(ActionEvent e) {
            ThemeMediator.modifyTablesFont(increment);
        }
    }
}