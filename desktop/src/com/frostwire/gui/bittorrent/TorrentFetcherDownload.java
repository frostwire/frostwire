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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.CopyrightLicenseBroker;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.UserAgentGenerator;
import com.limegroup.gnutella.gui.GUIMediator;

import java.io.File;
import java.util.Date;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorrentFetcherDownload implements BTDownload {

    private static final Logger LOG = Logger.getLogger(TorrentFetcherDownload.class);

    private final String uri;
    private final String referer;
    private final String cookie;
    private final String displayName;
    private final boolean partial;
    private final String relativePath;

    private final Date dateCreated;

    private TransferState state;

    public TorrentFetcherDownload(String uri, String referrer, String cookie, String displayName, boolean partial, String relativePath) {
        this.uri = uri;
        this.referer = referrer;
        this.cookie = cookie;
        this.displayName = displayName;
        this.partial = partial;

        if (!partial) {
            this.relativePath = relativePath;
        } else {
            this.relativePath = null;
        }

        this.dateCreated = new Date();

        state = TransferState.DOWNLOADING_TORRENT;

        Thread t = new Thread(new FetcherRunnable(), "Torrent-Fetcher - " + uri);
        t.setDaemon(true);
        t.start();
    }

    public TorrentFetcherDownload(String uri, String referrer, String displayName, boolean partial, String relativePath) {
        this(uri, referrer, null, displayName, partial, relativePath);
    }

    public TorrentFetcherDownload(String uri, String referrer, String displayName, boolean partial) {
        this(uri, referrer, displayName, partial, null);
    }

    public TorrentFetcherDownload(String uri, boolean partial) {
        this(uri, null, getDownloadNameFromMagnetURI(uri), partial);
    }

    public long getSize() {
        return -1;
    }

    @Override
    public String getName() {
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isResumable() {
        return false;
    }

    public boolean isPausable() {
        return false;
    }

    public boolean isCompleted() {
        return false;
    }

    public TransferState getState() {
        return state;
    }

    public void remove() {
        state = TransferState.CANCELED;
    }

    public void pause() {
    }

    public void resume() {
    }

    public File getSaveLocation() {
        return null;
    }

    public int getProgress() {
        return 0;
    }

    public long getBytesReceived() {
        return 0;
    }

    public long getBytesSent() {
        return 0;
    }

    public double getDownloadSpeed() {
        return 0;
    }

    public double getUploadSpeed() {
        return 0;
    }

    public long getETA() {
        return 0;
    }

    public String getPeersString() {
        return "";
    }

    public String getSeedsString() {
        return "";
    }

    public boolean isDeleteTorrentWhenRemove() {
        return false;
    }

    public void setDeleteTorrentWhenRemove(boolean deleteTorrentWhenRemove) {
    }

    public boolean isDeleteDataWhenRemove() {
        return false;
    }

    public void setDeleteDataWhenRemove(boolean deleteDataWhenRemove) {
    }

    public String getHash() {
        return null;
    }

    public String getSeedToPeerRatio() {
        return "";
    }

    public String getShareRatio() {
        return "";
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public boolean isPartialDownload() {
        return false;
    }

    public long getSize(boolean update) {
        return -1;
    }

    @Override
    public PaymentOptions getPaymentOptions() {
        return null;
    }

    @Override
    public CopyrightLicenseBroker getCopyrightLicenseBroker() {
        return null;
    }

    @Override
    public boolean canPreview() {
        return false;
    }

    @Override
    public File getPreviewFile() {
        return null;
    }

    private void cancel() {
        state = TransferState.CANCELED;
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                BTDownloadMediator.instance().remove(TorrentFetcherDownload.this);
            }
        });
    }

    private void downloadTorrent(final byte[] data) {
        if (relativePath != null) {
            try {
                TorrentInfo ti = TorrentInfo.bdecode(data);
                boolean[] selection = calculateSelection(ti, relativePath);
                BTEngine.getInstance().download(ti, null, selection);
            } catch (Throwable e) {
                LOG.error("Error downloading torrent", e);
            }
        } else {
            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    try {

                        boolean[] selection = null;

                        if (partial) {
                            PartialFilesDialog dlg = new PartialFilesDialog(GUIMediator.getAppFrame(), data, displayName);
                            dlg.setVisible(true);
                            selection = dlg.getFilesSelection();
                            if (selection == null) {
                                return;
                            }
                        }

                        TorrentInfo ti = TorrentInfo.bdecode(data);
                        BTEngine.getInstance().download(ti, null, selection);

                    } catch (Throwable e) {
                        LOG.error("Error downloading torrent", e);
                    }
                }
            });
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

    private static String getDownloadNameFromMagnetURI(String uri) {
        if (!uri.startsWith("magnet:")) {
            return uri;
        }

        if (uri.contains("dn=")) {
            String[] split = uri.split("&");
            for (String s : split) {
                if (s.toLowerCase().startsWith("dn=") && s.length() > 3) {
                    return UrlUtils.decode(s.split("=")[1]);
                }
            }
        }

        return uri;
    }

    private class FetcherRunnable implements Runnable {

        @Override
        public void run() {
            if (state == TransferState.CANCELED) {
                return;
            }

            try {
                byte[] data;
                if (uri.startsWith("http")) {
                    data = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(uri, 15000, UserAgentGenerator.getUserAgent(), referer, cookie);
                } else {
                    data = BTEngine.getInstance().fetchMagnet(uri, 90);
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
                    state = TransferState.ERROR_NOT_ENOUGH_PEERS;
                }
            } catch (Throwable e) {
                state = TransferState.ERROR;
                LOG.error("Error downloading torrent from uri", e);
            }
        }
    }
}
