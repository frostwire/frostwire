/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.mp4.Box;
import com.frostwire.mp4.IsoFile;
import com.frostwire.mp4.Mp4Demuxer;
import com.frostwire.mp4.Mp4Info;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.search.youtube.YouTubeExtractor.LinkInfo;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpClientListener;
import com.limegroup.gnutella.gui.iTunesMediator;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.settings.iTunesSettings;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.limewire.util.OSUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author gubatron
 * @author aldenml
 */
public class YouTubeDownload extends HttpBTDownload {
    private static final Executor YOUTUBE_THREAD_POOL = Executors.newFixedThreadPool(6);
    private final YouTubeCrawledSearchResult sr;
    private final DownloadType downloadType;
    private final File tempVideo;
    private final File tempAudio;

    YouTubeDownload(YouTubeCrawledSearchResult sr) {
        super(sr.getFilename(), sr.getSize());
        this.sr = sr;
        this.downloadType = buildDownloadType(sr);
        String filename = sr.getFilename();
        tempVideo = buildTempFile(FilenameUtils.getBaseName(filename), "m4v");
        tempAudio = buildTempFile(FilenameUtils.getBaseName(filename), "m4a");
        start();
    }

    @Override
    HttpClientListener createHttpClientListener() {
        return new HttpDownloadListenerImpl(this);
    }

    private DownloadType buildDownloadType(YouTubeCrawledSearchResult sr) {
        DownloadType dt;
        if (sr.getVideo() != null && sr.getAudio() == null) {
            dt = DownloadType.VIDEO;
        } else if (sr.getVideo() != null && sr.getAudio() != null) {
            dt = DownloadType.DASH;
        } else if (sr.getVideo() == null && sr.getAudio() != null) {
            dt = DownloadType.DEMUX;
        } else {
            throw new IllegalArgumentException("Not track specified");
        }
        return dt;
    }

    @Override
    public String getName() {
        return completeFile.getName();
    }

    @Override
    public String getDisplayName() {
        return sr.getDisplayName();
    }

    @Override
    public boolean isPausable() {
        return false;
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public String getHash() {
        return sr.getDownloadUrl();
    }

    private void start() {
        if (downloadType == DownloadType.DEMUX) {
            start(sr.getAudio(), tempAudio);
        } else {
            start(sr.getVideo(), tempVideo);
        }
    }

    private void start(final LinkInfo inf, final File temp) {
        state = TransferState.WAITING;

        YOUTUBE_THREAD_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    httpClient.save(inf.link, temp, false);
                } catch (IOException e) {
                    e.printStackTrace();
                    httpClientListener.onError(httpClient, e);
                }
            }
        });
    }

    @Override
    void cleanupIncomplete() {
        cleanupFile(tempVideo);
        cleanupFile(tempAudio);
    }

    private final class HttpDownloadListenerImpl implements HttpClientListener {
        private final YouTubeDownload dl;

        HttpDownloadListenerImpl(YouTubeDownload youTubeDownload) {
            dl = youTubeDownload;
        }

        @Override
        public void onError(HttpClient client, Throwable e) {
            state = TransferState.ERROR;
            cleanup();
        }

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            if (!state.equals(TransferState.PAUSING) && !state.equals(TransferState.CANCELING)) {
                bytesReceived += length;
                updateAverageDownloadSpeed();
                state = TransferState.DOWNLOADING;
            }
        }

        private void removeUdta(File mp4) throws IOException {
            RandomAccessFile f = new RandomAccessFile(mp4, "rw");
            try {
                IsoFile.free(f, Box.udta, ByteBuffer.allocate(100 * 1024));
            } finally {
                IOUtils.closeQuietly(f);
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            if (downloadType == DownloadType.VIDEO) {
                try {
                    removeUdta(tempVideo);
                } catch (IOException e) {
                    e.printStackTrace();
                    state = TransferState.ERROR_MOVING_INCOMPLETE;
                    cleanupIncomplete();
                    return;
                }
                boolean renameTo = tempVideo.renameTo(completeFile);

                if (!renameTo) {
                    if (!MediaPlayer.instance().isThisBeingPlayed(tempVideo)) {
                        state = TransferState.ERROR_MOVING_INCOMPLETE;
                    } else {
                        boolean copiedTo = HttpBTDownload.copyPlayingTemp(tempVideo, completeFile);
                        if (!copiedTo) {
                            state = TransferState.ERROR_MOVING_INCOMPLETE;
                        } else {
                            state = TransferState.FINISHED;
                        }
                        cleanupIncomplete();
                    }
                } else {
                    state = TransferState.FINISHED;
                    cleanupIncomplete();
                }
            } else if (downloadType == DownloadType.DEMUX) {
                try {
                    Mp4Demuxer.audio(tempAudio.getAbsoluteFile(), completeFile.getAbsoluteFile(), buildMp4Info(true), null);

                    if (!completeFile.exists()) {
                        state = TransferState.ERROR_MOVING_INCOMPLETE;
                    } else {
                        state = TransferState.FINISHED;
                        cleanupIncomplete();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    state = TransferState.ERROR_MOVING_INCOMPLETE;
                    cleanupIncomplete();
                }
            } else if (downloadType == DownloadType.DASH) {
                if (tempVideo.exists() && !tempAudio.exists()) {
                    start(sr.getAudio(), tempAudio);
                } else if (tempVideo.exists() && tempAudio.exists()) {
                    try {
                        Mp4Demuxer.muxFragments(tempVideo.getAbsoluteFile(), tempAudio.getAbsoluteFile(), completeFile.getAbsoluteFile(), buildMp4Info(false), null);

                        if (!completeFile.exists()) {
                            state = TransferState.ERROR_MOVING_INCOMPLETE;
                        } else {
                            state = TransferState.FINISHED;
                            cleanupIncomplete();
                        }

                    } catch (Exception e) {
                        state = TransferState.ERROR_MOVING_INCOMPLETE;
                        cleanupIncomplete();
                    }
                } else {
                    state = TransferState.ERROR_MOVING_INCOMPLETE;
                    cleanupIncomplete();
                }
            } else {
                // warning!!! if this point is reached review the logic
                state = TransferState.ERROR_MOVING_INCOMPLETE;
                cleanupIncomplete();
            }

            if (completeFile.exists()) {
                if (SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
                    BittorrentDownload.RendererHelper.onSeedTransfer(dl, false);
                    // TODO: Rich DHT announcement.
                }

                if (iTunesSettings.ITUNES_SUPPORT_ENABLED.getValue() && !iTunesMediator.instance().isScanned(completeFile)) {
                    if ((OSUtils.isMacOSX() || OSUtils.isWindows())) {
                        iTunesMediator.instance().scanForSongs(completeFile);
                    }
                }
            }
        }

        @Override
        public void onCancel(HttpClient client) {
            if (state.equals(TransferState.CANCELING)) {
                cleanup();
                state = TransferState.CANCELED;
            } else if (state.equals(TransferState.PAUSING)) {
                state = TransferState.PAUSED;
            } else {
                state = TransferState.CANCELED;
            }
        }

        @Override
        public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
        }
    }

    private enum DownloadType {
        VIDEO, DASH, DEMUX
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof YouTubeDownload && sr.getDownloadUrl().equals(((YouTubeDownload) obj).sr.getDownloadUrl());
    }

    private Mp4Info buildMp4Info(boolean audio) {
        String title = sr.getDisplayName();
        String author = sr.getAuthor();
        String source = "YouTube.com";

        String jpgUrl = sr.getVideo() != null ? sr.getVideo().thumbnails.normal : null;
        if (jpgUrl == null && sr.getAudio() != null) {
            jpgUrl = sr.getAudio() != null ? sr.getAudio().thumbnails.normal : null;
        }

        byte[] jpg = jpgUrl != null ? HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(jpgUrl) : null;

        if (audio) {
            return Mp4Info.audio(title, author, source, jpg);
        } else {
            return Mp4Info.avc(title, author, source, jpg);
        }
    }

    @Override
    public File getPreviewFile() {
        if (isCompleted()) {
            return completeFile;
        } else {
            if (tempVideo.exists()) {
                return tempVideo;
            }
            if (tempAudio.exists()) {
                return tempAudio;
            }
        }
        return null;
    }
}
