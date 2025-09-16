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

package com.frostwire.gui.searchfield;

import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.OSUtils;
import com.frostwire.util.http.HttpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.FileMenuActions;
import com.limegroup.gnutella.gui.search.SearchInformation;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.SearchSettings;
import com.limegroup.gnutella.util.URLDecoder;
import org.limewire.util.LCS;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.event.*;
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
    public static final String CLOUD_SEARCH_FIELD_HINT_TEXT = I18n.tr("Search or enter target URL");

    public GoogleSearchField() {
        this.dict = createDefaultDictionary();
        setPrompt(I18n.tr("Hints by Google"));
        setSearchMode(SearchMode.REGULAR);
        setBackground(UIManager.getColor("TextField.background"));
        initCloudSearchField(this);
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

    private static String lastClipboardSearchQuery = null;

    public static void eraseLastClipboardSearchQuery() {
        lastClipboardSearchQuery = null;
    }

    public static void initCloudSearchField(GoogleSearchField cloudSearchField) {
        cloudSearchField.addActionListener(new GoogleSearchField.SearchListener(cloudSearchField));
        cloudSearchField.setPrompt(CLOUD_SEARCH_FIELD_HINT_TEXT);
        Font origFont = cloudSearchField.getFont();
        Font newFont = origFont.deriveFont(origFont.getSize2D() + 2f);
        cloudSearchField.setFont(newFont);
        cloudSearchField.setMargin(new Insets(0, 2, 0, 0));
        cloudSearchField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (cloudSearchField.getText().equals(CLOUD_SEARCH_FIELD_HINT_TEXT)) {
                    cloudSearchField.setText("");
                }
            }
        });
        cloudSearchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue()) {
                    return;
                }
                Clipboard systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                String s = GUIUtils.extractStringContentFromClipboard(systemClipboard);
                if (s == null || "".equals(s)) {
                    return;
                }
                if (lastClipboardSearchQuery != null && lastClipboardSearchQuery.equals(s)) {
                    return;
                }
                if (s.startsWith("http") || s.startsWith("magnet")) {
                    cloudSearchField.setText(s);
                    lastClipboardSearchQuery = s;
                    cloudSearchField.getActionListeners()[0].actionPerformed(null);
                    cloudSearchField.setText("");
                }
            }
        });
    }


    protected JComponent getPopupComponent() {
        if (entryPanel != null)
            return entryPanel;
        entryPanel = new JPanel(new GridBagLayout());
        entryPanel.setBorder(UIManager.getBorder("List.border"));
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
                    JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
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

    public static class SearchListener implements ActionListener {
        private final GoogleSearchField cloudSearchField;
        public SearchListener(GoogleSearchField searchField) {
            cloudSearchField = searchField;
        }
        public void actionPerformed(ActionEvent e) {
            // Keep the query if there was one before switching to the search tab.
            String query = cloudSearchField.getText();
            String queryTitle = query;
            GUIMediator.instance().setWindow(GUIMediator.Tabs.SEARCH);
            // Start a download from the search box by entering a URL.
            if (FileMenuActions.openMagnetOrTorrent(query)) {
                cloudSearchField.setText("");
                cloudSearchField.hidePopup();
                return;
            }
            if (query.contains("www.frostclick.com/cloudplayer/?type=yt") ||
                    query.contains("frostwire-preview.com/?type=yt")) {
                try {
                    query = query.split("detailsUrl=")[1];
                    query = URLDecoder.decode(query);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }

            final SearchInformation info = SearchInformation.createTitledKeywordSearch(query, null, MediaType.getTorrentMediaType(), queryTitle);
            // If the search worked, store & clear it.
            if (SearchMediator.instance().triggerSearch(info) != 0) {
                if (info.isKeywordSearch()) {
                    cloudSearchField.addToDictionary();
                    // Clear the existing search.
                    cloudSearchField.setText("");
                    cloudSearchField.hidePopup();
                }
            }
        }
    }
}
