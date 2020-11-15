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

package com.limegroup.gnutella.gui.actions;

import com.limegroup.gnutella.gui.GUIUtils;

import javax.swing.*;

/**
 * Abstract class that allows the name of the action to have an ampersand to
 * mark the mnemonic of the action in its name.
 * <p>
 * A call to {@link #putValue(String, Object) putValue(Action.Name, "Hello &World")}
 * will set the name of the action to "Hello World" and its menomonic to 'W'.
 */
public abstract class AbstractAction extends javax.swing.AbstractAction {
    @SuppressWarnings("unused")
    public AbstractAction(String name, Icon icon) {
        super(name, icon);
    }

    protected AbstractAction(String name) {
        super(name);
    }

    protected AbstractAction() {
    }

    @Override
    public void putValue(String key, Object newValue) {
        // parse out mnemonic key for action name
        if (key.equals(NAME)) {
            String name = (String) newValue;
            newValue = GUIUtils.stripAmpersand(name);
            int mnemonicKeyCode = GUIUtils.getMnemonicKeyCode(name);
            if (mnemonicKeyCode != -1) {
                super.putValue(MNEMONIC_KEY, mnemonicKeyCode);
            }
        }
        super.putValue(key, newValue);
    }
}
