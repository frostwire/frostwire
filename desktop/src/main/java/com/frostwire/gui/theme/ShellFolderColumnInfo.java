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

package com.frostwire.gui.theme;

import javax.swing.*;
import java.util.Comparator;

public class ShellFolderColumnInfo {
    private String title;
    private Integer width;
    private boolean visible;
    /**
     * Allowed values are {@link SwingConstants#LEFT}, {@link SwingConstants#RIGHT}, {@link SwingConstants#LEADING},
     * {@link SwingConstants#TRAILING}, {@link SwingConstants#CENTER}
     */
    private final Integer alignment;
    private Comparator<Object> comparator;
    /**
     * <code>false</code> (default) if the comparator expects folders as arguments,
     * and <code>true</code> if folder's column values. The first option is used default for comparison
     * on Windows and also for separating files from directories when sorting using
     * ShellFolderManager's inner comparator.
     */
    private final boolean compareByColumn;

    ShellFolderColumnInfo(String title, Integer width,
                          Integer alignment, boolean visible,
                          Comparator<Object> comparator,
                          boolean compareByColumn) {
        this.title = title;
        this.width = width;
        this.alignment = alignment;
        this.visible = visible;
        this.comparator = comparator;
        this.compareByColumn = compareByColumn;
    }

    ShellFolderColumnInfo(String title, Integer width,
                          Integer alignment, boolean visible,
                          Comparator<Object> comparator) {
        this(title, width, alignment, visible, comparator, false);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getAlignment() {
        return alignment;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Comparator<Object> getComparator() {
        return comparator;
    }

    public void setComparator(Comparator<Object> comparator) {
        this.comparator = comparator;
    }

    boolean isCompareByColumn() {
        return compareByColumn;
    }
}
