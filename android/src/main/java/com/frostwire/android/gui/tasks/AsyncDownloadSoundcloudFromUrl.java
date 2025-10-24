/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.tasks;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.ConfirmSoundcloudDownloadDialog;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;

import java.util.ArrayList;
import java.util.List;

/**
 * @author aldenml
 * @author gubatron
 */
public final class AsyncDownloadSoundcloudFromUrl {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(AsyncDownloadSoundcloudFromUrl.class);

    public AsyncDownloadSoundcloudFromUrl(Context ctx, String soundcloudUrl) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
            List<SoundcloudSearchResult> results = doInBackground(soundcloudUrl);
            SystemUtils.postToUIThread(() -> onPostExecute(ctx, soundcloudUrl, results));
        });
    }

    private static List<SoundcloudSearchResult> doInBackground(final String soundcloudUrl) {
        List<SoundcloudSearchResult> results = new ArrayList<>();
        try {
            String url = soundcloudUrl;
            if (soundcloudUrl.contains("?in=")) {
                url = soundcloudUrl.substring(0, url.indexOf("?in="));
            }
            String resolveURL = SoundcloudSearchPerformer.resolveUrl(url);
            HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
            String json = client.get(resolveURL, 10000);
            results = SoundcloudSearchPerformer.fromJson(json, true);
        } catch (Throwable e) {
            LOG.error("AsyncDownloadSoundcloudFromUrl::doInBackground: Error downloading from Soundcloud", e);
        }
        return results;
    }

    private static void onPostExecute(Context ctx, final String soundcloudUrl, List<SoundcloudSearchResult> results) {
        if (ctx == null) {
            return;
        }
        if (results.isEmpty()) {
            UIUtils.showLongMessage(ctx, R.string.sorry_could_not_find_valid_download_location_at, soundcloudUrl);
            return;
        }

        MainActivity activity = (MainActivity) ctx;
        ConfirmSoundcloudDownloadDialog dlg = createConfirmListDialog(ctx, results);
        dlg.show(activity.getSupportFragmentManager());
    }

    private static ConfirmSoundcloudDownloadDialog createConfirmListDialog(Context ctx, List<SoundcloudSearchResult> results) {
        String title = ctx.getString(R.string.confirm_download);
        String whatToDownload = ctx.getString((results.size() > 1) ? R.string.playlist : R.string.track);
        String totalSize = UIUtils.getBytesInHuman(getTotalSize(results));
        String text = ctx.getString(R.string.are_you_sure_you_want_to_download_the_following, whatToDownload, totalSize);

        //AbstractConfirmListDialog
        return ConfirmSoundcloudDownloadDialog.newInstance(ctx, title, text, results);
    }

    private static long getTotalSize(List<SoundcloudSearchResult> results) {
        long totalSizeInBytes = 0;
        for (SoundcloudSearchResult sr : results) {
            totalSizeInBytes += sr.getSize();
        }
        return totalSizeInBytes;
    }
}
