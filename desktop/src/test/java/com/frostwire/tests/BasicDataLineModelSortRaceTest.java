/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The background resort must keep using the column it started with even if the EDT
 * unsorts or re-sorts the table mid-sort (unsort() sets the active column to -1).
 */
class BasicDataLineModelSortRaceTest {

    static Runnable onFirstCompare;

    static final class Line extends AbstractDataLine<String> {
        private static final List<String> COLUMNS = List.of("name");

        public Line() {
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public LimeTableColumn getColumn(int col) {
            return new LimeTableColumn(col, "COL" + col, "col" + col, 50, true, String.class);
        }

        @Override
        public boolean isDynamic(int col) {
            return true;
        }

        @Override
        public boolean isClippable(int col) {
            return false;
        }

        @Override
        public Object getValueAt(int col) {
            COLUMNS.get(col); // mirrors BTDownloadDataLine: a stale -1 throws IndexOutOfBounds
            Runnable hook = onFirstCompare;
            if (hook != null) {
                onFirstCompare = null;
                hook.run();
            }
            return getInitializeObject();
        }

        @Override
        public int getTypeAheadColumn() {
            return 0;
        }

        @Override
        public void update() {
        }
    }

    static final class Model extends BasicDataLineModel<Line, String> {
        Model() {
            super(Line.class);
        }

        void sortNow(int column) {
            _activeColumn = column;
            _ascending = 1;
            doResort();
        }
    }

    @Test
    void unsortDuringResortKeepsTheOriginalColumn() {
        Model model = new Model();
        for (String value : new String[]{"c", "a", "d", "b"}) {
            model.add(value, 0);
        }
        onFirstCompare = model::unsort;

        assertDoesNotThrow(() -> model.sortNow(0));

        List<String> order = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            order.add(model.get(i).getInitializeObject());
        }
        assertEquals(List.of("a", "b", "c", "d"), order);
    }
}
