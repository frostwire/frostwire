/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TorrentFetcherDownload;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.tcp_endpoint_vector;
import com.frostwire.transfers.Transfer;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public final class HandpickedTorrentDownloadDialog extends AbstractConfirmListDialog<HandpickedTorrentDownloadDialog.TorrentFileEntry> {

    private TorrentInfo torrentInfo;
    private String magnetUri;
    private long torrentFetcherDownloadTokenId;
    private static final String BUNDLE_KEY_TORRENT_INFO_DATA = "torrentInfoData";
    private static final String BUNDLE_KEY_MAGNET_URI = "magnetUri";
    private static final String BUNDLE_KEY_TORRENT_FETCHER_DOWNLOAD_TOKEN_ID = "torrentFetcherDownloadTokenId";

    // non-void constructors must be avoided when creating dialogs. use setArguments instead
    public HandpickedTorrentDownloadDialog() {
        super();
    }

    public static HandpickedTorrentDownloadDialog newInstance(
            Context ctx,
            TorrentInfo tinfo,
            String magnetUri,
            long torrentFetcherDownloadTokenId) {
        //
        // ideas:  - pre-selected file(s) to just check the one(s)
        //         - passing a file path
        //         - passing a byte[] to create the tinfo from.

        final HandpickedTorrentDownloadDialog dlg = new HandpickedTorrentDownloadDialog();

        // this creates a bundle that gets passed to setArguments(). It's supposed to be ready
        // before the dialog is attached to the underlying activity, after we attach to it, then
        // we are able to use such Bundle to create our adapter.
        final TorrentFileEntryList torrentInfoList = getTorrentInfoList(tinfo.files());
        boolean[] allChecked = new boolean[torrentInfoList.list.size()];
        for (int i = 0; i < allChecked.length; i++) {
            allChecked[i] = true;
        }

        dlg.prepareArguments(R.drawable.download_icon,
                tinfo.name(),
                ctx.getString(R.string.pick_the_files_you_want_to_download_from_this_torrent),
                JsonUtils.toJson(torrentInfoList),
                SelectionMode.MULTIPLE_SELECTION);
        final Bundle arguments = dlg.getArguments();
        arguments.putByteArray(BUNDLE_KEY_TORRENT_INFO_DATA, tinfo.bencode());
        arguments.putString(BUNDLE_KEY_MAGNET_URI, magnetUri);
        arguments.putLong(BUNDLE_KEY_TORRENT_FETCHER_DOWNLOAD_TOKEN_ID, torrentFetcherDownloadTokenId);
        arguments.putBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS, allChecked);

        return dlg;
    }


    private static TorrentFileEntryList getTorrentInfoList(FileStorage fileStorage) {
        TorrentFileEntryList entryList = new TorrentFileEntryList();
        if (fileStorage != null && fileStorage.numFiles() > 0) {
            int n = fileStorage.numFiles();
            for (int i = 0; i < n; i++) {
                entryList.add(new TorrentFileEntry(i,
                        fileStorage.fileName(i),
                        fileStorage.filePath(i),
                        fileStorage.fileSize(i)));
            }
        }
        return entryList;
    }

    @Override
    protected View.OnClickListener createOnYesListener() {
        return new OnStartDownloadsClickListener(getActivity(), this);
    }

    @Override
    public List<TorrentFileEntry> deserializeData(String listDataInJSON) {
        final TorrentFileEntryList torrentFileEntryList = JsonUtils.toObject(listDataInJSON, TorrentFileEntryList.class);
        return torrentFileEntryList.list;
    }

    @Override
    public ConfirmListDialogDefaultAdapter<TorrentFileEntry> createAdapter(Context context, List<TorrentFileEntry> listData, SelectionMode selectionMode, Bundle bundle) {
        Collections.sort(listData, new NameComparator());
        return new HandpickedTorrentFileEntriesDialogAdapter(context, listData, selectionMode);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (outState != null && torrentInfo != null) {
            outState.putByteArray(BUNDLE_KEY_TORRENT_INFO_DATA, torrentInfo.bencode());
            outState.putString(BUNDLE_KEY_MAGNET_URI, magnetUri);
            outState.putLong(BUNDLE_KEY_TORRENT_FETCHER_DOWNLOAD_TOKEN_ID, torrentFetcherDownloadTokenId);
        }
        super.onSaveInstanceState(outState); //saves the torrentInfo in bytes.
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        byte[] torrentInfoData;
        Bundle arguments = getArguments();
        if (this.torrentInfo == null &&
                arguments != null &&
                (torrentInfoData = arguments.getByteArray(BUNDLE_KEY_TORRENT_INFO_DATA)) != null) {
            torrentInfo = TorrentInfo.bdecode(torrentInfoData);
            magnetUri = arguments.getString(BUNDLE_KEY_MAGNET_URI, null);
            torrentFetcherDownloadTokenId = arguments.getLong(BUNDLE_KEY_TORRENT_FETCHER_DOWNLOAD_TOKEN_ID);
            if (torrentFetcherDownloadTokenId != -1) {
                setOnCancelListener(new OnCancelDownloadsClickListener(this));
            }
            setOnYesListener(new OnStartDownloadsClickListener(dlg.getContext(), this));
        }

        super.initComponents(dlg, savedInstanceState);
    }

    private void removeTorrentFetcherDownloadFromTransfers() {
        // if I was made and there was a TorrentFetcherDownload waiting for me in TransferManager
        if (torrentFetcherDownloadTokenId != -1) {
            List<Transfer> transfers = TransferManager.instance().getTransfers();
            if (transfers != null && !transfers.isEmpty()) {
                for (Transfer i : transfers) {
                    if (i instanceof TorrentFetcherDownload) {
                        TorrentFetcherDownload tempTransfer = (TorrentFetcherDownload) i;
                        if (tempTransfer.tokenId == torrentFetcherDownloadTokenId) {
                            TransferManager.instance().remove(i);
                            return;
                        }
                    }
                }
            }
        }
    }

    private TorrentInfo getTorrentInfo() {
        return torrentInfo;
    }

    public String getMagnetUri() {
        return magnetUri;
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
        public double getItemSize(TorrentFileEntry data) {
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

//        @Override
//        public View getView(int position, View view, ViewGroup parent) {
//            return super.getView(position, view, parent);
//        }

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
                        new ArrayList<>();

                if (checked.isEmpty()) {
                    checked.addAll(dlg.getChecked());
                }

                if (!checked.isEmpty()) {
                    try {
                        dlg.dismiss();
                    } catch (Throwable ignored) {
                        // FragmentManager might throw illegal state exception after dialog is dismissed checking state loss
                    }
                    startTorrentPartialDownload(checked);

                    if (ctxRef.get() instanceof Activity) {
                        Offers.showInterstitialOfferIfNecessary((Activity) ctxRef.get(), Offers.PLACEMENT_INTERSTITIAL_MAIN, false, false);
                    }
                }
            }
        }

        private void startTorrentPartialDownload(List<TorrentFileEntry> results) {
            if (!Ref.alive(ctxRef) ||
                    !Ref.alive(dlgRef) ||
                    results == null ||
                    dlgRef.get().getList() == null ||
                    results.size() > dlgRef.get().getList().size()) {
                return;
            }

            final boolean[] selection = new boolean[((HandpickedTorrentDownloadDialog) dlgRef.get()).getList().size()];
            for (TorrentFileEntry selectedFileEntry : results) {
                selection[selectedFileEntry.getIndex()] = true;
            }

            Engine.instance().getThreadPool().execute(() -> {
                try {
                    // there is a still a chance of reference getting null, this is in
                    // the background
                    if (!Ref.alive(ctxRef) || !Ref.alive(dlgRef)) {
                        return;
                    }
                    Context ctx = ctxRef.get();
                    HandpickedTorrentDownloadDialog dlg = (HandpickedTorrentDownloadDialog) dlgRef.get();

                    String magnet = dlg.getMagnetUri();
                    List<TcpEndpoint> peers = parsePeers(magnet);
                    BTEngine.getInstance().download(dlg.getTorrentInfo(),
                            null,
                            selection,
                            peers,
                            TransferManager.instance().isDeleteStartedTorrentEnabled());
                    UIUtils.showTransfersOnDownloadStart(ctx);
                    dlg.removeTorrentFetcherDownloadFromTransfers();
                    MainActivity.refreshTransfers(ctx);
                } catch (Throwable ignored) {
                }
            });

            TransferManager.instance().incrementStartedTransfers();
        }

        private static List<TcpEndpoint> parsePeers(String magnet) {
            if (magnet == null || magnet.isEmpty() || magnet.startsWith("http")) {
                return Collections.emptyList();
            }

            // TODO: replace this with the public API
            error_code ec = new error_code();
            add_torrent_params params = add_torrent_params.parse_magnet_uri(magnet, ec);
            tcp_endpoint_vector v = params.get_peers();
            int size = (int) v.size();
            ArrayList<TcpEndpoint> l = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                l.add(new TcpEndpoint(v.get(i)));
            }

            return l;
        }
    }

    private static class OnCancelDownloadsClickListener implements DialogInterface.OnCancelListener {

        private final WeakReference<HandpickedTorrentDownloadDialog> dlgRef;

        OnCancelDownloadsClickListener(HandpickedTorrentDownloadDialog dlg) {
            dlgRef = Ref.weak(dlg);
        }

        @Override
        public void onCancel(DialogInterface dialogInterface) {
            if (Ref.alive(dlgRef)) {
                HandpickedTorrentDownloadDialog handpickedTorrentDownloadDialog = dlgRef.get();
                handpickedTorrentDownloadDialog.removeTorrentFetcherDownloadFromTransfers();
                // can't use dlgRef.get().getContext() since that call exists only for API >= 23
                MainActivity.refreshTransfers(dlgRef.get().getDialog().getContext());
            }
        }
    }

    private static class NameComparator implements Comparator<TorrentFileEntry> {
        @Override
        public int compare(TorrentFileEntry left, TorrentFileEntry right) {
            return left.getDisplayName().compareTo(right.getDisplayName());
        }
    }

}
