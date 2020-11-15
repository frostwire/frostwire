/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinTitledBorder extends TitledBorder {
    private static Font TITLE_FONT;
    private static Border BORDER;
    private final Point textLoc = new Point();

    public SkinTitledBorder(String title) {
        super(title);
        setTitlePosition(BELOW_TOP);
    }

    static boolean isLeftToRight(Component c) {
        return c.getComponentOrientation().isLeftToRight();
    }

    private static boolean computeIntersection(Rectangle dest, int rx, int ry, int rw, int rh) {
        int x1 = Math.max(rx, dest.x);
        int x2 = Math.min(rx + rw, dest.x + dest.width);
        int y1 = Math.max(ry, dest.y);
        int y2 = Math.min(ry + rh, dest.y + dest.height);
        dest.x = x1;
        dest.y = y1;
        dest.width = x2 - x1;
        dest.height = y2 - y1;
        return dest.width > 0 && dest.height > 0;
    }

    @Override
    public Font getTitleFont() {
        if (TITLE_FONT == null) {
            Font f = super.getTitleFont();
            if (f != null) {
                TITLE_FONT = new Font(f.getName(), Font.BOLD, f.getSize() - 1);
            }
        }
        return TITLE_FONT;
    }

    @Override
    public Border getBorder() {
        if (BORDER == null) {
            BORDER = new SkinEtchedBorder(SkinColors.GENERAL_BORDER_COLOR);
        }
        return BORDER;
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Border border = getBorder();
        if (getTitle() == null || getTitle().equals("")) {
            if (border != null) {
                border.paintBorder(c, g, x, y, width, height);
            }
            return;
        }
        Rectangle grooveRect = new Rectangle(x, y, width, height);
        Font font = g.getFont();
        Color color = g.getColor();
        g.setFont(getFont(c));
        FontMetrics fm = g.getFontMetrics();
        int fontHeight = fm.getHeight();
        int descent = fm.getDescent();
        int ascent = fm.getAscent();
        int diff;
        int stringWidth = fm.stringWidth(getTitle());
        Insets insets;
        if (border != null) {
            insets = border.getBorderInsets(c);
        } else {
            insets = new Insets(0, 0, 0, 0);
        }
        int titlePos = getTitlePosition();
        switch (titlePos) {
            case ABOVE_TOP:
                diff = ascent + descent + (Math.max(EDGE_SPACING, TEXT_SPACING * 2) - EDGE_SPACING);
                grooveRect.y += diff;
                grooveRect.height -= diff;
                textLoc.y = grooveRect.y - (descent + TEXT_SPACING);
                break;
            case TOP:
            case DEFAULT_POSITION:
                diff = Math.max(0, ((ascent / 2) + TEXT_SPACING) - EDGE_SPACING);
                grooveRect.y += diff;
                grooveRect.height -= diff;
                textLoc.y = (grooveRect.y - descent) + (insets.top + ascent + descent) / 2;
                break;
            case BELOW_TOP:
                textLoc.y = grooveRect.y + insets.top + ascent + TEXT_SPACING;
                break;
            case ABOVE_BOTTOM:
                textLoc.y = (grooveRect.y + grooveRect.height) - (insets.bottom + descent + TEXT_SPACING);
                break;
            case BOTTOM:
                grooveRect.height -= fontHeight / 2;
                textLoc.y = ((grooveRect.y + grooveRect.height) - descent) + ((ascent + descent) - insets.bottom) / 2;
                break;
            case BELOW_BOTTOM:
                grooveRect.height -= fontHeight;
                textLoc.y = grooveRect.y + grooveRect.height + ascent + TEXT_SPACING;
                break;
        }
        int justification = getTitleJustification();
        if (isLeftToRight(c)) {
            if (justification == LEADING || justification == DEFAULT_JUSTIFICATION) {
                justification = LEFT;
            } else if (justification == TRAILING) {
                justification = RIGHT;
            }
        } else {
            if (justification == LEADING || justification == DEFAULT_JUSTIFICATION) {
                justification = RIGHT;
            } else if (justification == TRAILING) {
                justification = LEFT;
            }
        }
        switch (justification) {
            case LEFT:
                textLoc.x = grooveRect.x + TEXT_INSET_H + insets.left;
                break;
            case RIGHT:
                textLoc.x = (grooveRect.x + grooveRect.width) - (stringWidth + TEXT_INSET_H + insets.right);
                break;
            case CENTER:
                textLoc.x = grooveRect.x + ((grooveRect.width - stringWidth) / 2);
                break;
        }
        // If title is positioned in middle of border AND its fontsize
        // is greater than the border's thickness, we'll need to paint
        // the border in sections to leave space for the component's background
        // to show through the title.
        //
        if (border != null) {
            if (((titlePos == TOP || titlePos == DEFAULT_POSITION) && (grooveRect.y > textLoc.y - ascent)) || (titlePos == BOTTOM && (grooveRect.y + grooveRect.height < textLoc.y + descent))) {
                Rectangle clipRect = new Rectangle();
                // save original clip
                Rectangle saveClip = g.getClipBounds();
                // paint strip left of text
                clipRect.setBounds(saveClip);
                if (computeIntersection(clipRect, x, y, textLoc.x - 1 - x, height)) {
                    g.setClip(clipRect);
                    border.paintBorder(c, g, grooveRect.x, grooveRect.y, grooveRect.width, grooveRect.height);
                }
                // paint strip right of text
                clipRect.setBounds(saveClip);
                if (computeIntersection(clipRect, textLoc.x + stringWidth + 1, y, x + width - (textLoc.x + stringWidth + 1), height)) {
                    g.setClip(clipRect);
                    border.paintBorder(c, g, grooveRect.x, grooveRect.y, grooveRect.width, grooveRect.height);
                }
                if (titlePos == TOP || titlePos == DEFAULT_POSITION) {
                    // paint strip below text
                    clipRect.setBounds(saveClip);
                    if (computeIntersection(clipRect, textLoc.x - 1, textLoc.y + descent, stringWidth + 2, y + height - textLoc.y - descent)) {
                        g.setClip(clipRect);
                        border.paintBorder(c, g, grooveRect.x, grooveRect.y, grooveRect.width, grooveRect.height);
                    }
                } else { // titlePos == BOTTOM
                    // paint strip above text
                    clipRect.setBounds(saveClip);
                    if (computeIntersection(clipRect, textLoc.x - 1, y, stringWidth + 2, textLoc.y - ascent - y)) {
                        g.setClip(clipRect);
                        border.paintBorder(c, g, grooveRect.x, grooveRect.y, grooveRect.width, grooveRect.height);
                    }
                }
                // restore clip
                g.setClip(saveClip);
            } else {
                border.paintBorder(c, g, grooveRect.x, grooveRect.y, grooveRect.width, grooveRect.height);
            }
        }
        g.setColor(getTitleColor());
        g.drawString(getTitle(), textLoc.x, textLoc.y);
        g.setFont(font);
        g.setColor(color);
    }
}
