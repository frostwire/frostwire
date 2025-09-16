/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.mp3.ID3Wrapper;
import com.frostwire.mp3.ID3v1Tag;
import com.frostwire.mp3.ID3v23Tag;
import com.frostwire.mp3.Mp3File;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpClientListener;
import com.limegroup.gnutella.gui.iTunesMediator;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.settings.iTunesSettings;
import org.apache.commons.io.FilenameUtils;
import com.frostwire.util.OSUtils;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author gubatron
 * @author aldenml
 */
public class SoundcloudDownload extends HttpBTDownload {
    private static final Executor SOUNDCLOUD_THREAD_POOL = Executors.newFixedThreadPool(6);
    private final SoundcloudSearchResult sr;
    private final File tempAudio;

    SoundcloudDownload(SoundcloudSearchResult sr) {
        super(sr.getFilename(), sr.getSize());
        this.sr = sr;
        String filename = sr.getFilename();
        tempAudio = buildTempFile(FilenameUtils.getBaseName(filename), "mp3");
        start();
    }

    @Override
    HttpClientListener createHttpClientListener() {
        return new HttpDownloadListenerImpl(this);
    }

    @Override
    public double getSize() {
        if (isCompleted() && getSaveLocation().exists()) {
            return getSaveLocation().length();
        }
        return size;
    }

    @Override
    public String getDisplayName() {
        return sr.getDisplayName();
    }

    @Override
    public String getName() {
        return sr.getFilename();
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public String getHash() {
        return sr.getHash();
    }

    private void start() {
        start(tempAudio);
    }

    private void start(final File temp) {
        state = TransferState.WAITING;
        SOUNDCLOUD_THREAD_POOL.execute(() -> {
            String downloadUrl = null;
            try {
                downloadUrl = sr.getDownloadUrl();
                httpClient.save(downloadUrl, temp, false);
            } catch (Throwable e) {
                System.err.println("URL at issue: [" + downloadUrl + "]");
                e.printStackTrace();
                httpClientListener.onError(httpClient, e);
            }
        });
    }

    @Override
    void cleanupIncomplete() {
        cleanupFile(tempAudio);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SoundcloudDownload && sr.getHash().equals(((SoundcloudDownload) obj).sr.getHash());
    }

    private boolean setAlbumArt(String mp3Filename, String mp3outputFilename) {
        try {
            byte[] imageBytes = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(sr.getThumbnailUrl());
            Mp3File mp3 = new Mp3File(mp3Filename);
            ID3Wrapper newId3Wrapper = new ID3Wrapper(new ID3v1Tag(), new ID3v23Tag());
            newId3Wrapper.setAlbum(sr.getUsername() + ": " + sr.getDisplayName() + " via SoundCloud.com");
            newId3Wrapper.setArtist(sr.getUsername());
            newId3Wrapper.setTitle(sr.getDisplayName());
            newId3Wrapper.setAlbumImage(imageBytes, "image/jpg");
            newId3Wrapper.setUrl(sr.getDetailsUrl());
            newId3Wrapper.getId3v2Tag().setPadding(true);
            mp3.setId3v1Tag(newId3Wrapper.getId3v1Tag());
            mp3.setId3v2Tag(newId3Wrapper.getId3v2Tag());
            mp3.save(mp3outputFilename);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public File getPreviewFile() {
        if (isCompleted()) {
            return completeFile;
        } else {
            return tempAudio;
        }
    }

    private final class HttpDownloadListenerImpl implements HttpClientListener {
        private final SoundcloudDownload dl;

        HttpDownloadListenerImpl(SoundcloudDownload soundcloudDownload) {
            dl = soundcloudDownload;
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

        @Override
        public void onComplete(HttpClient client) {
            if (state != TransferState.REDIRECTING) {
                if (!setAlbumArt(tempAudio.getAbsolutePath(), completeFile.getAbsolutePath())) {
                    boolean renameTo = tempAudio.renameTo(completeFile);
                    if (!renameTo) {
                        if (!MediaPlayer.instance().isThisBeingPlayed(tempAudio)) {
                            state = TransferState.ERROR_MOVING_INCOMPLETE;
                            cleanupIncomplete();
                            return;
                        } else {
                            boolean copiedTo = HttpBTDownload.copyPlayingTemp(tempAudio, completeFile);
                            if (!copiedTo) {
                                state = TransferState.ERROR_MOVING_INCOMPLETE;
                                cleanupIncomplete();
                                return;
                            }
                        }
                        state = TransferState.ERROR_MOVING_INCOMPLETE;
                        cleanupIncomplete();
                        return;
                    }
                }
                state = TransferState.FINISHED;
                if (SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
                    BittorrentDownload.RendererHelper.onSeedTransfer(dl, false);
                    // TODO: Rich DHT announcement.
                }
                if (iTunesSettings.ITUNES_SUPPORT_ENABLED.getValue() && !iTunesMediator.instance().isScanned(completeFile)) {
                    if ((OSUtils.isMacOSX() || OSUtils.isWindows())) {
                        iTunesMediator.instance().scanForSongs(completeFile);
                    }
                }
                cleanupIncomplete();
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
            if (headerFields != null && headerFields.containsKey("Content-Length")) {
                String lengthStr = headerFields.get("Content-Length").get(0);
                SoundcloudDownload.this.size = Long.parseLong(lengthStr);
            }
        }
    }
}
