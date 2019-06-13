/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
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

package com.limegroup.gnutella.gui.tables;

import javax.swing.table.TableModel;
import java.util.Comparator;

/**
 * Interface for the model of a DataLineTable
 *
 * @author Sam Berlin, gubatron
 */
public interface DataLineModel<T extends DataLine<E>, E> extends Comparator<T>, TableModel {
    /**
     * Whether or not the underlying data is sorted.
     */
    boolean isSorted();

    /**
     * Returns whether or not the underlying data is sorted ascending.
     */
    boolean isSortAscending();

    /**
     * Returns the column by which the underlying data is sorted ascending.
     */
    int getSortColumn();

    /**
     * Sort the underlying data by the column.
     */
    void sort(int col);

    /**
     * Whether or not the underlying data needs to be resorted.
     */
    boolean needsResort();

    /**
     * Resorts the underlying data.
     */
    void resort();

    /**
     * Clear the table of all data.
     */
    void clear();

    /**
     * Refresh the data's info.
     */
    Object refresh();

    /**
     * Update a specific DataLine.
     * The dataline updated is one that was initialized by Object o.
     * Should return the row of the DataLine updated.
     */
    int update(E o);

    /**
     * Add a new DataLine to the info, initialized by o.
     * Return the row it was added at.
     */
    int add(E o);

    /**
     * Adds a new DataLine to the info, initialized by o.
     * Added to whatever row will keep the DataLine sorted.
     * Return the row it was added at.
     */
    int addSorted(E o);

    /**
     * Adds a new DataLine to the info.
     * Return the row it was added at.
     */
    @SuppressWarnings("unused")
    int add(T dl);

    /**
     * Adds a new DataLine to the model in whatever row will keep
     * the DataLine sorted.
     */
    @SuppressWarnings("unused")
    void addSorted(T dl);

    /**
     * Add a new DataLine to the info, at a specific row initialized by o.
     * Return the row it was added at.
     */
    int add(E o, int row);

    /**
     * Adds a new DataLine to the info at a specific row.
     * Return the row it was added at.
     */
    @SuppressWarnings("unused")
    int add(T dl, int row);

    /**
     * Get the DataLine associated with the row.
     */
    T get(int row);

    /**
     * Gets the DataLine that was initialized by Object o.
     */
    @SuppressWarnings("unused")
    T get(E o);

    /**
     * Gets the first DataLine that has Object o in column col.
     */
    @SuppressWarnings("unused")
    T get(Object o, int col);

    /**
     * Remove a row from the data.
     */
    void remove(int row);

    /**
     * Remove the row associated with the DataLine 'line'.
     */
    @SuppressWarnings("unused")
    void remove(T line);

    /**
     * Remove the row that was initialized by Object 'o'.
     */
    @SuppressWarnings("unused")
    void remove(Object o);

    /**
     * Determine if the list contains Object o in column col.
     */
    @SuppressWarnings("unused")
    boolean contains(Object o, int col);

    /**
     * Determine if the list contains a row that was initialized by Object o.
     */
    @SuppressWarnings("unused")
    boolean contains(Object o);

    /**
     * Get the row of the row that contains Object o in column col.
     */
    @SuppressWarnings("unused")
    int getRow(Object o, int col);

    /**
     * Get the index of the DataLine that was initialized by Object o.
     */
    int getRow(E o);

    /**
     * Get the index of this DataLine.
     */
    int getRow(T dl);

    /**
     * Gets the tooltip for a specific row.
     */
    String[] getToolTipArray(int row, int col);

    /**
     * Determines if a tooltip must show, regardless of the table settings.
     */
    boolean isTooltipRequired(int row, int col);

    /**
     * Determines if the specified column can be clipped.
     * This is generally true for all text columns.
     */
    boolean isClippable(int col);

    /**
     * Gets the LimeTableColumn for this column.
     * A LimeTableColumn encapsulates access to the columnId,
     * columnName, etc...
     */
    LimeTableColumn getTableColumn(int col);

    /**
     * Gets the id of the specified column.
     */
    Object getColumnId(int col);

    /**
     * Gets the 'type ahead' column.
     */
    int getTypeAheadColumn();
}
