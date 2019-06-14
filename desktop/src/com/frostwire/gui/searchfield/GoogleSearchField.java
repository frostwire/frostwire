/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.searchfield;

import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.limewire.util.LCS;
import org.limewire.util.OSUtils;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class GoogleSearchField extends SearchField {
    private static final String SUGGESTIONS_URL = buildSuggestionsUrl();
    private static final int HTTP_QUERY_TIMEOUT = 1000;
    private SuggestionsThread suggestionsThread;

    public GoogleSearchField() {
        this.dict = createDefaultDictionary();
        setPrompt(I18n.tr("Hints by Google"));
        setSearchMode(SearchMode.REGULAR);
    }

    private static String buildSuggestionsUrl() {
        String lang = ApplicationSettings.LANGUAGE.getValue();
        if (StringUtils.isNullOrEmpty(lang)) {
            lang = "en";
        }
        return "https://clients1.google.com/complete/search?client=youtube&q=%s&hl=" + lang + "&gl=us&gs_rn=23&gs_ri=youtube&ds=yt&cp=2&gs_id=8&callback=google.sbox.p50";
    }

    public void autoCompleteInput() {
        String input = getText();
        if (input != null && input.length() > 0) {
            if (suggestionsThread != null) {
                suggestionsThread.cancel();
            }
            if (getAutoComplete()) {
                suggestionsThread = new SuggestionsThread(input, this);
                suggestionsThread.start();
            }
        } else {
            hidePopup();
        }
    }

    @Override
    public void setText(String t) {
        try {
            if (t != null) {
                t = t.replace("<html>", "").replace("</html>", "").replace("<b>", "").replace("</b>", "");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        super.setText(t);
    }

    protected JComponent getPopupComponent() {
        if (entryPanel != null)
            return entryPanel;
        entryPanel = new JPanel(new GridBagLayout());
        entryPanel.setBorder(UIManager.getBorder("List.border"));
        entryPanel.setBackground(UIManager.getColor("List.background"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = GridBagConstraints.REMAINDER;
        entryList = new AutoCompleteList();
        JScrollPane entryScrollPane = new JScrollPane(entryList);
        entryScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        entryScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        entryPanel.add(entryScrollPane, c);
        Font origFont = getFont();
        Font newFont = origFont;
        if (OSUtils.isWindows()) {
            newFont = ThemeMediator.DIALOG_FONT.deriveFont(origFont.getSize2D());
        }
        entryList.setFont(newFont);
        return entryPanel;
    }

    private static final class SuggestionsThread extends Thread {
        private final String constraint;
        private final GoogleSearchField input;
        private boolean cancelled;

        SuggestionsThread(String constraint, GoogleSearchField input) {
            this.constraint = constraint;
            this.input = input;
            this.setName("SuggestionsThread: " + constraint);
            this.setDaemon(true);
        }

        boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            cancelled = true;
        }

        public void run() {
            try {
                String url = String.format(SUGGESTIONS_URL, URLEncoder.encode(constraint, StandardCharsets.UTF_8));
                HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
                String js = httpClient.get(url, HTTP_QUERY_TIMEOUT);
                String json = stripJs(js);
                if (!isCancelled()) {
                    JsonArray arr = new JsonParser().parse(json).getAsJsonArray();
                    final List<String> suggestions = readSuggestions(arr.get(1).getAsJsonArray());
                    GUIMediator.safeInvokeLater(() -> {
                        Iterator<String> it = suggestions.iterator();
                        if (it.hasNext())
                            if (!StringUtils.isNullOrEmpty(input.getText(), true)) {
                                input.showPopup(it);
                            } else
                                input.hidePopup();
                    });
                }
            } catch (Throwable e) {
                // ignore
            }
        }

        private List<String> readSuggestions(JsonArray array) {
            String t = input.getText();
            List<String> suggestions = new ArrayList<>(array.size());
            if (!StringUtils.isNullOrEmpty(t, true)) {
                for (int i = 0; i < array.size(); i++) {
                    try {
                        String s = LCS.lcsHtml(t, array.get(i).getAsJsonArray().get(0).getAsString());
                        suggestions.add(s);
                    } catch (Throwable e) {
                        //e.printStackTrace();
                    }
                }
            }
            return suggestions;
        }

        private String stripJs(String js) {
            js = js.replace("google.sbox.p50 && google.sbox.p50(", "");
            js = js.replace("}])", "}]");
            return js;
        }
    }
}
