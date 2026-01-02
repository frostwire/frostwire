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

package com.frostwire.gui;

import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class LocaleLabel extends JLabel {
    public void setText(LocaleString text) {
        if (OSUtils.isWindows()) {
            Font f;
            if (text.canDisplay()) {
                f = getParent().getFont();
            } else {
                f = ThemeMediator.DIALOG_FONT;
            }
            changeFont(f);
        }
        setText(text.getValue());
    }

    private void changeFont(Font f) {
        if (f != null && !f.equals(getFont())) {
            setFont(f);
        }
    }

    public static class LocaleString {
        private final String value;
        private Boolean canDisplay;

        public LocaleString(String value) {
            this.value = value;
            this.canDisplay = OSUtils.isWindows() ? null : Boolean.TRUE;
        }

        String getValue() {
            return value;
        }

        boolean canDisplay() {
            if (canDisplay == null) {
                canDisplay = getDefaultFont().canDisplayUpTo(value) == -1;
            }
            return canDisplay;
        }

        @Override
        public String toString() {
            return value;
        }

        private Font getDefaultFont() {
            return (Font) UIManager.getDefaults().get("defaultFont");
        }
    }
}
