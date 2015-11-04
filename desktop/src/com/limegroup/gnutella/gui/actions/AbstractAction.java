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

import javax.swing.Icon;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;

/**
 * Abstract class that allows the name of the action to have an ampersand to 
 * mark the mnemonic of the action in its name.
 * 
 * A call to {@link #putValue(String, Object) putValue(Action.Name, "Hello &World")}
 * will set the name of the action to "Hello World" and its menomonic to 'W'.
 * 
 * 
 */
public abstract class AbstractAction extends javax.swing.AbstractAction {

    /**
     * 
     */
    private static final long serialVersionUID = 5133426772218480351L;

    public AbstractAction(String name, Icon icon) {
        super(name, icon);
    }

    public AbstractAction(String name) {
        super(name);
    }
    
    public AbstractAction() {
    }
    
    @Override
    public void putValue(String key, Object newValue) {
        // parse out mnemonic key for action name
        if (key.equals(NAME)) {
            String name = (String)newValue;
            newValue = GUIUtils.stripAmpersand(name);
            int mnemonicKeyCode = GUIUtils.getMnemonicKeyCode(name);
            if (mnemonicKeyCode != -1) { 
            	super.putValue(MNEMONIC_KEY, mnemonicKeyCode);
            }
        }
        super.putValue(key, newValue);
    }

    /**
     * Swing thread-safe way to enable/disable the action from any thread. 
     */
    public void setEnabledLater(final boolean enabled) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                setEnabled(enabled);
            }
        });
    }
}
