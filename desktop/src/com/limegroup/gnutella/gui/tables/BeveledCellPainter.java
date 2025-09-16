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

package com.limegroup.gnutella.gui.tables;

import java.awt.*;

public final class BeveledCellPainter {
    private static final Color CELL_UPPER_BORDER_COLOR = Color.WHITE;
    private static final Color CELL_BOTTOM_BORDER_COLOR = new Color(0xe8eaed);

    public static void paintBorder(Graphics g, int width, int height) {
        //and then paint an upper white 1px line and a bottom 1px gray line to give depth effect on each row.
        if (g != null) {
            g.setColor(CELL_UPPER_BORDER_COLOR);
            g.drawLine(0, 0, width, 0);
            g.setColor(CELL_BOTTOM_BORDER_COLOR);
            g.drawLine(0, height - 1, width, height - 1);
        }
    }
}
