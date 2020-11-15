/*
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

package com.limegroup.gnutella.gui.search;

import javax.swing.*;

/**
 * The search result menu.
 */
final class SearchResultMenu {
    private final SearchResultMediator PANEL;

    /**
     * Private constructor to ensure that this class can never be
     * created.
     */
    SearchResultMenu(SearchResultMediator rp) {
        PANEL = rp;
    }

    /**
     * Adds search-result specific items to the JPopupMenu.
     */
    JPopupMenu addToMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines) {
        // Check if there are lines
        if (lines.length == 0) {
            return popupMenu;
        }
        // Now check to see if any of the table lines are different classes
        // In this case we need to show a message that only similar
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i - 1].isSameKindAs(lines[i])) {
                // Bail! Bail!
                // Just pick the first similar ones, since we're
                // lost as to what to do otherwise...
                SearchResultDataLine[] newLines = new SearchResultDataLine[i - 1];
                System.arraycopy(lines, 0, newLines, 0, i - 1);
                lines = newLines;
                break;
            }
        }
        return lines[0].getSearchResult().createMenu(popupMenu, lines, PANEL);
    }
}
