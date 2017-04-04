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

package com.frostwire.android.gui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.R;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.tcp_endpoint_vector;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Created on 4/19/16.
 *
 * @author gubatron
 * @author aldenml
 */
@SuppressWarnings("WeakerAccess")
// We need the class to be public so that the dialog can be rotated (via Reflection)
public class HandpickedTorrentDownloadDialog extends AbstractConfirmListDialog<HandpickedTorrentDownloadDialog.TorrentFileEntry> {
    private static Logger LOG = Logger.getLogger(HandpickedTorrentDownloadDialog.class);
    private TorrentInfo torrentInfo;
    private String magnetUri;
    private static final String BUNDLE_KEY_TORRENT_INFO_DATA = "torrentInfoData";
    private static final String BUNDLE_KEY_MAGNET_URI = "magnetUri";

    public HandpickedTorrentDownloadDialog() {
        super();
    }

    public static HandpickedTorrentDownloadDialog newInstance(
            Context ctx,
            TorrentInfo tinfo,
            String magnetUri) {
        //
        // ideas:  - pre-selected file(s) to just check the one(s)
        //         - passing a file path
        //         - passing a byte[] to create the tinfo from.

        HandpickedTorrentDownloadDialog dlg = new HandpickedTorrentDownloadDialog();

        // this creates a bundle that gets passed to setArguments(). It's supposed to be ready
        // before the dialog is attached to the underlying activity, after we attach to it, then
        // we are able to use such Bundle to create our adapter.
        final TorrentFileEntryList torrentInfoList = getTorrentInfoList(tinfo.files());
        boolean[] allChecked = new boolean[torrentInfoList.list.size()];
        for (int i = 0; i < allChecked.length; i++) {
            allChecked[i] = true;
        }

        dlg.onAttach((Activity) ctx);
        dlg.prepareArguments(R.drawable.download_icon,
                tinfo.name(),
                ctx.getString(R.string.pick_the_files_you_want_to_download_from_this_torrent),
                JsonUtils.toJson(torrentInfoList),
                SelectionMode.MULTIPLE_SELECTION);
        final Bundle arguments = dlg.getArguments();
        arguments.putByteArray(BUNDLE_KEY_TORRENT_INFO_DATA, tinfo.bencode());
        arguments.putString(BUNDLE_KEY_MAGNET_URI, magnetUri);
        arguments.putBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS, allChecked);

        dlg.setOnYesListener(new OnStartDownloadsClickListener(ctx, dlg));
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
    protected View.OnClickListener createOnYesListener(AbstractConfirmListDialog dlg) {
        return new OnStartDownloadsClickListener(getActivity(), dlg);
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
        }
        super.initComponents(dlg, savedInstanceState);
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
                        Offers.showInterstitialOfferIfNecessary((Activity) ctxRef.get(), Offers.PLACEMENT_INTERSTITIAL_EXIT, false, false);
                    }
                }
            }
        }

        private void startTorrentPartialDownload(final Context context, List<TorrentFileEntry> results) {
            if (context == null ||
                    !Ref.alive(dlgRef) ||
                    results == null ||
                    dlgRef.get().getList() == null ||
                    results.size() > dlgRef.get().getList().size()) {
                LOG.warn("can't startTorrentPartialDownload()");
                return;
            }

            final HandpickedTorrentDownloadDialog theDialog = (HandpickedTorrentDownloadDialog) dlgRef.get();

            final boolean[] selection = new boolean[theDialog.getList().size()];
            for (TorrentFileEntry selectedFileEntry : results) {
                selection[selectedFileEntry.getIndex()] = true;
            }

            Engine.instance().getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String magnet = theDialog.getMagnetUri();
                        List<TcpEndpoint> peers = parsePeers(magnet);
                        BTEngine.getInstance().download(theDialog.getTorrentInfo(),
                                null,
                                selection,
                                peers,
                                TransferManager.instance().isDeleteStartedTorrentEnabled());
                        UIUtils.showTransfersOnDownloadStart(context);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        private static List<TcpEndpoint> parsePeers(String magnet) {
            if (magnet == null || magnet.isEmpty() || magnet.startsWith("http")) {
                return Collections.emptyList();
            }

            add_torrent_params params = add_torrent_params.create_instance();
            // TODO: replace this with the public API
            error_code ec = new error_code();
            add_torrent_params.parse_magnet_uri(magnet, params, ec);
            tcp_endpoint_vector v = params.getPeers();
            int size = (int) v.size();
            ArrayList<TcpEndpoint> l = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                l.add(new TcpEndpoint(v.get(i)));
            }

            return l;
        }
    }

    private static class NameComparator implements Comparator<TorrentFileEntry> {
        @Override
        public int compare(TorrentFileEntry left, TorrentFileEntry right) {
            return left.getDisplayName().compareTo(right.getDisplayName());
        }
    }
}
