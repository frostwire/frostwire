/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2014, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.frostwire.android.R;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.adnetworks.Offers;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Ref;
import org.apache.commons.io.FilenameUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created on 4/19/16.
 *
 * @author gubatron
 * @author aldenml
 *
 */
@SuppressWarnings("WeakerAccess") // We need the class to be public so that the dialog can be rotated (via Reflection)
public class HandpickedTorrentDownloadDialog extends AbstractConfirmListDialog<HandpickedTorrentDownloadDialog.TorrentFileEntry> {
    private static Logger LOG = Logger.getLogger(HandpickedTorrentDownloadDialog.class);
    private TorrentInfo torrentInfo;
    private static final String BUNDLE_KEY_TORRENT_INFO_DATA = "torrentInfoData";

    public HandpickedTorrentDownloadDialog() {
        super();
    }

    private HandpickedTorrentDownloadDialog(TorrentInfo tinfo) {
        super();
        this.torrentInfo = tinfo;
    }

    public static HandpickedTorrentDownloadDialog newInstance(
            Context ctx,
            TorrentInfo tinfo) {
        //
        // ideas:  - pre-selected file(s) to just check the one(s)
        //         - passing a file path
        //         - passing a byte[] to create the tinfo from.

        HandpickedTorrentDownloadDialog dlg = new HandpickedTorrentDownloadDialog(tinfo);

        // this creates a bundle that gets passed to setArguments(). It's supposed to be ready
        // before the dialog is attached to the underlying activity, after we attach to it, then
        // we are able to use such Bundle to create our adapter.
        final TorrentFileEntryList torrentInfoList = getTorrentInfoList(tinfo.files());
        boolean[] allChecked = new boolean[torrentInfoList.list.size()];
        for (int i=0; i < allChecked.length; i++) {
            allChecked[i] = true;
        }

        dlg.onAttach((Activity) ctx);
        dlg.prepareArguments(R.drawable.download_icon,
                tinfo.name(),
                ctx.getString(R.string.pick_the_files_you_want_to_download_from_this_torrent),
                JsonUtils.toJson(torrentInfoList),
                SelectionMode.MULTIPLE_SELECTION);
        dlg.getArguments().putBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS, allChecked);
        dlg.setOnYesListener(new OnStartDownloadsClickListener(ctx, dlg));
        return dlg;
    }


    private static TorrentFileEntryList getTorrentInfoList(FileStorage fileStorage) {
        TorrentFileEntryList entryList = new TorrentFileEntryList();
        if (fileStorage != null && fileStorage.numFiles() > 0) {
            int n = fileStorage.numFiles();
            for (int i=0; i < n; i++) {
                entryList.add(new TorrentFileEntry(i,
                        fileStorage.fileName(i),
                        fileStorage.filePath(i),
                        fileStorage.fileSize(i)));
            }
        }
        return entryList;
    }

    @Override
    protected View.OnClickListener createOnYesListener(AbstractConfirmListDialog dlg) {
        return new OnStartDownloadsClickListener(getActivity(), dlg);
    }

    @Override
    void prepareArguments(int dialogIcon, String dialogTitle, String dialogText, String listDataInJSON, SelectionMode selectionMode) {
        super.prepareArguments(dialogIcon, dialogTitle, dialogText, listDataInJSON, selectionMode);
        onSaveInstanceState(getArguments());
    }

    @Override
    public List<TorrentFileEntry> deserializeData(String listDataInJSON) {
        final TorrentFileEntryList torrentFileEntryList = JsonUtils.toObject(listDataInJSON, TorrentFileEntryList.class);
        return torrentFileEntryList.list;
    }

    @Override
    public ConfirmListDialogDefaultAdapter<TorrentFileEntry> createAdapter(Context context, List<TorrentFileEntry> listData, SelectionMode selectionMode, Bundle bundle) {
        return new HandpickedTorrentFileEntriesDialogAdapter(context, listData, selectionMode);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (outState!=null) {
            outState.putByteArray(BUNDLE_KEY_TORRENT_INFO_DATA, torrentInfo.bencode());
        }
        super.onSaveInstanceState(outState); //saves the torrentInfo in bytes.
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        byte[] torrentInfoData;
        if (this.torrentInfo == null &&
            savedInstanceState != null &&
            (torrentInfoData=savedInstanceState.getByteArray(BUNDLE_KEY_TORRENT_INFO_DATA))!=null) {
            torrentInfo = TorrentInfo.bdecode(torrentInfoData);
        }
        super.initComponents(dlg, savedInstanceState);
    }

    private TorrentInfo getTorrentInfo() {
        return torrentInfo;
    }

    private static class TorrentFileEntryList {
        final List<TorrentFileEntry> list = new ArrayList<>();
        public void add(TorrentFileEntry entry) {
            list.add(entry);
        }
    }

    static class TorrentFileEntry {
        private final int index;
        private final String name;
        private final String path;
        private final long size;

        TorrentFileEntry(int index, String name, String path, long size) {
            this.index = index;
            this.name = name;
            this.path = path;
            this.size = size;
        }

        public int getIndex() {
            return index;
        }

        public String getDisplayName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }
    }

    private class HandpickedTorrentFileEntriesDialogAdapter extends ConfirmListDialogDefaultAdapter<TorrentFileEntry> {

        HandpickedTorrentFileEntriesDialogAdapter(Context context,
                                                  List<TorrentFileEntry> list,
                                                  SelectionMode selectionMode) {
            super(context, list, selectionMode);
        }

        @Override
        public CharSequence getItemTitle(TorrentFileEntry data) {
            return data.getDisplayName();
        }

        @Override
        public long getItemSize(TorrentFileEntry data) {
            return data.getSize();
        }

        @Override
        public CharSequence getItemThumbnailUrl(TorrentFileEntry data) {
            return null;
        }

        @Override
        public int getItemThumbnailResourceId(TorrentFileEntry data) {
            return MediaType.getFileTypeIconId(FilenameUtils.getExtension(data.getPath()));
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            return super.getView(position, view, parent);
        }

        @Override
        public String getCheckedSum() {
            if (checked == null || checked.isEmpty()) {
                return null;
            }
            long totalBytes = 0;
            for (TorrentFileEntry entry : (Set<TorrentFileEntry>) checked) {
                totalBytes += entry.getSize();
            }
            return UIUtils.getBytesInHuman(totalBytes);
        }
    }

    private static class OnStartDownloadsClickListener implements View.OnClickListener {
        private final WeakReference<Context> ctxRef;
        private WeakReference<AbstractConfirmListDialog> dlgRef;

        OnStartDownloadsClickListener(Context ctx, AbstractConfirmListDialog dlg) {
            ctxRef = new WeakReference<>(ctx);
            dlgRef = new WeakReference<>(dlg);
        }

        public void setDialog(AbstractConfirmListDialog dlg) {
            dlgRef = new WeakReference<>(dlg);
        }

        @Override
        public void onClick(View v) {
            if (Ref.alive(ctxRef) && Ref.alive(dlgRef)) {
                final AbstractConfirmListDialog dlg = dlgRef.get();

                final AbstractConfirmListDialog.SelectionMode selectionMode = dlg.getSelectionMode();
                List<TorrentFileEntry> checked = (selectionMode == AbstractConfirmListDialog.SelectionMode.NO_SELECTION) ?
                        (List<TorrentFileEntry>) dlg.getList() :
                        new ArrayList<TorrentFileEntry>();

                if (checked.isEmpty()) {
                    checked.addAll(dlg.getChecked());
                }

                if (!checked.isEmpty()) {
                    LOG.info("about to startTorrentPartialDownload()");
                    startTorrentPartialDownload(ctxRef.get(), checked);
                    dlg.dismiss();

                    if (ctxRef.get() instanceof Activity) {
                        Offers.showInterstitialOfferIfNecessary((Activity) ctxRef.get());
                    }
                }
            }
        }

        private void startTorrentPartialDownload(Context context, List<TorrentFileEntry> results) {
            if (context == null ||
                !Ref.alive(dlgRef) ||
                results == null ||
                dlgRef.get().getList() == null ||
                results.size() > dlgRef.get().getList().size()) {
                LOG.warn("can't startTorrentPartialDownload()");
                return;
            }

            final HandpickedTorrentDownloadDialog theDialog = (HandpickedTorrentDownloadDialog) dlgRef.get();

            boolean[] selection = new boolean[theDialog.getList().size()];
            for (TorrentFileEntry selectedFileEntry : results) {
                selection[selectedFileEntry.getIndex()] = true;
            }
            BTEngine.getInstance().download(theDialog.getTorrentInfo(),
                    null,
                    selection);
            UIUtils.showTransfersOnDownloadStart(context);
        }
    }
}
