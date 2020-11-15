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

/**
 * Encapsulates access to preferences for a table's column.
 */
public interface ColumnPreferenceHandler {
    /**
     * Reverts this table's header preferences to their default
     * values.
     */
    void revertToDefault();

    /**
     * Determines whether or not the columns are already their default values.
     */
    boolean isDefault();

    /**
     * Sets the headers to the correct widths, depending on
     * the user's preferences for this table.
     */
    void setWidths();

    /**
     * Sets the headers to the correct order, depending on the
     * user's preferences for this table.
     */
    void setOrder();

    /**
     * Sets the headers so that some may be invisible, depending
     * on the user's preferences for this table.
     */
    void setVisibility();

    /**
     * Sets the single SimpleColumnListener callback.
     */
    @SuppressWarnings("unused")
    void setSimpleColumnListener(SimpleColumnListener scl);
}