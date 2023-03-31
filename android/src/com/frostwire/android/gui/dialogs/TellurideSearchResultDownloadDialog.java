/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

import java.util.List;

public class TellurideSearchResultDownloadDialog extends AbstractConfirmListDialog<TellurideSearchResult> {

    private static final Logger LOG = Logger.getLogger(TellurideSearchResultDownloadDialog.class);

    public static TellurideSearchResultDownloadDialog newInstance(
            Context ctx,
            List<TellurideSearchResult> listData) {
        TellurideSearchResultDownloadDialog dlg = new TellurideSearchResultDownloadDialog();
        TellurideSearchResultList srList = new TellurideSearchResultList();
        srList.listData = listData;
        dlg.prepareArguments(R.drawable.download_icon,
                ctx.getString(R.string.downloads),
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
            TellurideSearchResult sr = (TellurideSearchResult) data;
            return sr.getDisplayName();
        }

        @Override
        public long getItemSize(TellurideSearchResult data) {
            TellurideSearchResult sr = (TellurideSearchResult) data;
            return sr.getSize();
        }

        @Override
        public CharSequence getItemThumbnailUrl(TellurideSearchResult data) {
            TellurideSearchResult sr = (TellurideSearchResult) data;
            return sr.getThumbnailUrl();
        }

        @Override
        public int getItemThumbnailResourceId(TellurideSearchResult data) {
            return data.getDisplayName().contains("audio") ? R.drawable.list_item_audio_icon : R.drawable.list_item_video_icon;
        }

        @Override
        public void addItem(TellurideSearchResult item) {
            SystemUtils.postToUIThread(() -> super.addItem(item));
        }
    }
}
