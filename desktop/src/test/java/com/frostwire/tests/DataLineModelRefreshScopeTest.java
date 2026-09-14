/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.tables.IconAndNameHolderImpl;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.SeedsHolder;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import com.limegroup.gnutella.gui.tables.TimeRemainingHolder;
import org.junit.jupiter.api.Test;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataLineModelRefreshScopeTest {

    static final class FakeLine extends AbstractDataLine<String> {
        private String displayed = "";

        public FakeLine() {
        }

        void setDisplayed(String displayed) {
            this.displayed = displayed;
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public LimeTableColumn getColumn(int col) {
            return new LimeTableColumn(col, "COL" + col, "col" + col, 50, true, String.class);
        }

        @Override
        public boolean isDynamic(int col) {
            return false;
        }

        @Override
        public boolean isClippable(int col) {
            return false;
        }

        @Override
        public Object getValueAt(int col) {
            return col == 0 ? displayed : "static";
        }

        @Override
        public int getTypeAheadColumn() {
            return 0;
        }

        @Override
        public void update() {
        }
    }

    static final class FakeModel extends BasicDataLineModel<FakeLine, String> {
        FakeModel() {
            super(FakeLine.class);
        }
    }

    private static FakeModel modelWithRows(int rows, TableModelListener listener) {
        FakeModel model = new FakeModel();
        for (int i = 0; i < rows; i++) {
            model.add("row" + i, i);
            model.get(i).setDisplayed("value" + i);
        }
        model.addTableModelListener(listener);
        return model;
    }

    @Test
    void refresh_staticRows_firesNoEvents() {
        List<TableModelEvent> events = new ArrayList<>();
        FakeModel model = modelWithRows(3, events::add);

        model.refresh();
        assertTrue(events.isEmpty(), "First refresh only stores the baseline, got " + events.size() + " events");

        model.refresh();

        assertTrue(events.isEmpty(), "Static rows must not repaint, got " + events.size() + " events");
    }

    @Test
    void refresh_changedRow_firesSingleRowEvent() {
        List<TableModelEvent> events = new ArrayList<>();
        FakeModel model = modelWithRows(3, events::add);
        model.refresh();
        events.clear();

        model.get(1).setDisplayed("changed");
        model.refresh();

        assertEquals(1, events.size());
        assertEquals(TableModelEvent.UPDATE, events.get(0).getType());
        assertEquals(1, events.get(0).getFirstRow());
        assertEquals(1, events.get(0).getLastRow());
    }

    @Test
    void refresh_contiguousChangedRows_firesOneRangeEvent() {
        List<TableModelEvent> events = new ArrayList<>();
        FakeModel model = modelWithRows(4, events::add);
        model.refresh();
        events.clear();

        model.get(1).setDisplayed("changed1");
        model.get(2).setDisplayed("changed2");
        model.refresh();

        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getFirstRow());
        assertEquals(2, events.get(0).getLastRow());
    }

    @Test
    void refresh_scatteredChangedRows_firesOneEventPerRange() {
        List<TableModelEvent> events = new ArrayList<>();
        FakeModel model = modelWithRows(4, events::add);
        model.refresh();
        events.clear();

        model.get(0).setDisplayed("changed0");
        model.get(3).setDisplayed("changed3");
        model.refresh();

        assertEquals(2, events.size());
        assertEquals(0, events.get(0).getFirstRow());
        assertEquals(0, events.get(0).getLastRow());
        assertEquals(3, events.get(1).getFirstRow());
        assertEquals(3, events.get(1).getLastRow());
    }

    @Test
    void refresh_emptyModel_firesNoEvents() {
        List<TableModelEvent> events = new ArrayList<>();
        FakeModel model = modelWithRows(0, events::add);

        model.refresh();

        assertTrue(events.isEmpty());
    }

    @Test
    void sizeHolder_valueEquality() {
        assertEquals(new SizeHolder(1024), new SizeHolder(1024));
        assertEquals(new SizeHolder(1024).hashCode(), new SizeHolder(1024).hashCode());
        assertNotEquals(new SizeHolder(1024), new SizeHolder(2048));
        assertNotEquals(new SizeHolder(1024), null);
        assertNotEquals(new SizeHolder(1024), "1024");
    }

    @Test
    void timeRemainingHolder_valueEquality() {
        assertEquals(new TimeRemainingHolder(60), new TimeRemainingHolder(60));
        assertEquals(new TimeRemainingHolder(60).hashCode(), new TimeRemainingHolder(60).hashCode());
        assertNotEquals(new TimeRemainingHolder(60), new TimeRemainingHolder(61));
    }

    @Test
    void seedsHolder_valueEquality() {
        assertEquals(new SeedsHolder("3/10"), new SeedsHolder("3/10"));
        assertEquals(new SeedsHolder("3/10").hashCode(), new SeedsHolder("3/10").hashCode());
        assertNotEquals(new SeedsHolder("3/10"), new SeedsHolder("4/10"));
    }

    @Test
    void iconAndNameHolder_valueEquality() {
        assertEquals(new IconAndNameHolderImpl(null, "name"), new IconAndNameHolderImpl(null, "name"));
        assertEquals(new IconAndNameHolderImpl(null, "name").hashCode(),
                new IconAndNameHolderImpl(null, "name").hashCode());
        assertNotEquals(new IconAndNameHolderImpl(null, "name"), new IconAndNameHolderImpl(null, "other"));
    }
}
