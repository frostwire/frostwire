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

import android.content.Context;
import android.media.MediaScannerConnection;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.android.util.TorrentUtils;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.frostclick.Slide;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Map;

/**
 * @author aldenml
 * @author gubatron
 */
public class UIHttpDownload extends HttpDownload {

    private final TransferManager manager;
    private final Logger LOG = Logger.getLogger(UIHttpDownload.class);
    /** Hidden DASH mux sibling (m4a/AAC) for video-only Telluride rows; null otherwise. */
    private String muxAudioUrl;
    private String muxAudioExt;
    private java.util.Map<String, String> muxAudioHeaders;

    public UIHttpDownload(TransferManager manager, HttpSearchResult sr) {
        super(convert(sr));
        this.manager = manager;
        TellurideSearchResult telluride = (sr instanceof TellurideSearchResult)
                && ((TellurideSearchResult) sr).needsAudioMux() ? (TellurideSearchResult) sr : null;
        this.muxAudioUrl = telluride != null ? telluride.getMuxAudioUrl() : null;
        this.muxAudioExt = telluride != null ? telluride.getMuxAudioExt() : null;
        this.muxAudioHeaders = telluride != null ? telluride.getMuxAudioHeaders() : null;
    }

    public UIHttpDownload(TransferManager manager, CompositeFileSearchResult sr) {
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
        muxDashAudioIfNeeded();
        manager.incrementDownloadsToReview();
        Engine.instance().notifyDownloadFinished(getDisplayName(), savePath);

        // Seed the finished HTTP download if seeding is enabled
        TorrentUtils.seedFinishedHttpDownloadIfEnabled(savePath, getDisplayName(), Constants.FILE_TYPE_DOCUMENTS, manager, this);
    }

    /**
     * DASH video-only results play silent unless the hidden m4a sibling is
     * fetched and muxed in. Runs on the transfer thread before notify/seed;
     * the media scan below re-runs so the gallery sees final metadata.
     * On any failure the silent video is kept (status quo, never worse).
     */
    private void muxDashAudioIfNeeded() {
        if (muxAudioUrl == null || muxAudioUrl.isEmpty() || savePath == null) {
            return;
        }
        File video = savePath.getAbsoluteFile();
        if (!video.exists()) {
            return;
        }
        String suffix = (muxAudioExt != null && !muxAudioExt.isEmpty()) ? muxAudioExt : "m4a";
        File audioTmp = new File(video.getParentFile(), ".mux-" + video.getName() + "." + suffix);
        File mergedTmp = new File(video.getParentFile(), ".mux-" + video.getName() + ".merged.mp4");
        try {
            fetchSiblingAudio(muxAudioUrl, muxAudioHeaders, audioTmp);
            String videoExt = FilenameUtils.getExtension(video.getName());
            boolean muxed = com.frostwire.search.telluride.DashMux.muxIfSupported(
                    video, audioTmp, videoExt, suffix, mergedTmp);
            if (!muxed) {
                LOG.warn("No muxer for video ." + videoExt + " + audio ." + suffix
                        + "; keeping silent video " + video.getName());
                return;
            }
            java.nio.file.Files.move(mergedTmp.toPath(), video.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Muxed DASH audio into " + video.getAbsolutePath());
            rescanMedia(video);
        } catch (Throwable t) {
            LOG.warn("Could not mux DASH audio into " + video.getName()
                    + " (keeping silent video): " + t.getMessage());
        } finally {
            deleteQuietly(audioTmp);
            deleteQuietly(mergedTmp);
        }
    }

    private static void deleteQuietly(File f) {
        try {
            if (f != null && f.exists() && !f.delete()) {
                f.deleteOnExit();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fetchSiblingAudio(String url, java.util.Map<String, String> headers, File dst)
            throws java.io.IOException {
        java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "FrostWire");
        if (headers != null) {
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = conn.getInputStream();
            out = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
            conn.disconnect();
        }
        if (!dst.exists() || dst.length() == 0) {
            throw new java.io.IOException("sibling audio download came back empty");
        }
    }

    private void rescanMedia(File dst) {
        try {
            if (!SystemUtils.hasAndroid11OrNewer()) {
                return;
            }
            Context context = SystemUtils.getApplicationContext();
            if (context == null) {
                return;
            }
            MediaScannerConnection.scanFile(context,
                    new String[]{dst.getAbsolutePath()},
                    new String[]{MimeDetector.getMimeType(FilenameUtils.getExtension(dst.getName()))},
                    (path, uri) -> LOG.info("UIHttpDownload::muxDashAudioIfNeeded() -> mediaScan refreshed " + dst));
        } catch (Throwable t) {
            LOG.warn("rescanMedia after mux failed: " + t.getMessage());
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
        downloadThumbnailSidecar(dst);
    }

    private void downloadThumbnailSidecar(File audioFile) {
        String thumbUrl = info.thumbnailUrl();
        if (thumbUrl == null || thumbUrl.isEmpty()) {
            return;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                File artFile = new File(audioFile.getParent(), "." + audioFile.getName() + ".art");
                if (artFile.exists()) {
                    return;
                }
                java.io.InputStream in = null;
                java.io.FileOutputStream out = null;
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(thumbUrl).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("User-Agent", "FrostWire");
                    in = conn.getInputStream();
                    out = new java.io.FileOutputStream(artFile);
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                    LOG.info("downloadThumbnailSidecar: saved " + artFile.getAbsolutePath());
                } finally {
                    if (in != null) try { in.close(); } catch (Exception ignored) {}
                    if (out != null) try { out.close(); } catch (Exception ignored) {}
                }
            } catch (Throwable t) {
                LOG.warn("downloadThumbnailSidecar failed for " + audioFile.getName() + ": " + t.getMessage());
            }
        });
    }

    private static Info convert(HttpSearchResult sr) {
        Map<String, String> headers = (sr instanceof TellurideSearchResult) ?
                ((TellurideSearchResult) sr).getHttpHeaders() : null;
        String thumbnailUrl = sr.getThumbnailUrl();
        return new Info(sr.getDownloadUrl(), sr.getFilename(), sr.getDisplayName(), sr.getSize(), headers, thumbnailUrl);
    }

    private static Info convert(CompositeFileSearchResult sr) {
        String downloadUrl = sr.getHttpDownloadUrl().orElse(null);
        return new Info(downloadUrl, sr.getFilename(), sr.getDisplayName(), sr.getSize(), null, sr.getThumbnailUrl());
    }

    private static Info convert(Slide slide) {
        return new Info(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL,
                FilenameUtils.getName(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL),
                slide.title,
                slide.size);
    }
}
