/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.tasks;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import android.widget.CompoundButton;
import com.frostwire.android.R;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.AbstractConfirmListDialog;
import com.frostwire.android.gui.dialogs.ConfirmListDialogDefaultAdapter;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ContextTask;
import com.frostwire.logging.Logger;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Ref;
import com.frostwire.util.http.HttpClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/*
 * @author aldenml
 * @author gubatron
 */
public final class DownloadSoundcloudFromUrlTask extends ContextTask<List<SoundcloudSearchResult>> {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(DownloadSoundcloudFromUrlTask.class);
    private final String soundcloudUrl;
    private WeakReference<ConfirmSoundcloudDownloadDialog> dlgRef;

    public DownloadSoundcloudFromUrlTask(Context ctx, String soundcloudUrl) {
        super(ctx);
        this.soundcloudUrl = soundcloudUrl;
    }

    private ConfirmSoundcloudDownloadDialog createConfirmListDialog(Context ctx, List<SoundcloudSearchResult> results) {
        String title = ctx.getString(R.string.confirm_download);
        String whatToDownload = ctx.getString((results.size() > 1) ? R.string.playlist : R.string.track);
        String totalSize = UIUtils.getBytesInHuman(getTotalSize(results));
        String text = ctx.getString(R.string.are_you_sure_you_want_to_download_the_following, whatToDownload, totalSize);

        //AbstractConfirmListDialog
        ConfirmSoundcloudDownloadDialog dlg = ConfirmSoundcloudDownloadDialog.newInstance(ctx, title, text, results);
        dlg.setOnYesListener(new OnStartDownloadsClickListener(ctx, dlg));
        return dlg;
    }

    private long getTotalSize(List<SoundcloudSearchResult> results) {
        long totalSizeInBytes = 0;
        for (SoundcloudSearchResult sr : results) {
            totalSizeInBytes += sr.getSize();
        }
        return totalSizeInBytes;
    }

    public static void startDownloads(Context ctx, List<? extends SearchResult> srs) {
        if (srs != null && !srs.isEmpty()) {
            for (SearchResult sr : srs) {
                StartDownloadTask task = new StartDownloadTask(ctx, sr);
                task.execute();
            }
            UIUtils.showTransfersOnDownloadStart(ctx);
        }
    }

    @Override
    protected void onPostExecute(Context ctx, List<SoundcloudSearchResult> results) {
        if (!results.isEmpty()) {
            MainActivity activity = (MainActivity) ctx;
            ConfirmSoundcloudDownloadDialog dlg = createConfirmListDialog(ctx, results);
            dlgRef = new WeakReference<>(dlg);
            dlg.show(activity.getFragmentManager());
        }
    }

    /**
    public void dismissDialog() {
        if (Ref.alive(dlgRef)) {
            dlgRef.get().dismiss();
        }
    }*/

    @Override
    protected List<SoundcloudSearchResult> doInBackground() {
        List<SoundcloudSearchResult> results = new ArrayList<>();

        try {
            String url = soundcloudUrl;
            if (soundcloudUrl.contains("?in=")) {
                url = soundcloudUrl.substring(0, url.indexOf("?in="));
            }

            String resolveURL = SoundcloudSearchPerformer.resolveUrl(url);

            HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
            String json = client.get(resolveURL, 10000);

            results = SoundcloudSearchPerformer.fromJson(json);

        } catch (Throwable e) {
            e.printStackTrace();
        }
        return results;
    }

    private static class OnStartDownloadsClickListener implements View.OnClickListener {
        private final WeakReference<Context> ctxRef;
        private WeakReference<AbstractConfirmListDialog> dlgRef;

        public OnStartDownloadsClickListener(Context ctx, AbstractConfirmListDialog dlg) {
            ctxRef = new WeakReference<>(ctx);
            dlgRef = new WeakReference<>(dlg);
        }

        public void setDialog(AbstractConfirmListDialog dlg) {
            dlgRef = new WeakReference<>(dlg);
        }

        @Override
        public void onClick(View v) {
            if (Ref.alive(ctxRef) && Ref.alive(dlgRef)) {
                AbstractConfirmListDialog dlg = dlgRef.get();
                final AbstractConfirmListDialog.SelectionMode selectionMode = dlg.getSelectionMode();
                List<SoundcloudSearchResult> results = (selectionMode == AbstractConfirmListDialog.SelectionMode.NO_SELECTION) ?
                        (List<SoundcloudSearchResult>) dlg.getList() :
                        new ArrayList<SoundcloudSearchResult>();

                if (selectionMode == AbstractConfirmListDialog.SelectionMode.MULTIPLE_SELECTION) {
                    results.addAll(dlg.getChecked());
                } else if (selectionMode == AbstractConfirmListDialog.SelectionMode.SINGLE_SELECTION) {
                    SoundcloudSearchResult selected = results.get(dlg.getLastSelected());
                    if (selected == null) {
                        // MIGHT DO: dlg.displayErrorNotice(ERROR_CODE);
                        return;
                    }
                    results.add(selected);
                }
                startDownloads(ctxRef.get(), results);
                dlg.dismiss();
            }
        }
    }

    private static class SoundcloudSearchResultList {
        List<SoundcloudSearchResult> listData;
    }

    private static class SoundcloudPlaylistConfirmListDialogAdapter extends ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> {
        public SoundcloudPlaylistConfirmListDialogAdapter(Context context, List list, AbstractConfirmListDialog.SelectionMode selectionMode) {
            super(context, list, selectionMode);
        }

        @Override
        public CharSequence getItemTitle(SoundcloudSearchResult data) {
            return data.getDisplayName();
        }

        @Override
        public long getItemSize(SoundcloudSearchResult data) {
            return data.getSize();
        }

        @Override
        public CharSequence getItemThumbnailUrl(SoundcloudSearchResult data) {
            return data.getDisplayName();
        }

        @Override
        public int getItemThumbnailResourceId(SoundcloudSearchResult data) {
            return -1;
        }
    }

    public static class ConfirmSoundcloudDownloadDialog extends AbstractConfirmListDialog<SoundcloudSearchResult> {
        public ConfirmSoundcloudDownloadDialog(Context context, List<SoundcloudSearchResult> listData, SelectionMode selectionMode) {
            super(context, listData, selectionMode, null);
        }

        public static ConfirmSoundcloudDownloadDialog newInstance(
                Context ctx,
                String dialogTitle,
                String dialogText,
                List<SoundcloudSearchResult> listData) {
            ConfirmSoundcloudDownloadDialog dlg = new ConfirmSoundcloudDownloadDialog(ctx, listData, SelectionMode.MULTIPLE_SELECTION);
            SoundcloudSearchResultList srList = new SoundcloudSearchResultList();
            srList.listData = listData;
            dlg.prepareArguments(R.drawable.download_icon, dialogTitle, dialogText, JsonUtils.toJson(srList));
            return dlg;
        }

        @Override
        public List<SoundcloudSearchResult> deserializeData(String listDataInJSON) {
            SoundcloudSearchResultList srList = JsonUtils.toObject(listDataInJSON, SoundcloudSearchResultList.class);
            return srList.listData;
        }

        @Override
        public ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> createAdapter(Context context,
                                                                                     List<SoundcloudSearchResult> listData,
                                                                                     SelectionMode selectionMode,
                                                                                     Bundle bundle) {
            return new SoundcloudPlaylistConfirmListDialogAdapter(context, listData, selectionMode);
        }


        @Override
        protected OnStartDownloadsClickListener createOnYesListener(final AbstractConfirmListDialog dlg) {
            OnStartDownloadsClickListener listener = new OnStartDownloadsClickListener(getActivity(), dlg);
            listener.setDialog(this);
            return listener;
        }
    }
}
