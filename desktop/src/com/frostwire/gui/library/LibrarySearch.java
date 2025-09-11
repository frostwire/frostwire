/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.library;

import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.gui.searchfield.JXSearchField.SearchMode;
import com.frostwire.gui.searchfield.SearchField;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchInformation;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.LibrarySettings;
import org.limewire.util.FileUtils;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.FileFilter;
import java.io.Serial;
import java.text.Normalizer;
import java.util.List;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class LibrarySearch extends JPanel {
    private JLabel statusLabel;
    private SearchField searchField;
    private SearchRunnable currentSearchRunnable;
    private int resultsCount;
    private String status;

    LibrarySearch() {
        setupUI();
    }

    public void searchFor(final String query, final boolean displayTextOnSearchBox) {
        GUIMediator.safeInvokeLater(() -> {
            GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
            LibraryMediator.instance().getLibraryExplorer().selectFinishedDownloads();
            if (searchField != null) {
                SearchLibraryAction searchAction = new SearchLibraryAction();
                if (displayTextOnSearchBox) {
                    if (query.length() < 50) {
                        searchField.setText(query);
                    } else {
                        searchField.setText(query.substring(0, 49));
                    }
                    searchAction.actionPerformed(null);
                } else {
                    searchAction.actionPerformed(null, query);
                }
            }
        });
    }

    void addResults(int n) {
        if (n < 0) {
            return;
        }
        resultsCount += n;
        setStatus(resultsCount + " " + I18n.tr("search results"));
    }

    public void clear() {
        setStatus("");
        searchField.setText("");
        resultsCount = 0;
    }

    void pushStatus(final String newStatus) {
        GUIMediator.safeInvokeLater(() -> statusLabel.setText(newStatus));
    }

    void revertStatus() {
        GUIMediator.safeInvokeLater(() -> statusLabel.setText(status));
    }

    public void setStatus(String status) {
        this.status = status;
        statusLabel.setText(status);
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setMinimumSize(new Dimension(200, 20));
        setPreferredSize(new Dimension(200, 20));
        statusLabel = new JLabel();
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 0));
        add(statusLabel, BorderLayout.CENTER);
        searchField = new SearchField();
        searchField.setSearchMode(SearchMode.INSTANT);
        searchField.setInstantSearchDelay(50);
        searchField.setPrompt(I18n.tr("Search in Library"));
        Font origFont = searchField.getFont();
        Font newFont = origFont.deriveFont(origFont.getSize2D() + 2f);
        searchField.setFont(newFont);
        searchField.addActionListener(new ActionListener() {
            private final SearchLibraryAction a = new SearchLibraryAction();

            @Override
            public void actionPerformed(ActionEvent e) {
                if (searchField.getText().length() == 0) {
                    a.perform(".");
                } else {
                    a.actionPerformed(null);
                }
            }
        });
        searchField.addFocusListener(new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
                // TODO Auto-generated method stub
            }

            @Override
            public void focusGained(FocusEvent e) {
                //if there's nothing selected for search, select Audio directory holder.
                if (LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder() == null) {
                    LibraryMediator.instance().getLibraryExplorer().selectAudio();
                }
            }
        });
    }

    public SearchField getSearchField() {
        return searchField;
    }

    void setSearchPrompt(String string) {
        searchField.setPrompt(string);
    }

    private static final class SearchFileFilter implements FileFilter {
        private final String[] _tokens;

        SearchFileFilter(String query) {
            _tokens = StringUtils.removeDoubleSpaces(normalize(query)).split(" ");
        }

        public boolean accept(File pathname) {
            return accept(pathname, true);
        }

        boolean accept(File pathname, boolean includeAllDirectories) {
            if (!pathname.exists()) {
                return false;
            }
            if (pathname.isDirectory() && includeAllDirectories) {
                return true;
            }
            String name = normalize(pathname.getAbsolutePath());
            for (String token : _tokens) {
                if (!name.contains(token)) {
                    return false;
                }
            }
            return true;
        }

        private String normalize(String token) {
            String norm = Normalizer.normalize(token, Normalizer.Form.NFKD);
            norm = norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            norm = norm.toLowerCase(Locale.US);
            return norm;
        }
    }

    private class SearchLibraryAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -2182314529781104010L;

        SearchLibraryAction() {
            putValue(Action.NAME, I18n.tr("Search"));
        }

        boolean validate(SearchInformation info) {
            int i = SearchMediator.validateInfo(info);
            return i != SearchMediator.QUERY_EMPTY;
        }

        void perform(String query) {
            if (query.length() == 0) {
                searchField.getToolkit().beep();
                return;
            }
            final SearchInformation info = SearchInformation.createKeywordSearch(query, null, MediaType.getAnyTypeMediaType());
            if (!validate(info)) {
                return;
            }
            searchField.addToDictionary();
            //cancel previous search if any
            if (currentSearchRunnable != null) {
                currentSearchRunnable.cancel();
            }
            DirectoryHolder directoryHolder = LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder();
            if (directoryHolder != null) {
                currentSearchRunnable = new SearchFilesRunnable(query);
                BackgroundQueuedExecutorService.schedule(currentSearchRunnable);
            }
        }

        @SuppressWarnings("unused")
        public void actionPerformed(ActionEvent e, String query) {
            perform(query);
        }

        public void actionPerformed(ActionEvent e) {
            actionPerformed(e, searchField.getText().trim());
        }
    }

    private abstract static class SearchRunnable implements Runnable {
        boolean canceled;

        void cancel() {
            canceled = true;
        }
    }

    private final class SearchFilesRunnable extends SearchRunnable {
        private final String _query;
        private final DirectoryHolder directoryHolder;

        SearchFilesRunnable(String query) {
            _query = query;
            directoryHolder = LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder();
            // weird case
            canceled = directoryHolder == null;
        }

        public void run() {
            try {
                if (canceled) {
                    return;
                }
                // special case for Finished Downloads
                if (_query.equals(".") && directoryHolder instanceof SavedFilesDirectoryHolder) {
                    GUIMediator.safeInvokeLater(() -> {
                        LibraryMediator.instance().updateTableFiles(directoryHolder);
                        setStatus("");
                        resultsCount = 0;
                    });
                    return;
                }
                GUIMediator.safeInvokeAndWait(() -> {
                    LibraryFilesTableMediator.instance().clearTable();
                    statusLabel.setText("");
                    resultsCount = 0;
                });
                if (directoryHolder instanceof MediaTypeSavedFilesDirectoryHolder) {
                    List<File> cache = new ArrayList<>(((MediaTypeSavedFilesDirectoryHolder) directoryHolder).getCache());
                    if (cache.size() > 0) {
                        search(cache);
                        return;
                    }
                } else if (directoryHolder instanceof SavedFilesDirectoryHolder) {
                    List<File> cache = new ArrayList<>(((SavedFilesDirectoryHolder) directoryHolder).getCache());
                    if (cache.size() > 0) {
                        search(cache);
                        return;
                    }
                }
                Set<File> ignore = TorrentUtil.getIgnorableFiles();
                if (directoryHolder instanceof TorrentDirectoryHolder) {
                    search(directoryHolder.getDirectory(), ignore, LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                    return;
                }
                if (directoryHolder instanceof SavedFilesDirectoryHolder) {
                    search(directoryHolder.getDirectory(), ignore, LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                    return;
                }
                Set<File> directories = new HashSet<>(LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue());
                directories.removeAll(LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                for (File dir : directories) {
                    if (dir == null) {
                        continue;
                    }
                    if (!dir.equals(LibrarySettings.USER_MUSIC_FOLDER.getValue()) || !(directoryHolder instanceof MediaTypeSavedFilesDirectoryHolder) || ((MediaTypeSavedFilesDirectoryHolder) directoryHolder).getMediaType().equals(MediaType.getAudioMediaType())) {
                        search(dir, new HashSet<>(), LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                    }
                }
            } catch (Throwable e) {
                // just until we refactor this
                e.printStackTrace();
            }
        }

        /**
         * It searches _query in haystackDir.
         */
        private void search(File haystackDir, Set<File> excludeFiles, Set<File> excludedSubFolders) {
            if (canceled) {
                return;
            }
            if (haystackDir == null || !haystackDir.isDirectory() || !haystackDir.exists()) {
                return;
            }
            final List<File> directories = new ArrayList<>();
            final List<File> results = new ArrayList<>();
            SearchFileFilter searchFilter = new SearchFileFilter(_query);
            for (File child : FileUtils.listFiles(haystackDir)) { //haystackDir.listFiles(searchFilter)) {
                if (canceled) {
                    return;
                }
                /////
                //Stop search if the user selected another item in the library tree
                DirectoryHolder currentDirectoryHolder = LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder();
                if (!directoryHolder.equals(currentDirectoryHolder)) {
                    return;
                }
                /////
                if (excludeFiles.contains(child)) {
                    continue;
                }
                if (child.isHidden()) {
                    continue;
                }
                if (child.isDirectory() && !excludedSubFolders.contains(child)) {
                    directories.add(child);
                } else if (child.isFile()) {
                    if (directoryHolder instanceof FileSettingDirectoryHolder) {
                        if (searchFilter.accept(child, false)) {
                            results.add(child);
                        }
                    } else if (directoryHolder.accept(child)) {
                        results.add(child);
                    }
                }
            }
            Runnable r = () -> {
                LibraryMediator.instance().addFilesToLibraryTable(results);
                if (directoryHolder instanceof SavedFilesDirectoryHolder) {
                    LibraryFilesTableMediator.instance().resetAudioPlayerFileView();
                }
            };
            GUIMediator.safeInvokeLater(r);
            for (File directory : directories) {
                search(directory, excludeFiles, excludedSubFolders);
            }
        }

        private void search(List<File> cache) {
            if (canceled) {
                return;
            }
            final List<File> results = new ArrayList<>();
            SearchFileFilter searchFilter = new SearchFileFilter(_query);
            for (File file : cache) {
                if (canceled) {
                    return;
                }
                /////
                //Stop search if the user selected another item in the library tree
                DirectoryHolder currentDirectoryHolder = LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder();
                if (!directoryHolder.equals(currentDirectoryHolder)) {
                    return;
                }
                /////
                if (file.isHidden()) {
                    continue;
                }
                if (searchFilter.accept(file)) {
                    results.add(file);
                }
            }
            GUIMediator.safeInvokeLater(() -> LibraryMediator.instance().addFilesToLibraryTable(results));
        }
    }

}
