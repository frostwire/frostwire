package com.frostwire.gui.bittorrent;

import com.frostwire.gui.filters.TableLineFilter;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.settings.BittorrentSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters out certain rows from the data model.
 *
 * @author Sumeet Thadani, Sam Berlin, Gubatron
 */
public class BTDownloadRowFilteredModel extends BTDownloadModel {
    /**
     * The filter to use in this row filter.
     */
    private final TableLineFilter<BTDownloadDataLine> FILTER;
    /**
     * A list of all filtered results.
     */
    private final List<BTDownloadDataLine> HIDDEN;

    /**
     * Constructs a TableRowFilter with the specified TableLineFilter.
     */
    BTDownloadRowFilteredModel(TableLineFilter<BTDownloadDataLine> f) {
        if (f == null)
            throw new NullPointerException("null filter");
        FILTER = f;
        HIDDEN = new ArrayList<>();
    }

    /**
     * Determines whether or not this line should be added.
     */
    public int add(BTDownloadDataLine tl, int row) {
        if (!allow(tl)) {
            HIDDEN.add(tl);
            return -1;
        } else {
            return super.add(tl, row);
        }
    }

    @Override
    public void clear() {
        super.clear();
        HIDDEN.clear();
    }

    /**
     * Notification that the filters have changed.
     */
    void filtersChanged() {
        rebuild();
        fireTableDataChanged();
    }

    /**
     * Determines whether or not the specified line is allowed by the filter.
     */
    private boolean allow(BTDownloadDataLine tl) {
        return FILTER.allow(tl);
    }

    /**
     * Rebuilds the internal map to denote a new filter.
     */
    private void rebuild() {
        List<BTDownloadDataLine> existing = new ArrayList<>(_list);
        List<BTDownloadDataLine> hidden = new ArrayList<>(HIDDEN);
        clear();
        // For stuff in _list, we can just re-add the DataLines as-is.
        for (BTDownloadDataLine anExisting : existing) {
            addSorted(anExisting);
        }
        // Merge the hidden TableLines
        for (BTDownloadDataLine tl : hidden) {
            addSorted(tl);
        }
    }

    @Override
    public void sort(int col) {
        super.sort(col);
        saveSortSettings();
    }

    @Override
    public void unsort() {
        super.unsort();
        saveSortSettings();
    }

    private void saveSortSettings() {
        BittorrentSettings.BTMEDIATOR_COLUMN_SORT_INDEX.setValue(getSortColumn());
        BittorrentSettings.BTMEDIATOR_COLUMN_SORT_ORDER.setValue(isSortAscending());
    }

    @Override
    int getActiveUploads() {
        int count = super.getActiveUploads();
        try {
            for (BTDownloadDataLine aHIDDEN : HIDDEN) {
                BTDownload downloader = aHIDDEN.getInitializeObject();
                if (downloader.isCompleted() && downloader.getState() == TransferState.SEEDING) {
                    count++;
                }
            }
        } catch (Throwable e) {
            // ignore, multi-threading issues?
        }
        return count;
    }
}
