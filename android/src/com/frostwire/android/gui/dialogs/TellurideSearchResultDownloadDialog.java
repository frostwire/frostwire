/*
 *     Created by Angel Leon (@gubatron)
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

import androidx.fragment.app.FragmentManager;

import com.frostwire.android.R;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class TellurideSearchResultDownloadDialog extends AbstractConfirmListDialog<TellurideSearchResult> {

    private static final Logger LOG = Logger.getLogger(TellurideSearchResultDownloadDialog.class);

    public static void show(Context ctx, FragmentManager fragmentManager, List<TellurideSearchResult> listData) {
        String dialogTag = "TELLURIDE_SEARCH_RESULT_DOWNLOAD_DIALOG";

        // Check if the dialog is already shown
        if (fragmentManager.findFragmentByTag(dialogTag) != null) {
            LOG.warn("TellurideSearchResultDownloadDialog is already shown.");
            return;
        }

        TellurideSearchResultDownloadDialog dlg = newInstance(ctx, listData);
        dlg.show(fragmentManager, dialogTag);
    }


    public static TellurideSearchResultDownloadDialog newInstance(
            Context ctx,
            List<TellurideSearchResult> listData) {
        TellurideSearchResultDownloadDialog dlg = new TellurideSearchResultDownloadDialog();
        TellurideSearchResultList srList = new TellurideSearchResultList();
        srList.listData = listData;
        dlg.prepareArguments(R.drawable.download_icon,
                Optional.of(ctx.getString(R.string.downloads)).orElse("Downloads"),
                ctx.getString(R.string.select_option_below),
                JsonUtils.toJson(srList),
                SelectionMode.SINGLE_SELECTION);
        return dlg;
    }

    @Override
    protected View.OnClickListener createOnYesListener() {
        return v -> {
            LOG.info("startTellurideDownloadDialog::createOnYesListener");
            TellurideSearchResult selectedItem = getSelectedItem();
            if (selectedItem != null) {
                SearchFragment.startDownload(getActivity(), selectedItem, getContext().getString(R.string.download_added_to_queue));
            }
            dismiss();
        };
    }

    @Override
    public List<TellurideSearchResult> deserializeData(String listDataInJSON) {
        TellurideSearchResultDownloadDialog.TellurideSearchResultList srList = JsonUtils.toObject(listDataInJSON, TellurideSearchResultDownloadDialog.TellurideSearchResultList.class);
        return srList.listData;
    }

    @Override
    public ConfirmListDialogDefaultAdapter<TellurideSearchResult> createAdapter(Context context, List<TellurideSearchResult> listData, AbstractConfirmListDialog.SelectionMode selectionMode, Bundle bundle) {
        return new TellurideSearchResultDownloadDialogAdapter(context, listData, selectionMode);
    }

    private static class TellurideSearchResultList {
        List<TellurideSearchResult> listData;
    }

    public static class TellurideSearchResultDownloadDialogAdapter extends ConfirmListDialogDefaultAdapter<TellurideSearchResult> {
        public TellurideSearchResultDownloadDialogAdapter(Context context, List<TellurideSearchResult> list, SelectionMode selectionMode) {
            super(context, list, selectionMode);
        }

        @Override
        public CharSequence getItemTitle(TellurideSearchResult data) {
            return data.getDisplayName();
        }

        @Override
        public long getItemSize(TellurideSearchResult data) {
            return data.getSize();
        }

        @Override
        public CharSequence getItemThumbnailUrl(TellurideSearchResult data) {
            return data.getThumbnailUrl();
        }

        @Override
        public int getItemThumbnailResourceId(TellurideSearchResult data) {
            return data.getDisplayName().toLowerCase(Locale.ROOT).contains("audio") ? R.drawable.list_item_audio_icon : R.drawable.list_item_video_icon;
        }

        @Override
        public void addItem(TellurideSearchResult item) {
            SystemUtils.postToUIThread(() -> super.addItem(item));
        }
    }
}
