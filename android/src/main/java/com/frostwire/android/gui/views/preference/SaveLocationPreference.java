/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.View;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;

/**
 * Preference to select download location within Downloads folder.
 * Validates selection is under DIRECTORY_DOWNLOADS to maintain MediaStore compatibility.
 * Long-press to reset to default Downloads folder.
 */
public final class SaveLocationPreference extends Preference {
    private static final Logger LOG = Logger.getLogger(SaveLocationPreference.class);
    public static final int REQUEST_CODE = 4001;

    public SaveLocationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        updateDisplay();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // Long-press to reset to default
        holder.itemView.setOnLongClickListener(v -> {
            resetToDefault();
            return true;
        });
    }

    /**
     * Called when user selects folder via SAF. Checks if it's under Downloads and shows
     * a warning if it's on external storage (SD card, etc.).
     */
    public void onFolderSelected(String selectedPath) {
        LOG.info("SaveLocationPreference.onFolderSelected(): selectedPath=" + selectedPath);

        if (selectedPath == null) {
            LOG.error("SaveLocationPreference: Selected path is null");
            return;
        }

        boolean isDownloads = isUnderDownloads(selectedPath);

        if (!isDownloads) {
            // Show warning for external storage (SD card, etc.)
            showExternalStorageWarning(selectedPath);
        } else {
            // Safe location - save immediately
            saveDownloadPath(selectedPath);
        }
    }

    /**
     * Shows warning dialog for external storage locations with limitations.
     */
    private void showExternalStorageWarning(String selectedPath) {
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.external_storage_warning_title)
                .setMessage(R.string.external_storage_warning_message)
                .setPositiveButton(R.string.external_storage_warning_proceed, (dialog, which) -> {
                    saveDownloadPath(selectedPath);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    /**
     * Saves the download path to preferences and updates BTEngine.
     */
    private void saveDownloadPath(String selectedPath) {
        ConfigurationManager.instance().setStoragePath(selectedPath);
        String verifyPath = ConfigurationManager.instance().getStoragePath();
        LOG.info("SaveLocationPreference: Saved path=" + selectedPath + ", verified read=" + verifyPath);
        // Update BTEngine in background thread to avoid StrictMode violation
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, this::updateBTEngineDataDir);
        updateDisplay();
        notifyChanged();
    }

    /**
     * Reset to default Downloads folder. Called on long-press.
     */
    private void resetToDefault() {
        ConfigurationManager.instance().setStoragePath("");
        // Update BTEngine in background thread to avoid StrictMode violation
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, this::updateBTEngineDataDir);
        updateDisplay();
        notifyChanged();
    }

    private boolean isUnderDownloads(String path) {
        String downloadsPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        return path != null && path.startsWith(downloadsPath);
    }

    private void updateDisplay() {
        String path = ConfigurationManager.instance().getStoragePath();
        if (path != null && !path.isEmpty() && isUnderDownloads(path)) {
            setSummary(truncate(path) + " (long-press to reset)");
        } else {
            setSummary(R.string.downloads_folder_default);
        }
    }

    private String truncate(String path) {
        return path.length() <= 50 ? path : "..." + path.substring(path.length() - 47);
    }

    /**
     * Updates BTEngine's cached data directory after storage path changes.
     * This ensures downloads use the new path immediately.
     */
    private void updateBTEngineDataDir() {
        try {
            if (BTEngine.ctx != null) {
                BTEngine.ctx.dataDir = Platforms.get().systemPaths().data();
                LOG.info("SaveLocationPreference: Updated BTEngine.ctx.dataDir to " + BTEngine.ctx.dataDir.getAbsolutePath());
            }
        } catch (Exception e) {
            LOG.warn("SaveLocationPreference: Failed to update BTEngine data directory", e);
        }
    }
}
