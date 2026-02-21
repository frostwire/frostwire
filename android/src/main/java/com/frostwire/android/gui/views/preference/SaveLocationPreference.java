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
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.util.Logger;

/**
 * Preference to select default download location via SAF dialog.
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

    public void onLocationSelected(Uri uri) {
        if (uri != null && canWriteTo(uri)) {
            getContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Extract filesystem path from SAF URI if possible, otherwise store URI
            String pathToStore = extractPathFromUri(uri);
            if (pathToStore == null) {
                pathToStore = uri.toString();
            }

            ConfigurationManager.instance().setStoragePath(pathToStore);
            updateDisplay();
        } else {
            LOG.error("SaveLocationPreference: Cannot write to " + uri);
        }
    }

    private String extractPathFromUri(Uri uri) {
        // For content:// URIs from external storage, try to extract the filesystem path
        // This works for Downloads folder and other public directories on Android 11+
        String uriPath = uri.getPath();  // e.g., "/tree/primary:Downloads/FrostWire"
        if (uriPath != null && uriPath.contains("primary:")) {
            try {
                String relativePath = uriPath.substring(uriPath.indexOf("primary:") + 8);  // "Downloads/FrostWire"
                return android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + relativePath;
            } catch (Exception e) {
                LOG.warn("SaveLocationPreference: Could not extract path from URI: " + uri, e);
            }
        }
        return null;
    }

    private boolean canWriteTo(Uri uri) {
        DocumentFile doc = DocumentFile.fromTreeUri(getContext(), uri);
        return doc != null && doc.canWrite();
    }

    private void updateDisplay() {
        String path = ConfigurationManager.instance().getStoragePath();
        setSummary(path != null && !path.isEmpty() ? truncate(path) : getContext().getString(R.string.not_set));
    }

    private String truncate(String path) {
        return path.length() <= 50 ? path : "..." + path.substring(path.length() - 47);
    }
}
