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

package com.frostwire.gui.theme;

import java.awt.Graphics2D;

import javax.swing.JComponent;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class SkinPopupMenuSeparatorPainter extends AbstractSkinPainter {

    private final State state;

    public SkinPopupMenuSeparatorPainter(State state) {
        this.state = state;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        switch (state) {
        default:
            g.setPaint(ThemeMediator.LIGHT_BORDER_COLOR);
            g.drawLine(0, 0, width, 0);
            break;
        }
    }

    public static enum State {
        Enabled
    }
}
