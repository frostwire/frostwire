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

import android.app.Activity;
import android.content.Context;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.InvalidDownload;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.YouTubeDownloadDialog;
import com.frostwire.android.gui.transfers.ExistingDownload;
import com.frostwire.android.gui.transfers.InvalidTransfer;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ContextTask;
import com.frostwire.util.Logger;
import com.frostwire.search.ScrapedTorrentFileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubePackageSearchResult;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public class StartDownloadTask extends ContextTask<Transfer> {

    private static final Logger LOG = Logger.getLogger(StartDownloadTask.class);

    private final SearchResult sr;
    private final String message;

    public StartDownloadTask(Context ctx, SearchResult sr, String message) {
        super(ctx);
        this.sr = sr;
        this.message = message;
    }
    
    public StartDownloadTask(Context ctx, SearchResult sr){
        this(ctx,sr,null);
    }

    @Override
    protected Transfer doInBackground() {
        Transfer transfer = null;
        try {
            if (sr instanceof TorrentSearchResult &&
                !(sr instanceof ScrapedTorrentFileSearchResult) &&
                !(sr instanceof TorrentCrawledSearchResult)) {
                transfer = TransferManager.instance().downloadTorrent(((TorrentSearchResult) sr).getTorrentUrl(),
                        new HandpickedTorrentDownloadDialogOnFetch((Activity) getContext()));
            } else if (sr instanceof YouTubePackageSearchResult) {
                YouTubeDownloadDialog ytDownloadDlg = YouTubeDownloadDialog.newInstance(getContext(), (YouTubePackageSearchResult) sr);
                ytDownloadDlg.show(((Activity) getContext()).getFragmentManager());
            }
            else {
                transfer = TransferManager.instance().download(sr);
                if(!(transfer instanceof InvalidDownload)) {
                    UIUtils.showTransfersOnDownloadStart(getContext());
                }
            }
        } catch (Throwable e) {
            LOG.warn("Error adding new download from result: " + sr, e);
            e.printStackTrace();
        }
        return transfer;
    }

    @Override
    protected void onPostExecute(Context ctx, Transfer transfer) {
        if (transfer != null) {
            if (ctx instanceof Activity) {
                Offers.showInterstitialOfferIfNecessary((Activity) ctx, Offers.PLACEMENT_INTERSTITIAL_EXIT, false, false);
            }

            if (!(transfer instanceof InvalidTransfer)) {
                TransferManager tm = TransferManager.instance();
                if (tm.isBittorrentDownloadAndMobileDataSavingsOn(transfer)) {
                    UIUtils.showLongMessage(ctx, R.string.torrent_transfer_enqueued_on_mobile_data);
                    ((BittorrentDownload) transfer).pause();
                } else {
                    if (tm.isBittorrentDownloadAndMobileDataSavingsOff(transfer)) {
                        UIUtils.showLongMessage(ctx, R.string.torrent_transfer_consuming_mobile_data);
                    }
                    
                    if (message != null){
                        UIUtils.showShortMessage(ctx, message);
                    }
                }
            } else if (!(transfer instanceof ExistingDownload)) {
                UIUtils.showLongMessage(ctx, ((InvalidTransfer) transfer).getReasonResId());
            }
        }
    }
}
