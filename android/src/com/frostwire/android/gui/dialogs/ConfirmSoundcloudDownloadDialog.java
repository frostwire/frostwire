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

package com.frostwire.android.gui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.gui.tasks.AsyncStartDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConfirmSoundcloudDownloadDialog extends AbstractConfirmListDialog<SoundcloudSearchResult> {

    public static ConfirmSoundcloudDownloadDialog newInstance(
            Context ctx,
            String dialogTitle,
            String dialogText,
            List<SoundcloudSearchResult> listData) {
        ConfirmSoundcloudDownloadDialog dlg = new ConfirmSoundcloudDownloadDialog();
        SoundcloudSearchResultList srList = new SoundcloudSearchResultList();
        srList.listData = listData;

        dlg.prepareArguments(R.drawable.download_icon, dialogTitle, dialogText, JsonUtils.toJson(srList),
                SelectionMode.MULTIPLE_SELECTION);

        dlg.setOnYesListener(new OnStartDownloadsClickListener(ctx, dlg));
        return dlg;
    }

    private static void startDownloads(Context ctx, List<? extends SearchResult> srs) {
        if (srs != null && !srs.isEmpty()) {
            for (SearchResult sr : srs) {
                new AsyncStartDownload(ctx, sr);
            }
            UIUtils.showTransfersOnDownloadStart(ctx);
        }
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
    protected OnStartDownloadsClickListener createOnYesListener() {
        return new OnStartDownloadsClickListener(getActivity(), this);
    }

    private static class SoundcloudSearchResultList {
        List<SoundcloudSearchResult> listData;
    }

    // TODO: this class needs heavy refactor/cleanup
    private static class OnStartDownloadsClickListener implements View.OnClickListener {
        private final WeakReference<Context> ctxRef;
        private final WeakReference<AbstractConfirmListDialog> dlgRef;

        OnStartDownloadsClickListener(Context ctx, AbstractConfirmListDialog dlg) {
            ctxRef = new WeakReference<>(ctx);
            dlgRef = new WeakReference<>(dlg);
        }

        @Override
        public void onClick(View v) {
            if (Ref.alive(ctxRef) && Ref.alive(dlgRef)) {
                final AbstractConfirmListDialog dlg = dlgRef.get();

                final AbstractConfirmListDialog.SelectionMode selectionMode = dlg.getSelectionMode();
                List<SoundcloudSearchResult> results = (selectionMode == AbstractConfirmListDialog.SelectionMode.NO_SELECTION) ?
                        (List<SoundcloudSearchResult>) dlg.getList() :
                        new ArrayList<>();

                if (results.isEmpty()) {
                    results.addAll(dlg.getChecked());
                }

                if (!results.isEmpty()) {
                    startDownloads(ctxRef.get(), results);
                    dlg.dismiss();
                }
            }
        }
    }

    private static class SoundcloudPlaylistConfirmListDialogAdapter extends ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> {
        SoundcloudPlaylistConfirmListDialogAdapter(Context context, List list, AbstractConfirmListDialog.SelectionMode selectionMode) {
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
            return data.getThumbnailUrl();
        }

        @Override
        public int getItemThumbnailResourceId(SoundcloudSearchResult data) {
            return -1;
        }

        @Override
        public String getCheckedSum() {
            if (checked==null || checked.isEmpty()) {
                return null;
            }

            long totalBytes = 0;
            for (SoundcloudSearchResult sr : (Set<SoundcloudSearchResult>) checked) {
                totalBytes += sr.getSize();
            }

            return UIUtils.getBytesInHuman(totalBytes);
        }
    }
}
