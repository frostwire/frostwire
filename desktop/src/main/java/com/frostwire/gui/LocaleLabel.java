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
import com.frostwire.util.SafeText;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class LocaleLabel extends JLabel {
    private static final Font SAFE_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);

    public void setText(LocaleString text) {
        Font f = getParent() != null ? getParent().getFont() : SAFE_FONT;
        if (!canDisplaySafely(f, text.getValue())) {
            f = SAFE_FONT;
        }
        changeFont(f);
        setText(text.getValue());
    }

    private boolean canDisplaySafely(Font font, String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        if (font == null) {
            return false;
        }
        try {
            return font.canDisplayUpTo(text) == -1;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void setText(String text) {
        super.setText(SafeText.sanitize(text));
    }

    private void changeFont(Font f) {
        if (f != null && !f.equals(getFont())) {
            setFont(f);
        }
    }

    public static class LocaleString {
        private final String value;
        private final String displayValue;
        private Boolean canDisplay;

        public LocaleString(String value) {
            this.value = value;
            this.displayValue = SafeText.sanitize(value);
            this.canDisplay = OSUtils.isWindows() ? null : Boolean.TRUE;
        }

        String getValue() {
            return displayValue;
        }

        boolean canDisplay() {
            if (canDisplay == null) {
                canDisplay = getDefaultFont().canDisplayUpTo(displayValue) == -1;
            }
            return canDisplay;
        }

        @Override
        public String toString() {
            return displayValue;
        }

        private Font getDefaultFont() {
            return (Font) UIManager.getDefaults().get("defaultFont");
        }
    }
}
