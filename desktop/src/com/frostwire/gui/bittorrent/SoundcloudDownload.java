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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.CopyrightLicenseBroker;
import com.frostwire.bittorrent.PaymentOptions;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.OSUtils;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author gubatron
 * @author aldenml
 */
public class SoundcloudDownload implements BTDownload {

    private static final Executor SOUNDCLOUD_THREAD_POOL = Executors.newFixedThreadPool(6);

    private static final int SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS = 1000;

    private final SoundcloudSearchResult sr;

    private final File completeFile;
    private final File tempAudio;

    private final HttpClient httpClient;
    private final HttpClientListener httpClientListener;
    private final Date dateCreated;

    private long size;
    private long bytesReceived;
    private TransferState state;
    private long averageSpeed; // in bytes

    // variables to keep the download rate of file transfer
    private long speedMarkTimestamp;
    private long totalReceivedSinceLastSpeedStamp;
    private boolean deleteDataWhenRemoved;

    public SoundcloudDownload(SoundcloudSearchResult sr) {
        this.sr = sr;
        this.size = sr.getSize();

        String filename = sr.getFilename();

        completeFile = buildFile(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), filename);
        tempAudio = buildTempFile(FilenameUtils.getBaseName(filename), "mp3");

        bytesReceived = 0;
        dateCreated = new Date();

        httpClientListener = new HttpDownloadListenerImpl();

        httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
        httpClient.setListener(httpClientListener);

        start();
    }

    @Override
    public long getSize() {
        if (isComplete() && getSaveLocation().exists()) {
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
    public boolean isResumable() {
        return false;
    }

    @Override
    public boolean isPausable() {
        return false;
    }

    @Override
    public boolean isCompleted() {
        return isComplete();
    }

    @Override
    public TransferState getState() {
        return state;
    }

    @Override
    public void remove() {
        if (state != TransferState.FINISHED) {
            state = TransferState.CANCELING;
            httpClient.cancel();
        }

        if (deleteDataWhenRemoved) {
            getSaveLocation().delete();
        }
    }

    private void cleanup() {
        cleanupIncomplete();
        cleanupComplete();
    }

    @Override
    public void pause() {
        if (state != TransferState.FINISHED) {
            state = TransferState.CANCELING;
            httpClient.cancel();
        }
    }

    @Override
    public File getSaveLocation() {
        return completeFile;
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public int getProgress() {
        if (isComplete()) {
            return 100;
        }

        if (size <= 0) {
            return -1;
        }

        int progress = (int) ((bytesReceived * 100) / size);

        return Math.min(100, progress);
    }

    @Override
    public long getBytesReceived() {
        if (isComplete() && getSaveLocation().exists()) {
            bytesReceived = getSaveLocation().length();
        }

        return bytesReceived;
    }

    @Override
    public long getBytesSent() {
        return 0;
    }

    @Override
    public double getDownloadSpeed() {
        double result = 0;
        if (state == TransferState.DOWNLOADING) {
            result = averageSpeed / 1000;
        }
        return result;
    }

    @Override
    public double getUploadSpeed() {
        return 0;
    }

    @Override
    public long getETA() {
        if (size > 0) {
            long speed = averageSpeed;
            return speed > 0 ? (size - getBytesReceived()) / speed : -1;
        } else {
            return -1;
        }
    }

    @Override
    public String getPeersString() {
        return "";
    }

    @Override
    public String getSeedsString() {
        return "";
    }

    @Override
    public String getHash() {
        return sr.getDownloadUrl();
    }

    @Override
    public String getSeedToPeerRatio() {
        return "";
    }

    @Override
    public String getShareRatio() {
        return "";
    }

    @Override
    public boolean isPartialDownload() {
        return false;
    }

    @Override
    public Date getDateCreated() {
        return dateCreated;
    }

    private void start() {
        start(tempAudio);
    }

    private void start(final File temp) {
        state = TransferState.WAITING;

        SOUNDCLOUD_THREAD_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    httpClient.save(sr.getDownloadUrl(), temp, false);
                } catch (Throwable e) {
                    e.printStackTrace();
                    httpClientListener.onError(httpClient, e);
                }
            }
        });
    }

    private void cleanupFile(File f) {
        if (f.exists()) {
            boolean delete = f.delete();
            if (!delete) {
                f.deleteOnExit();
            }
        }
    }

    private void cleanupIncomplete() {
        cleanupFile(tempAudio);
    }

    private void cleanupComplete() {
        cleanupFile(completeFile);
    }

    /**
     * files are saved with (1), (2),... if there's one with the same name already.
     */
    private static File buildFile(File savePath, String name) {
        String baseName = FilenameUtils.getBaseName(name);
        String ext = FilenameUtils.getExtension(name);

        File f = new File(savePath, name);
        int i = 1;
        while (f.exists() && i < Integer.MAX_VALUE) {
            f = new File(savePath, baseName + " (" + i + ")." + ext);
            i++;
        }
        return f;
    }

    private static File getIncompleteFolder() {
        File incompleteFolder = new File(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue().getParentFile(), "Incomplete");
        if (!incompleteFolder.exists()) {
            incompleteFolder.mkdirs();
        }
        return incompleteFolder;
    }

    private static File buildTempFile(String name, String ext) {
        return new File(getIncompleteFolder(), name + "." + ext);
    }

    public boolean isComplete() {
        if (bytesReceived > 0) {
            return bytesReceived == size || state == TransferState.FINISHED;
        } else {
            return false;
        }
    }

    private void updateAverageDownloadSpeed() {
        long now = System.currentTimeMillis();

        if (isComplete()) {
            averageSpeed = 0;
            speedMarkTimestamp = now;
            totalReceivedSinceLastSpeedStamp = 0;
        } else if (now - speedMarkTimestamp > SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS) {
            averageSpeed = ((bytesReceived - totalReceivedSinceLastSpeedStamp) * 1000) / (now - speedMarkTimestamp);
            speedMarkTimestamp = now;
            totalReceivedSinceLastSpeedStamp = bytesReceived;
        }
    }

    private final class HttpDownloadListenerImpl implements HttpClientListener {
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
                            boolean copiedTo = copyPlayingTemp(tempAudio, completeFile);
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
                SoundcloudDownload.this.size = Long.valueOf(lengthStr);
            }
        }
    }

    private boolean copyPlayingTemp(File temp, File dest) {
        boolean r = false;
        System.out.println(temp);

        try {
            FileUtils.copyFile(temp, dest);
            r = true;
        } catch (Throwable e) {
            e.printStackTrace();
            r = false;
        }

        return r;
    }

    @Override
    public void setDeleteTorrentWhenRemove(boolean deleteTorrentWhenRemove) {
    }

    @Override
    public void setDeleteDataWhenRemove(boolean deleteDataWhenRemove) {
        this.deleteDataWhenRemoved = deleteDataWhenRemove;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SoundcloudDownload && sr.getDownloadUrl().equals(((SoundcloudDownload) obj).sr.getDownloadUrl());
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
    public PaymentOptions getPaymentOptions() {
        return null;
    }

    @Override
    public CopyrightLicenseBroker getCopyrightLicenseBroker() {
        return null;
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    @Override
    public File getPreviewFile() {
        if (isComplete()) {
            return completeFile;
        } else {
            return tempAudio;
        }
    }
}
