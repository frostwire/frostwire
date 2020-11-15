/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.theme;

import javax.swing.plaf.ColorUIResource;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
final class SkinColors {
    public static final Color PROGRESS_BAR_ENABLED_COLOR1 = new ColorUIResource(158, 200, 224);
    public static final Color PROGRESS_BAR_ENABLED_COLOR2 = new ColorUIResource(124, 175, 204);
    public static final Color[] PROGRESS_BAR_ENABLED_GRADIENT_COLORS = new Color[]{PROGRESS_BAR_ENABLED_COLOR1, PROGRESS_BAR_ENABLED_COLOR2};
    public static final Color PROGRESS_BAR_DISABLED_COLOR1 = new ColorUIResource(210, 212, 214);
    public static final Color PROGRESS_BAR_DISABLED_COLOR2 = new ColorUIResource(180, 180, 180);
    public static final Color[] PROGRESS_BAR_DISABLED_GRADIENT_COLORS = new Color[]{PROGRESS_BAR_DISABLED_COLOR1, PROGRESS_BAR_DISABLED_COLOR2};
    public static final Color PROGRESS_BAR_ENABLED_INDETERMINATE_COLOR1 = new ColorUIResource(158, 200, 224);
    public static final Color PROGRESS_BAR_ENABLED_INDETERMINATE_COLOR2 = new ColorUIResource(124, 175, 204);
    public static final Color[] PROGRESS_BAR_ENABLED_INDERTERMINATE_GRADIENT_COLORS = new Color[]{PROGRESS_BAR_ENABLED_INDETERMINATE_COLOR1, PROGRESS_BAR_ENABLED_INDETERMINATE_COLOR2};
    public static final Color PROGRESS_BAR_DISABLED_INDETERMINATE_COLOR1 = new ColorUIResource(210, 212, 214);
    public static final Color PROGRESS_BAR_DISABLED_INDETERMINATE_COLOR2 = new ColorUIResource(180, 180, 180);
    public static final Color[] PROGRESS_BAR_DISABLED_INDERTERMINATE_GRADIENT_COLORS = new Color[]{PROGRESS_BAR_DISABLED_INDETERMINATE_COLOR1, PROGRESS_BAR_DISABLED_INDETERMINATE_COLOR2};
    public static final Color PROGRESS_BAR_ENABLED_BORDER_COLOR = new ColorUIResource(124, 175, 204);
    public static final Color PROGRESS_BAR_DISABLED_BORDER_COLOR = new ColorUIResource(180, 180, 180);
    // general colors
    public static final Color LIGHT_BACKGROUND_COLOR = new ColorUIResource(246, 246, 246);
    public static final Color GENERAL_BORDER_COLOR = new ColorUIResource(201, 201, 201);
    public static final Color GENERAL_FOCUSED_BORDER_COLOR = new ColorUIResource(100, 100, 100);
    public static final Color GENERAL_DISABLED_BACKGROUND_COLOR = new ColorUIResource(246, 246, 246);
    public static final Color DARK_BOX_BACKGROUND_COLOR = new ColorUIResource(238, 241, 242);
    // scrollbar
    public static final Color SCROLL_BUTTON_ARROW_DISABLED_COLOR = new ColorUIResource(199, 199, 199);
    public static final Color SCROLL_BUTTON_ARROW_ENABLED_COLOR = new ColorUIResource(36, 66, 92);
    public static final Color SCROLL_BUTTON_ARROW_BOX_DISABLED_COLOR1 = new ColorUIResource(245, 246, 246);
    public static final Color SCROLL_BUTTON_ARROW_BOX_DISABLED_COLOR2 = new ColorUIResource(234, 236, 237);
    public static final Color[] SCROLL_BUTTON_ARROW_BOX_DISABLED_COLORS = new Color[]{SCROLL_BUTTON_ARROW_BOX_DISABLED_COLOR1, SCROLL_BUTTON_ARROW_BOX_DISABLED_COLOR2};
    public static final Color SCROLL_BUTTON_ARROW_BOX_ENABLED_COLOR1 = new ColorUIResource(245, 246, 246);
    public static final Color SCROLL_BUTTON_ARROW_BOX_ENABLED_COLOR2 = new ColorUIResource(234, 236, 237);
    public static final Color[] SCROLL_BUTTON_ARROW_BOX_ENABLED_COLORS = new Color[]{SCROLL_BUTTON_ARROW_BOX_ENABLED_COLOR1, SCROLL_BUTTON_ARROW_BOX_ENABLED_COLOR2};
    public static final Color SCROLL_BUTTON_ARROW_BOX_MOUSEOVER_COLOR1 = new ColorUIResource(221, 222, 225);
    public static final Color SCROLL_BUTTON_ARROW_BOX_MOUSEOVER_COLOR2 = new ColorUIResource(201, 203, 205);
    public static final Color[] SCROLL_BUTTON_ARROW_BOX_MOUSEOVER_COLORS = new Color[]{SCROLL_BUTTON_ARROW_BOX_MOUSEOVER_COLOR1, SCROLL_BUTTON_ARROW_BOX_MOUSEOVER_COLOR2};
    public static final Color SCROLL_BUTTON_ARROW_BOX_PRESSED_COLOR1 = new ColorUIResource(221, 222, 225);
    public static final Color SCROLL_BUTTON_ARROW_BOX_PRESSED_COLOR2 = new ColorUIResource(201, 203, 205);
    public static final Color[] SCROLL_BUTTON_ARROW_BOX_PRESSED_COLORS = new Color[]{SCROLL_BUTTON_ARROW_BOX_PRESSED_COLOR1, SCROLL_BUTTON_ARROW_BOX_PRESSED_COLOR2};
    public static final Color SCROLL_BUTTON_ARROW_BOX_BORDER_COLOR = new ColorUIResource(208, 208, 209);
    public static final Color SCROLL_TRACK_DISABLED_COLOR1 = new ColorUIResource(234, 236, 237);
    public static final Color SCROLL_TRACK_DISABLED_COLOR2 = new ColorUIResource(246, 247, 247);
    public static final Color[] SCROLL_TRACK_DISABLED_COLORS = new Color[]{SCROLL_TRACK_DISABLED_COLOR1, SCROLL_TRACK_DISABLED_COLOR2};
    //COLOR1 = left side gradient, COLOR2 = right side gradient.
    public static final Color SCROLL_TRACK_ENABLED_COLOR1 = new ColorUIResource(245, 246, 246);
    public static final Color SCROLL_TRACK_ENABLED_COLOR2 = new ColorUIResource(234, 236, 237);
    public static final Color[] SCROLL_TRACK_ENABLED_COLORS = new Color[]{SCROLL_TRACK_ENABLED_COLOR1, SCROLL_TRACK_ENABLED_COLOR2};
    public static final Color SCROLL_TRACK_BORDER_COLOR = new ColorUIResource(208, 208, 209);
    public static final Color SCROLL_THUMB_ENABLED_COLOR1 = new ColorUIResource(201, 211, 216);
    public static final Color SCROLL_THUMB_ENABLED_COLOR2 = new ColorUIResource(169, 187, 197);
    public static final Color[] SCROLL_THUMB_ENABLED_COLORS = new Color[]{SCROLL_THUMB_ENABLED_COLOR1, SCROLL_THUMB_ENABLED_COLOR2};
    public static final Color SCROLL_THUMB_MOUSEOVER_COLOR1 = new ColorUIResource(183, 195, 201);
    public static final Color SCROLL_THUMB_MOUSEOVER_COLOR2 = new ColorUIResource(157, 173, 182);
    public static final Color[] SCROLL_THUMB_MOUSEOVER_COLORS = new Color[]{SCROLL_THUMB_MOUSEOVER_COLOR1, SCROLL_THUMB_MOUSEOVER_COLOR2};
    public static final Color SCROLL_THUMB_PRESSED_COLOR1 = new ColorUIResource(183, 195, 201);
    public static final Color SCROLL_THUMB_PRESSED_COLOR2 = new ColorUIResource(157, 173, 182);
    public static final Color[] SCROLL_THUMB_PRESSED_COLORS = new Color[]{SCROLL_THUMB_PRESSED_COLOR1, SCROLL_THUMB_PRESSED_COLOR2};
    public static final Color SCROLL_THUMB_BORDER_COLOR = new ColorUIResource(179, 193, 200);
    public static final Color TABLE_HEADER_ENABLED_COLOR1 = new ColorUIResource(240, 241, 242);
    public static final Color TABLE_HEADER_ENABLED_COLOR2 = new ColorUIResource(234, 234, 234);
    public static final Color[] TABLE_HEADER_ENABLED_COLORS = new Color[]{TABLE_HEADER_ENABLED_COLOR1, TABLE_HEADER_ENABLED_COLOR2};
    public static final Color TABLE_HEADER_MOUSEOVER_COLOR1 = new ColorUIResource(240, 241, 242);
    public static final Color TABLE_HEADER_MOUSEOVER_COLOR2 = new ColorUIResource(234, 234, 234);
    public static final Color[] TABLE_HEADER_MOUSEOVER_COLORS = new Color[]{TABLE_HEADER_MOUSEOVER_COLOR1, TABLE_HEADER_MOUSEOVER_COLOR2};
    public static final Color TABLE_HEADER_PRESSED_COLOR1 = new ColorUIResource(216, 216, 216);
    public static final Color TABLE_HEADER_PRESSED_COLOR2 = new ColorUIResource(226, 226, 226);
    public static final Color[] TABLE_HEADER_PRESSED_COLORS = new Color[]{TABLE_HEADER_PRESSED_COLOR1, TABLE_HEADER_PRESSED_COLOR2};
    public static final Color TABLE_HEADER_BORDER_COLOR = new ColorUIResource(201, 201, 201);
    public static final Color TABLE_ALTERNATE_ROW_COLOR = new ColorUIResource(243, 245, 247);
    public static final Color TABLE_SELECTED_BACKGROUND_ROW_COLOR = new ColorUIResource(213, 221, 225);
    public static final Color TABLE_SELECTED_FOREGROUND_ROW_COLOR = new ColorUIResource(Color.BLACK);
    public static final Color APPLICATION_HEADER_COLOR1 = new ColorUIResource(64, 113, 155);
    public static final Color APPLICATION_HEADER_COLOR2 = new ColorUIResource(49, 88, 120);
    public static final Color[] APPLICATION_HEADER_GRADIENT_COLORS = new Color[]{APPLICATION_HEADER_COLOR1, APPLICATION_HEADER_COLOR2};
    public static final Color TEXT_FONT_FOREGROUND_COLOR = new ColorUIResource(55, 69, 82);
    public static final Color TRANSPARENT_COLOR = new ColorUIResource(new Color(255, 255, 255, 0));

    private SkinColors() {
    }
}
