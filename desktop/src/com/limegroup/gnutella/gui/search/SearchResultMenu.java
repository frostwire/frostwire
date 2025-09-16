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
