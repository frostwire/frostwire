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

package com.frostwire.android.gui.transfers;

import android.content.Context;
import android.media.MediaScannerConnection;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.frostclick.Slide;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.create_torrent;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.file_storage;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.set_piece_hashes_listener;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Objects;

/**
 * @author aldenml
 * @author gubatron
 */
public class UIHttpDownload extends HttpDownload {

    private final TransferManager manager;
    private final Logger LOG = Logger.getLogger(UIHttpDownload.class);

    public UIHttpDownload(TransferManager manager, HttpSearchResult sr) {
        super(convert(sr));
        this.manager = manager;
    }

    public UIHttpDownload(TransferManager manager, Slide slide) {
        super(convert(slide));
        this.manager = manager;
    }

    @Override
    public void remove(boolean deleteData) {
        super.remove(deleteData);

        manager.remove(this);
    }

    @Override
    protected void onComplete() {
        manager.incrementDownloadsToReview();
        Engine.instance().notifyDownloadFinished(getDisplayName(), savePath);
        
        // Seed the finished HTTP download if seeding is enabled
        seedFinishedHttpDownloadIfEnabled();
    }
    
    private void seedFinishedHttpDownloadIfEnabled() {
        ConfigurationManager cm = ConfigurationManager.instance();
        
        // Only proceed if seeding is enabled
        if (!cm.isSeedFinishedTorrents()) {
            return;
        }
        
        // Check WiFi-only restriction
        if (cm.isSeedingEnabledOnlyForWifi() && !NetworkManager.instance().isDataWIFIUp()) {
            return;
        }
        
        // Don't seed if mobile data savings are on
        if (manager.isMobileAndDataSavingsOn()) {
            return;
        }
        
        // Create FWFileDescriptor for the downloaded file
        if (savePath != null && savePath.exists()) {
            FWFileDescriptor fd = createFileDescriptor(savePath);
            if (fd != null) {
                // Use background thread to create torrent and seed it
                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> buildTorrentAndSeedIt(fd));
            }
        }
    }
    
    private FWFileDescriptor createFileDescriptor(File file) {
        if (!file.exists()) {
            return null;
        }
        
        FWFileDescriptor fd = new FWFileDescriptor();
        fd.filePath = file.getAbsolutePath();
        fd.fileSize = file.length();
        fd.dateModified = file.lastModified();
        fd.dateAdded = System.currentTimeMillis();
        fd.mime = MimeDetector.getMimeType(FilenameUtils.getExtension(file.getName()));
        fd.fileType = Constants.FILE_TYPE_DOCUMENTS; // Default to documents
        fd.title = getDisplayName();
        fd.deletable = true;
        
        return fd;
    }
    
    private void buildTorrentAndSeedIt(final FWFileDescriptor fd) {
        try {
            File file = new File(fd.filePath);
            File saveDir = file.getParentFile();
            file_storage fs = new file_storage();
            libtorrent.add_files(fs, file.getAbsolutePath());
            fs.set_name(file.getName());
            create_torrent ct = new create_torrent(fs);
            ct.set_creator("FrostWire " + Constants.FROSTWIRE_VERSION_STRING + " build " + Constants.FROSTWIRE_BUILD);
            ct.set_priv(false);
            final error_code ec = new error_code();
            libtorrent.set_piece_hashes_ex(ct, Objects.requireNonNull(saveDir).getAbsolutePath(), new set_piece_hashes_listener(), ec);
            final byte[] torrent_bytes = new Entry(ct.generate()).bencode();
            final TorrentInfo tinfo = TorrentInfo.bdecode(torrent_bytes);
            // Create the TorrentHandle object and add it to the libtorrent session
            BTEngine.getInstance().download(tinfo, saveDir, new boolean[]{true}, null, manager.isDeleteStartedTorrentEnabled());
            LOG.info("Successfully created and started seeding torrent for HTTP download: " + fd.filePath);
        } catch (Throwable e) {
            LOG.error("Error creating torrent for HTTP download seed: " + fd.filePath, e);
        }
    }

    @Override
    protected void moveAndComplete(File src, File dst) {
        super.moveAndComplete(src, dst);
        if (SystemUtils.hasAndroid11OrNewer()) {
            Context context = SystemUtils.getApplicationContext();
            if (context == null) {
                return;
            }
            MediaScannerConnection.scanFile(context,
                    new String[]{dst.getAbsolutePath()},
                    new String[]{MimeDetector.getMimeType(FilenameUtils.getExtension(dst.getName()))},
                    (path, uri) -> LOG.info("UIHttpDownload::moveAndComplete() -> mediaScan complete on " + dst));
        }
    }

    private static Info convert(HttpSearchResult sr) {
        return new Info(sr.getDownloadUrl(), sr.getFilename(), sr.getDisplayName(), sr.getSize());
    }

    private static Info convert(Slide slide) {
        return new Info(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL,
                FilenameUtils.getName(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL),
                slide.title,
                slide.size);
    }
}
