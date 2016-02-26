/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.transfers;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorrentFetcherDownload implements BittorrentDownload {

    private static final Logger LOG = Logger.getLogger(TorrentFetcherDownload.class);

    private final TransferManager manager;
    private final TorrentDownloadInfo info;
    private final Date created;

    private TransferState state;

    public TorrentFetcherDownload(TransferManager manager, TorrentDownloadInfo info) {
        this.manager = manager;
        this.info = info;
        this.created = new Date();

        state = TransferState.DOWNLOADING_TORRENT;

        Thread t = new Thread(new FetcherRunnable(), "Torrent-Fetcher - " + info.getTorrentUrl());
        t.setDaemon(true);
        t.start();
    }

    public String getTorrentUri() {
        return info.getTorrentUrl();
    }

    public String getDisplayName() {
        return info.getDisplayName();
    }

    public String getStatus() {
        return state.name();
    }

    public int getProgress() {
        return 0;
    }

    public long getSize() {
        return info.getSize();
    }

    public Date getDateCreated() {
        return created;
    }

    public List<TransferItem> getItems() {
        return Collections.emptyList();
    }

    public File getSavePath() {
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

    public String getHash() {
        return info.getHash();
    }

    public String makeMagnetUri() {
        return info.makeMagnetUri();
    }


    public String getPeers() {
        return "";
    }

    public String getSeeds() {
        return "";
    }

    public boolean isResumable() {
        return false;
    }

    public boolean isPausable() {
        return false;
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
    public void cancel() {
        cancel(false);
    }

    @Override
    public void cancel(boolean deleteData) {
        state = TransferState.CANCELED;
        manager.remove(this);
    }

    public void pause() {
    }

    public void resume() {
    }

    @Override
    public boolean hasPaymentOptions() {
        return false;
    }

    @Override
    public PaymentOptions getPaymentOptions() {
        return null;
    }

    @Override
    public String getDetailsUrl() {
        return info.getDetailsUrl();
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

            BTEngine.getInstance().download(ti, null, selection);

        } catch (Throwable e) {
            LOG.error("Error downloading torrent", e);
        }
    }

    private boolean[] calculateSelection(TorrentInfo ti, String path) {
        boolean[] selection = new boolean[ti.getNumFiles()];

        FileStorage fs = ti.getFiles();
        for (int i = 0; i < selection.length; i++) {
            String filePath = fs.getFilePath(i);
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
                byte[] data = null;
                String uri = info.getTorrentUrl();
                String referrer = info.getReferrerUrl();
                if (uri.startsWith("http")) {
                    // use our http client, since we can handle referer
                    data = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(uri, 30000, referrer);
                } else {
                    data = BTEngine.getInstance().fetchMagnet(uri, 30);
                }

                if (state == TransferState.CANCELED) {
                    return;
                }

                if (data != null) {
                    try {
                        downloadTorrent(data);
                    } finally {
                        cancel();
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
