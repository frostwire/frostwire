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
 * This class creates a <tt>JTextField</tt> with a standardized size.<p>
 * <p>
 * It sets the preffered and maximum size of the field to the standard
 * <tt>Dimension</tt> or sets the preferred and maximum sizes to the
 * <tt>Dimension</tt> argument.
 */
public final class SizedTextField extends LimeTextField {
    /**
     * Constant for the standard height for <tt>JTextField</tt>.
     */
    private static final int STANDARD_HEIGHT = 28;
    /**
     * Constant for the standard <tt>Dimension</tt> for <tt>JTextField</tt>.
     */
    private static final Dimension STANDARD_DIMENSION = new Dimension(500, STANDARD_HEIGHT);

    /**
     * Creates a <tt>JTextField</tt> with a standard size.
     */
    SizedTextField() {
        setPreferredSize(STANDARD_DIMENSION);
        setMaximumSize(STANDARD_DIMENSION);
    }

    /**
     * Creates a <tt>JTextField</tt> with a standard size and with the
     * specified number of columns.
     *
     * @param columns the number of columns to use in the field
     */
    public SizedTextField(final int columns, final SizePolicy sizePolicy) {
        super(columns);
        GUIUtils.restrictSize(this, sizePolicy);
    }
}