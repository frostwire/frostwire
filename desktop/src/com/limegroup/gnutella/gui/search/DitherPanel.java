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

package com.limegroup.gnutella.gui.search;

import javax.swing.*;
import java.awt.*;

/**
 * Simple extension of JPanel that makes a FlowLayout.LEADING JPanel that
 * has a background image which is painted.
 */
public class DitherPanel extends JPanel {
    private final Ditherer DITHERER;

    /**
     * Creates a FlowLayout.LEADING layout.
     *
     * @param ditherer the <tt>Ditherer</tt> that paints the dithered
     *                 background
     */
    public DitherPanel(Ditherer ditherer) {
        super();
        DITHERER = ditherer;
    }

    /**
     * Does the actual placement of the background image.
     */
    public void paintComponent(java.awt.Graphics g) {
        Dimension size = getSize();
        DITHERER.draw(g, size.height, size.width);
    }
}
