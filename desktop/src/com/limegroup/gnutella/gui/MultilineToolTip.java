/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
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
        setUI(new SkinMultilineToolTipUI());
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
