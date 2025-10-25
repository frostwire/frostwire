package com.frostwire.gui.bittorrent;

import com.frostwire.gui.filters.TableLineFilter;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.BittorrentSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
     * Cache hidden uploads count to avoid iterating HIDDEN list on every status refresh.
     * The cache update is done in background thread to prevent EDT blocking.
     */
    private volatile int cachedHiddenUploads = 0;
    private volatile long lastHiddenCountUpdateTime = 0;
    private static final long HIDDEN_COUNT_CACHE_INTERVAL_MS = 500;
    private final AtomicBoolean isUpdatingHiddenCount = new AtomicBoolean(false);

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
        // Get count from parent (visible uploads)
        int count = super.getActiveUploads();

        // Add count from hidden uploads (use cache)
        long now = System.currentTimeMillis();
        if (now - lastHiddenCountUpdateTime >= HIDDEN_COUNT_CACHE_INTERVAL_MS) {
            // Trigger background update (non-blocking on EDT)
            scheduleHiddenUploadsUpdate();
        }
        count += cachedHiddenUploads;

        return count;
    }

    /**
     * Schedules a background thread to update the hidden uploads cache.
     * This prevents EDT blocking when there are many hidden downloads.
     */
    private void scheduleHiddenUploadsUpdate() {
        // Prevent multiple concurrent updates
        if (!isUpdatingHiddenCount.compareAndSet(false, true)) {
            return;
        }

        BackgroundQueuedExecutorService.schedule(() -> {
            try {
                int count = 0;
                try {
                    for (BTDownloadDataLine aHIDDEN : HIDDEN) {
                        BTDownload downloader = aHIDDEN.getInitializeObject();
                        if (downloader != null && downloader.isCompleted() && downloader.getState() == TransferState.SEEDING) {
                            count++;
                        }
                    }
                } catch (Throwable e) {
                    // ignore, multi-threading issues?
                    System.err.println("Error updating hidden uploads cache: " + e.getMessage());
                }
                cachedHiddenUploads = count;
                lastHiddenCountUpdateTime = System.currentTimeMillis();
            } finally {
                isUpdatingHiddenCount.set(false);
            }
        });
    }
}
