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

package com.limegroup.gnutella.gui;

import com.frostwire.gui.theme.SkinMultilineToolTipUI;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MultilineToolTip extends JToolTip {
    public MultilineToolTip() {
    }

    public void setTipArray(String[] arr) {
        super.setTipText(join(arr));
    }

    @Override
    public void setTipText(String tipText) {
        // avoid not allowed change
    }

    @Override
    public void updateUI() {
        if (UIManager.getLookAndFeel() instanceof javax.swing.plaf.synth.SynthLookAndFeel) {
            setUI(new SkinMultilineToolTipUI());
        } else {
            setUI(UIManager.getUI(this)); // FlatLaf, Metal, etc.
        }
    }

    private String join(String[] arr) {
        StringBuilder sb = new StringBuilder();
        int size = arr.length;
        if (size - 1 > -1) {
            for (int i = 0; i < size - 1; i++) {
                sb.append(arr[i]);
                sb.append(System.lineSeparator());
            }
            sb.append(arr[size - 1]);
        }
        return sb.toString();
    }
}
