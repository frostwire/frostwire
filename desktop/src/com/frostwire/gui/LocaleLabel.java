/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.

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

package com.frostwire.gui;

import com.frostwire.gui.theme.ThemeMediator;
import org.limewire.util.OSUtils;

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
