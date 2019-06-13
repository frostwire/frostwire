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

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;

import java.awt.*;

/**
 * A <code>WholeNumberField</code> with a standard size.
 */
public class SizedWholeNumberField extends WholeNumberField {
    /**
     * Constant for the standard number of columns in the field.
     */
    private static final int STANDARD_COLUMNS = 5;
    /**
     * constant dimension for sizing number fields.
     */
    private final Dimension STANDARD_DIMENSION = new Dimension(5, 28);

    /**
     * Constructs a <code>WholeNumberField</code> with a standard size.
     */
    public SizedWholeNumberField() {
        super(STANDARD_COLUMNS);
        setPreferredSize(STANDARD_DIMENSION);
        setMaximumSize(STANDARD_DIMENSION);
    }

    /**
     * Constructs a <code>WholeNumberField</code> with a standard size.
     * This constructor allows the number of columns to be customized.
     *
     * @param columns the number of columns to use
     */
    public SizedWholeNumberField(int columns) {
        super(columns);
        setPreferredSize(STANDARD_DIMENSION);
        setMaximumSize(STANDARD_DIMENSION);
    }

    public SizedWholeNumberField(int value, int columns, final SizePolicy sizePolicy) {
        super(value, columns);
        GUIUtils.restrictSize(this, sizePolicy);
    }
}
