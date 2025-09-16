/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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
