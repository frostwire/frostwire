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
