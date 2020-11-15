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
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.tcp_endpoint_vector;
import com.frostwire.search.PerformersHelper;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.UserAgentGenerator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.VPNDropGuard;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
    private final String hash;
    private final Date dateCreated;
    private TransferState state;

    private TorrentFetcherDownload(String uri, String referrer, String cookie, String displayName, boolean partial, String relativePath) {
        this.uri = uri;
        if (uri.startsWith("magnet")) {
            hash = PerformersHelper.parseInfoHash(uri);
        } else {
            hash = "";
        }
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

    public String getUri() {
        return uri;
    }

    public double getSize() {
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

    public void setDeleteTorrentWhenRemove(boolean deleteTorrentWhenRemove) {
    }

    public void setDeleteDataWhenRemove(boolean deleteDataWhenRemove) {
    }

    public String getHash() {
        return hash;
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
        GUIMediator.safeInvokeLater(() -> BTDownloadMediator.instance().remove(TorrentFetcherDownload.this));
    }

    private void downloadTorrent(final byte[] data, final List<TcpEndpoint> peers) {
        if (VPNDropGuard.canUseBitTorrent()) {
            if (relativePath != null) {
                try {
                    TorrentInfo ti = TorrentInfo.bdecode(data);
                    boolean[] selection = calculateSelection(ti, relativePath);
                    BTEngine.getInstance().download(ti, null, selection, peers);
                } catch (Throwable e) {
                    LOG.error("Error downloading torrent", e);
                }
            } else {
                GUIMediator.safeInvokeLater(() -> {
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
                        BTEngine.getInstance().download(ti, null, selection, peers);
                        GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
                    } catch (Throwable e) {
                        LOG.error("Error downloading torrent", e);
                    }
                });
            }
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
                if (uri.startsWith("http")) {
                    data = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(uri, 15000, UserAgentGenerator.getUserAgent(), referer, cookie);
                } else {
                    data = BTEngine.getInstance().fetchMagnet(uri, 90, true);
                }
                if (state == TransferState.CANCELED) {
                    return;
                }
                if (data != null) {
                    try {
                        downloadTorrent(data, parsePeers(uri));
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
