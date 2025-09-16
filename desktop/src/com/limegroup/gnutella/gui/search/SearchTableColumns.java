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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;

import javax.swing.*;
import java.util.Date;

/**
 * Simple collection of table columns.
 */
final class SearchTableColumns {
    // It is important that all the columns be non static,
    // so that the multiple search tables all have their own
    // columns.
    static final int ACTIONS_IDX = 0;
    static final int COUNT_IDX = 1;
    static final int TYPE_IDX = 2;
    static final int NAME_IDX = 3;
    static final int SIZE_IDX = 4;
    static final int SOURCE_IDX = 5;
    static final int ADDED_IDX = 6;
    static final int EXTENSION_IDX = 7;
    /**
     * The number of default columns.
     */
    static final int COLUMN_COUNT = 8;
    private final LimeTableColumn ACTIONS_COLUMN = new SearchColumn(ACTIONS_IDX, "RESULT_PANEL_ACTIONS", I18n.tr("Actions"), 63, true, SearchResultActionsHolder.class);
    private final LimeTableColumn COUNT_COLUMN = new SearchColumn(COUNT_IDX, "RESULT_PANEL_COUNT", I18n.tr("Seeds"), 24, true, Integer.class);
    private final LimeTableColumn TYPE_COLUMN = new SearchColumn(TYPE_IDX, "RESULT_PANEL_ICON", I18n.tr("Type"), 18, true, Icon.class);
    private final LimeTableColumn NAME_COLUMN = new SearchColumn(NAME_IDX, "RESULT_PANEL_NAME", I18n.tr("Name"), 272, true, SearchResultNameHolder.class);
    private final LimeTableColumn SIZE_COLUMN = new SearchColumn(SIZE_IDX, "RESULT_PANEL_SIZE", I18n.tr("Size"), 53, true, String.class);
    private final LimeTableColumn SOURCE_COLUMN = new SearchColumn(SOURCE_IDX, "RESULT_PANEL_SOURCE", I18n.tr("Source"), 220, true, SourceHolder.class);
    private final LimeTableColumn ADDED_COLUMN = new SearchColumn(ADDED_IDX, "RESULT_PANEL_ADDED", I18n.tr("Created"), 55, true, Date.class);
    private final LimeTableColumn EXTENSION_COLUMN = new SearchColumn(EXTENSION_IDX, "RESULT_PANEL_EXTENSION", I18n.tr("Extension"), 55, true, String.class);

    /**
     * Gets the column for the specified index.
     */
    LimeTableColumn getColumn(int idx) {
        switch (idx) {
            case ACTIONS_IDX:
                return ACTIONS_COLUMN;
            case COUNT_IDX:
                return COUNT_COLUMN;
            case TYPE_IDX:
                return TYPE_COLUMN;
            case NAME_IDX:
                return NAME_COLUMN;
            case SIZE_IDX:
                return SIZE_COLUMN;
            case SOURCE_IDX:
                return SOURCE_COLUMN;
            case ADDED_IDX:
                return ADDED_COLUMN;
            case EXTENSION_IDX:
                return EXTENSION_COLUMN;
            default:
                return null;
        }
    }
}
