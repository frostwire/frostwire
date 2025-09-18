/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.tasks;

import static com.frostwire.android.util.SystemUtils.postToHandler;
import static com.frostwire.android.util.SystemUtils.postToUIThreadAtFront;

import android.app.Activity;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.transfers.ExistingDownload;
import com.frostwire.android.gui.transfers.InvalidDownload;
import com.frostwire.android.gui.transfers.InvalidTransfer;
import com.frostwire.android.gui.transfers.TorrentFetcherDownload;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.transfers.BaseHttpDownload;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class AsyncStartDownload {

    private static final Logger LOG = Logger.getLogger(AsyncStartDownload.class);

    public AsyncStartDownload(final Context ctx, final SearchResult sr, final String message) {
        //async(ctx, AsyncStartDownload::doInBackground, sr, message, AsyncStartDownload::onPostExecute);
        WeakReference<Context> ctxRef = Ref.weak(ctx);
        postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> run(ctxRef, sr, message));
    }

    private void run(WeakReference<Context> ctxRef, final SearchResult sr, final String message) {
        LOG.info("AsyncStartDownload:run posted and now running to handler", true);
        try {
            if (!Ref.alive(ctxRef)) {
                Ref.free(ctxRef);
                return;
            }
            final Transfer transfer = doInBackground(ctxRef.get(), sr, message);
            if (transfer == null) {
                Ref.free(ctxRef);
                return;
            }
            postToUIThreadAtFront(() -> {
                if (!Ref.alive(ctxRef)) {
                    Ref.free(ctxRef);
                    return;
                }
                try {
                    onPostExecute(ctxRef.get(), sr, message, transfer);
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                } finally {
                    Ref.free(ctxRef);
                }
            });
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    public AsyncStartDownload(final Context ctx, final SearchResult sr) {
        this(ctx, sr, null);
    }

    private static Transfer doInBackground(final Context ctx, final SearchResult sr, final String message) {
        Transfer transfer = null;
        try {
            if (sr instanceof TorrentSearchResult &&
                    !(sr instanceof TorrentCrawledSearchResult)) {
                transfer = TransferManager.instance().downloadTorrent(((TorrentSearchResult) sr).getTorrentUrl(),
                        new HandpickedTorrentDownloadDialogOnFetch((AppCompatActivity) ctx, false), sr.getDisplayName());
            } else {
                transfer = TransferManager.instance().download(sr);
                if (!(transfer instanceof InvalidDownload)) {
                    if (ctx instanceof Activity) {
                        ((Activity) ctx).runOnUiThread(() -> {
                            try {
                                UIUtils.showTransfersOnDownloadStart(ctx);
                            } catch (Throwable t) {
                                if (BuildConfig.DEBUG) {
                                    throw t;
                                }
                                LOG.error("doInBackground() " + t.getMessage(), t);
                            }
                        });
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warn("Error adding new download from result: " + sr, e);
            e.printStackTrace();
        }
        return transfer;
    }

    private static void onPostExecute(final Context ctx, final SearchResult sr, final String message, final Transfer transfer) {
        if (transfer != null) {
            if (!(transfer instanceof InvalidTransfer)) {
                TransferManager tm = TransferManager.instance();
                if (tm.isBittorrentDownloadAndMobileDataSavingsOn(transfer)) {
                    UIUtils.showLongMessage(ctx, R.string.torrent_transfer_enqueued_on_mobile_data);
                    ((BittorrentDownload) transfer).pause();
                } else {
                    if (tm.isBittorrentDownloadAndMobileDataSavingsOff(transfer)) {
                        UIUtils.showLongMessage(ctx, R.string.torrent_transfer_consuming_mobile_data);
                    }

                    if (message != null) {
                        UIUtils.showShortMessage(ctx, message);
                    }
                }
                UIUtils.showTransfersOnDownloadStart(ctx);
            } else if (!(transfer instanceof ExistingDownload)) {
                UIUtils.showLongMessage(ctx, ((InvalidTransfer) transfer).getReasonResId());
            }
        }
    }
}
