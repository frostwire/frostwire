/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, 2013, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.tables;

import java.awt.Color;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class ColoredCellImpl implements ColoredCell, Comparable<Object> {

    private final Object val;
    private final Color col;
    private final Class<?> clazz;

    public ColoredCellImpl(Object dsp, Color cl) {
        this(dsp, cl, dsp == null ? String.class : dsp.getClass());
    }

    public ColoredCellImpl(Object dsp, Color cl, Class<?> clazz) {
        this.val = dsp;
        this.col = cl;
        this.clazz = clazz;
    }

    public Object getValue() {
        return val;
    }

    public Color getColor() {
        return col;
    }

    public Class<?> getCellClass() {
        return clazz;
    }

    public String toString() {
        return val == null ? null : val.toString();
    }

    public int compareTo(Object o) {
        return AbstractTableMediator.compare(val, ((ColoredCellImpl) o).val);
    }
}
