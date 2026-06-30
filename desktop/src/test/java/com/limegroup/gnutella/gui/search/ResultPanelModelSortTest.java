/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultPanelModelSortTest {

    @Test
    void seedsColumnSortsNumericallyDescending() {
        TestResultPanelModel model = new TestResultPanelModel();
        model.prepareSort(SearchTableColumns.COUNT_IDX, false);

        model.add(stubResult("low-seeds.mp3", 64, 1_000_000L));
        model.add(stubResult("high-seeds.mp3", 436, 2_000_000L));
        model.add(stubResult("mid-seeds.mp3", 109, 3_000_000L));

        model.resortNow();

        assertEquals(436, model.get(0).getSeeds());
        assertEquals(109, model.get(1).getSeeds());
        assertEquals(64, model.get(2).getSeeds());
    }

    @Test
    void seedsColumnSortsNumericallyAscending() {
        TestResultPanelModel model = new TestResultPanelModel();
        model.prepareSort(SearchTableColumns.COUNT_IDX, true);

        model.add(stubResult("low-seeds.mp3", 64, 1_000_000L));
        model.add(stubResult("high-seeds.mp3", 436, 2_000_000L));
        model.add(stubResult("mid-seeds.mp3", 109, 3_000_000L));

        model.resortNow();

        assertEquals(64, model.get(0).getSeeds());
        assertEquals(109, model.get(1).getSeeds());
        assertEquals(436, model.get(2).getSeeds());
    }

    @Test
    void seedsColumnDoesNotUseLexicographicStringOrder() {
        SearchResultDataLine line64 = lineWithSeeds(64);
        SearchResultDataLine line436 = lineWithSeeds(436);

        int stringOrder = AbstractTableMediator.compare("64", "436");
        assertTrue(stringOrder > 0, "lexicographic order treats 64 as greater than 436");

        TestResultPanelModel model = new TestResultPanelModel();
        model.prepareSort(SearchTableColumns.COUNT_IDX, false);
        assertTrue(model.compareLines(line436, line64) < 0);
    }

    @Test
    void sizeColumnSortsNumericallyDescending() {
        TestResultPanelModel model = new TestResultPanelModel();
        model.prepareSort(SearchTableColumns.SIZE_IDX, false);

        model.add(stubResult("small.mp3", 10, 1_000L));
        model.add(stubResult("large.mp3", 10, 9_000_000L));
        model.add(stubResult("medium.mp3", 10, 500_000L));

        model.resortNow();

        assertEquals(9_000_000L, model.get(0).getSize());
        assertEquals(500_000L, model.get(1).getSize());
        assertEquals(1_000L, model.get(2).getSize());
    }

    private static SearchResultDataLine lineWithSeeds(int seeds) {
        SearchResultDataLine line = new SearchResultDataLine(new SearchTableColumns());
        line.initialize(stubResult("file-" + seeds + ".mp3", seeds, 1_000L));
        return line;
    }

    private static UISearchResult stubResult(String filename, int seeds, long size) {
        FileSearchResult fileSearchResult = new FileSearchResult() {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long getSize() {
                return size;
            }

            @Override
            public String getDisplayName() {
                return filename;
            }

            @Override
            public String getDetailsUrl() {
                return "https://example.test/" + filename;
            }

            @Override
            public long getCreationTime() {
                return 0L;
            }

            @Override
            public String getSource() {
                return "test";
            }

            @Override
            public com.frostwire.licenses.License getLicense() {
                return null;
            }

            @Override
            public String getThumbnailUrl() {
                return null;
            }

            @Override
            public boolean isPreliminary() {
                return false;
            }
        };

        return new UISearchResult() {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long getSize() {
                return size;
            }

            @Override
            public long getCreationTime() {
                return 0L;
            }

            @Override
            public String getSource() {
                return "test";
            }

            @Override
            public String getExtension() {
                return "mp3";
            }

            @Override
            public void download(boolean partial) {
            }

            @Override
            public JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
                return popupMenu;
            }

            @Override
            public String getHash() {
                return filename;
            }

            @Override
            public int getSeeds() {
                return seeds;
            }

            @Override
            public SearchEngine getSearchEngine() {
                return null;
            }

            @Override
            public SearchResult getSearchResult() {
                return fileSearchResult;
            }

            @Override
            public void showSearchResultWebPage(boolean now) {
            }

            @Override
            public String getDetailsUrl() {
                return "https://example.test/" + filename;
            }

            @Override
            public String getDisplayName() {
                return filename;
            }

            @Override
            public String getQuery() {
                return "test";
            }

            @Override
            public void play() {
            }
        };
    }

    private static final class TestResultPanelModel extends ResultPanelModel {
        void prepareSort(int column, boolean ascending) {
            _activeColumn = column;
            _ascending = ascending ? 1 : -1;
        }

        int compareLines(SearchResultDataLine a, SearchResultDataLine b) {
            return compare(a, b);
        }

        void resortNow() {
            doResort();
        }
    }
}