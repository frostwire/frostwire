/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.tasks;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.ConfirmSoundcloudDownloadDialog;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;

import java.util.ArrayList;
import java.util.List;

import static com.frostwire.android.util.Asyncs.async;

/*
 * @author aldenml
 * @author gubatron
 */
public final class AsyncDownloadSoundcloudFromUrl {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(AsyncDownloadSoundcloudFromUrl.class);

    public AsyncDownloadSoundcloudFromUrl(Context ctx, String soundcloudUrl) {
        async(ctx, (context, soundcloudUrl1) -> doInBackground(soundcloudUrl1), soundcloudUrl, AsyncDownloadSoundcloudFromUrl::onPostExecute);
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
            e.printStackTrace();
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
        dlg.show(activity.getFragmentManager());
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
