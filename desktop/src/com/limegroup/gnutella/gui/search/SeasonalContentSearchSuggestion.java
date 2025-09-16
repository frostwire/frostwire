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

import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.actions.AbstractAction;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.List;

/**
 * Created on 5/5/16.
 *
 * @author gubatron
 * @author aldenml
 */
final class SeasonalContentSearchSuggestion extends AbstractAction {
    private static final Pattern SEA_EPI_PATTERN = Pattern.compile("s(\\d+)e(\\d+)");
    private final String query;

    private SeasonalContentSearchSuggestion(String query) {
        this.query = query;
        setEnabled(true);
        putValue(Action.NAME, MessageFormat.format(SearchMediator.SEARCH_FOR_KEYWORDS, query));
    }

    static void attemptToAddSeasonalContentSearchSuggestion(JMenu menu, JPopupMenu popupMenu, List<String> searchTokens) {
        int i = 0;
        String nextEpisodeSearchToken = null;
        for (String token : searchTokens) {
            final Matcher matcher = SEA_EPI_PATTERN.matcher(token.toLowerCase());
            if (matcher.find()) {
                String season = matcher.group(1);
                String episodeStr = matcher.group(2);
                int nextEpisode = Integer.parseInt(episodeStr) + 1;
                String nextEpisodeStr = (nextEpisode < 10) ?
                        "0" + nextEpisode :
                        String.valueOf(nextEpisode);
                nextEpisodeSearchToken = "s" + season + "e" + nextEpisodeStr;
                break;
            }
            i++;
        }
        if (nextEpisodeSearchToken != null) {
            StringBuilder buffer = new StringBuilder();
            for (int j = 0; j < searchTokens.size(); j++) {
                if (j != i) {
                    buffer.append(searchTokens.get(j));
                    buffer.append(" ");
                } else {
                    buffer.append(nextEpisodeSearchToken);
                    buffer.append(" ");
                }
            }
            String suggestedSearch = buffer.toString().trim();
            if (menu != null) {
                menu.add(new SkinMenuItem(new SeasonalContentSearchSuggestion(suggestedSearch)));
            }
            if (popupMenu != null) {
                popupMenu.add(new SeasonalContentSearchSuggestion(suggestedSearch));
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        GUIMediator.instance().startSearch(query);
    }
}
