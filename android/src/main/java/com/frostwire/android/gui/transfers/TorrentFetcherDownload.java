/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.transfers;

import android.os.Handler;
import android.os.HandlerThread;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorrentFetcherDownload implements BittorrentDownload {

    private static final Logger LOG = Logger.getLogger(TorrentFetcherDownload.class);

    private static final Handler HANDLER;

    static {
        HandlerThread handlerThread = new HandlerThread("TorrentFetcher-HandlerThread");
        handlerThread.start();
        HANDLER = new Handler(handlerThread.getLooper());
    }

    private final TransferManager manager;
    private final TorrentDownloadInfo info;
    private final Date created;
    private final TorrentFetcherListener fetcherListener;
    public final long tokenId;

    private TransferState state;

    TorrentFetcherDownload(TransferManager manager, TorrentDownloadInfo info) {
        this(manager, info, null);
    }

    public TorrentFetcherDownload(TransferManager manager, TorrentDownloadInfo info, TorrentFetcherListener listener) {
        this.manager = manager;
        this.info = info;
        this.created = new Date();
        this.fetcherListener = listener;
        this.tokenId = new Random(System.currentTimeMillis()).nextLong();
        state = TransferState.DOWNLOADING_TORRENT;

        // Use shared HandlerThread with looper to avoid creating a thread per download
        HANDLER.post(new FetcherRunnable());
    }

    String getTorrentUri() {
        return info.getTorrentUrl();
    }

    public String getDisplayName() {
        return info.getDisplayName();
    }

    public TorrentDownloadInfo getTorrentDownloadInfo() {
        return info;
    }

    public TorrentFetcherListener getTorrentFetcherListener() {
        return fetcherListener;
    }

    @Override
    public TransferState getState() {
        return state;
    }

    public int getProgress() {
        return 0;
    }

    public double getSize() {
        return info.getSize();
    }

    @Override
    public Date getCreated() {
        return created;
    }

    public List<TransferItem> getItems() {
        return Collections.emptyList();
    }

    public File getSavePath() {
        return null;
    }

    @Override
    public File getContentSavePath() {
        return null;
    }

    public long getBytesReceived() {
        return 0;
    }

    public long getBytesSent() {
        return 0;
    }

    public long getDownloadSpeed() {
        return 0;
    }

    public long getUploadSpeed() {
        return 0;
    }

    public long getETA() {
        return 0;
    }

    public String getInfoHash() {
        return info.getHash();
    }

    @Override
    public int getConnectedPeers() {
        return 0;
    }

    @Override
    public int getTotalPeers() {
        return 0;
    }

    @Override
    public int getConnectedSeeds() {
        return 0;
    }

    @Override
    public int getTotalSeeds() {
        return 0;
    }

    @Override
    public String magnetUri() {
        return info.makeMagnetUri();
    }


    public String getPeers() {
        return "";
    }

    public String getSeeds() {
        return "";
    }

    public boolean isComplete() {
        return false;
    }

    @Override
    public boolean isDownloading() {
        return true;
    }

    @Override
    public boolean isSeeding() {
        return false;
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public void remove(boolean deleteData) {
        state = TransferState.CANCELED;
        manager.remove(this);
    }

    @Override
    public void remove(boolean deleteTorrent, boolean deleteData) {
        remove(deleteData);
    }

    @Override
    public String getPredominantFileExtension() {
        return "torrent";
    }

    public void pause() {
    }

    public void resume() {
    }

    @Override
    public String getName() {
        if (info.getDisplayName() == null || info.getDisplayName().isEmpty()) {
            return info.getDetailsUrl();
        }
        return info.getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TorrentFetcherDownload)) {
            return false;
        }

        String u1 = info.getTorrentUrl();
        String u2 = ((TorrentFetcherDownload) o).info.getTorrentUrl();

        return u1.equalsIgnoreCase(u2);
    }

    @Override
    public File previewFile() {
        return null;
    }

    private void downloadTorrent(final byte[] data) {
        try {
            TorrentInfo ti = TorrentInfo.bdecode(data);
            boolean[] selection = null;
            if (info.getRelativePath() != null) {
                selection = calculateSelection(ti, info.getRelativePath());
            }

            BTEngine.getInstance().download(ti, null, selection, null, TransferManager.instance().isDeleteStartedTorrentEnabled());
        } catch (Throwable e) {
            LOG.error("Error downloading torrent", e);
        }
    }

    private boolean[] calculateSelection(TorrentInfo ti, String path) {
        boolean[] selection = new boolean[ti.numFiles()];

        FileStorage fs = ti.files();
        for (int i = 0; i < selection.length; i++) {
            String filePath = fs.filePath(i);
            if (path.endsWith(filePath) || filePath.endsWith(path)) {
                selection[i] = true;
            }
        }

        return selection;
    }

    private class FetcherRunnable implements Runnable {

        @Override
        public void run() {
            if (state == TransferState.CANCELED) {
                return;
            }

            try {
                byte[] data;
                String uri = info.getTorrentUrl();
                String referrer = info.getReferrerUrl();
                if (uri.startsWith("http")) {
                    // use our http client, since we can handle referer
                    data = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(uri, 30000, referrer);
                } else {
                    // use bittorrent engine, since it can handle dht and pex
                    data = new LibTorrentMagnetDownloader().download(uri, 30);
                }

                if (state == TransferState.CANCELED) {
                    return;
                }

                if (data != null) {
                    // Don't download the torrent yourself, there's a listener waiting
                    // for the .torrent, and it's up to this listener to start the transfer.
                    if (fetcherListener != null) {
                        fetcherListener.onTorrentInfoFetched(data, uri, tokenId);
                        return;
                    }

                    try {
                        downloadTorrent(data);
                    } finally {
                        remove(false);
                    }
                } else {
                    state = TransferState.ERROR;
                }
            } catch (Throwable e) {
                state = TransferState.ERROR;
                LOG.error("Error downloading torrent from uri", e);
            }
        }
    }
}
