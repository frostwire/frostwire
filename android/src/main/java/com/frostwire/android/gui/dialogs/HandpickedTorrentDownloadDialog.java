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

import static com.frostwire.android.util.SystemUtils.postToHandler;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TorrentFetcherDownload;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.tcp_endpoint_vector;
import com.frostwire.transfers.Transfer;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author gubatron
 * @author aldenml
 */
public final class HandpickedTorrentDownloadDialog extends AbstractConfirmListDialog<HandpickedTorrentDownloadDialog.TorrentFileEntry> {

    private TorrentInfo torrentInfo;
    private String magnetUri;
    private long torrentFetcherDownloadTokenId;
    private boolean openTransfersOnCancel;
    private static final String BUNDLE_KEY_TORRENT_INFO_PATH = "torrentInfoPath";
    private static final String BUNDLE_KEY_MAGNET_URI = "magnetUri";
    private static final String BUNDLE_KEY_TORRENT_FETCHER_DOWNLOAD_TOKEN_ID = "torrentFetcherDownloadTokenId";
    private static final String BUNDLE_KEY_OPEN_TRANSFERS_ON_CANCEL = "openTransfersOnCancel";

    // non-void constructors must be avoided when creating dialogs. use setArguments instead
    public HandpickedTorrentDownloadDialog() {
        super();
    }

    public static HandpickedTorrentDownloadDialog newInstance(
            Context ctx,
            TorrentInfo tinfo,
            String magnetUri,
            long torrentFetcherDownloadTokenId,
            boolean openTransfersOnCancel) {
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
        Arrays.fill(allChecked, true);

        dlg.prepareArguments(R.drawable.download_icon,
                tinfo.name(),
                ctx.getString(R.string.pick_the_files_you_want_to_download_from_this_torrent),
                JsonUtils.toJson(torrentInfoList),
                SelectionMode.MULTIPLE_SELECTION);
        final Bundle arguments = dlg.getArguments();
        // write torrent metadata to a temp file to avoid large Bundles
        String infoHash = tinfo.infoHashType().has_v2() ?
                tinfo.infoHashV2().toString() : tinfo.infoHashV1().toString();
        File cacheFile = new File(ctx.getCacheDir(), "torrent_" + infoHash);
        try {
            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(tinfo.bencode());
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write torrent metadata to cache", e);
        }

        if (arguments != null) {
            arguments.putString(BUNDLE_KEY_TORRENT_INFO_PATH, cacheFile.getAbsolutePath());
            arguments.putString(BUNDLE_KEY_MAGNET_URI, magnetUri);
            arguments.putLong(BUNDLE_KEY_TORRENT_FETCHER_DOWNLOAD_TOKEN_ID, torrentFetcherDownloadTokenId);
            arguments.putBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS, allChecked);
            arguments.putBoolean(BUNDLE_KEY_OPEN_TRANSFERS_ON_CANCEL, openTransfersOnCancel);
        }

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
        if (listDataInJSON == null || listDataInJSON.isEmpty()) {
            return new ArrayList<>();
        }

        final TorrentFileEntryList torrentFileEntryList = JsonUtils.toObject(listDataInJSON, TorrentFileEntryList.class);
        if (torrentFileEntryList == null || torrentFileEntryList.list == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(torrentFileEntryList.list);
    }

    @Override
    public ConfirmListDialogDefaultAdapter<TorrentFileEntry> createAdapter(Context context, List<TorrentFileEntry> listData, SelectionMode selectionMode, Bundle bundle) {
        listData.sort(new NameComparator());
        return new HandpickedTorrentFileEntriesDialogAdapter(context, listData, selectionMode);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // We persist only the file path; raw data lives on disk
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        byte[] torrentInfoData = null;
        if (arguments != null) {
            String path = arguments.getString(BUNDLE_KEY_TORRENT_INFO_PATH);
            if (path != null) {
                File cacheFile = new File(path);
                if (cacheFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(cacheFile);
                         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = fis.read(buf)) != -1) {
                            bos.write(buf, 0, read);
                        }
                        torrentInfoData = bos.toByteArray();
                    } catch (IOException ignored) {
                    }
                    cacheFile.delete();
                }
            }
        }
        if (this.torrentInfo == null && torrentInfoData != null) {
            torrentInfo = TorrentInfo.bdecode(torrentInfoData);
            magnetUri = arguments.getString(BUNDLE_KEY_MAGNET_URI, null);
            torrentFetcherDownloadTokenId = arguments.getLong(BUNDLE_KEY_TORRENT_FETCHER_DOWNLOAD_TOKEN_ID);
            openTransfersOnCancel = arguments.getBoolean(BUNDLE_KEY_OPEN_TRANSFERS_ON_CANCEL);
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
            if (!transfers.isEmpty()) {
                for (Transfer i : transfers) {
                    if (i instanceof TorrentFetcherDownload tempTransfer) {
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

    public static class TorrentFileEntry {
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

    private static class HandpickedTorrentFileEntriesDialogAdapter extends ConfirmListDialogDefaultAdapter<TorrentFileEntry> {

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
        public String getCheckedSum() {
            if (checked == null || checked.isEmpty()) {
                return null;
            }
            long totalBytes = 0;
            for (TorrentFileEntry entry : checked) {
                totalBytes += entry.getSize();
            }
            return UIUtils.getBytesInHuman(totalBytes);
        }
    }

    private static class OnStartDownloadsClickListener implements View.OnClickListener {
        private final WeakReference<Context> ctxRef;
        @SuppressWarnings("rawtypes")
        private WeakReference<AbstractConfirmListDialog> dlgRef;
        private final static Logger LOG = Logger.getLogger(OnStartDownloadsClickListener.class);

        @SuppressWarnings("rawtypes")
        OnStartDownloadsClickListener(Context ctx, AbstractConfirmListDialog dlg) {
            ctxRef = new WeakReference<>(ctx);
            dlgRef = new WeakReference<>(dlg);
        }

        @SuppressWarnings("rawtypes")
        public void setDialog(AbstractConfirmListDialog dlg) {
            dlgRef = new WeakReference<>(dlg);
        }

        @Override
        public void onClick(View v) {
            if (Ref.alive(ctxRef) && Ref.alive(dlgRef)) {
                @SuppressWarnings("rawtypes") final AbstractConfirmListDialog dlg = dlgRef.get();

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

            postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER,
                    () -> {
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
                            TorrentInfo torrentInfo = dlg.getTorrentInfo();
                            BTEngine.getInstance().download(torrentInfo,
                                    null,
                                    selection,
                                    peers,
                                    TransferManager.instance().isDeleteStartedTorrentEnabled());
                            dlg.removeTorrentFetcherDownloadFromTransfers();
                            TorrentHandle torrentHandle = BTEngine.getInstance().find(torrentInfo);
                            TransferManager.instance().updateUIBittorrentDownload(torrentHandle);
                            UIUtils.showTransfersOnDownloadStart(ctx);
                            MainActivity.refreshTransfers(ctx);
                        } catch (Throwable t) {
                            LOG.info("startTorrentPartialDownload(): " + t.getMessage(), t);
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
                if (dlgRef.get().openTransfersOnCancel) {
                    MainActivity.refreshTransfers(Objects.requireNonNull(dlgRef.get().getDialog()).getContext());
                }
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
