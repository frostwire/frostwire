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

package com.frostwire.android.gui.transfers;

import android.net.Uri;
import android.util.Log;
import com.frostwire.android.LollipopFileSystem;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.mp3.ID3Wrapper;
import com.frostwire.mp3.ID3v1Tag;
import com.frostwire.mp3.ID3v23Tag;
import com.frostwire.mp3.Mp3File;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.http.HttpClient;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class SoundcloudDownload extends TemporaryDownloadTransfer<SoundcloudSearchResult> {

    private static final String TAG = "FW.SoundcloudDownload";

    private static final long MAX_ACCEPTABLE_SOUNDCLOUD_FILESIZE_FOR_COVERART_FETCH = 20971520; //20MB

    private final TransferManager manager;

    public SoundcloudDownload(TransferManager manager, SoundcloudSearchResult sr) {
        this.manager = manager;
        this.sr = sr;
    }

    @Override
    public String getDisplayName() {
        return sr.getDisplayName();
    }

    @Override
    public String getStatus() {
        return delegate != null ? delegate.getStatus() : "";
    }

    @Override
    public int getProgress() {
        if (delegate != null && delegate.isComplete()) {
            return 100;
        }
        return delegate != null ? delegate.getProgress() : 0;
    }

    @Override
    public long getSize() {
        return delegate != null ? delegate.getSize() : 0;
    }

    @Override
    public Date getDateCreated() {
        return delegate != null ? delegate.getDateCreated() : new Date();
    }

    @Override
    public long getBytesReceived() {
        return delegate != null ? delegate.getBytesReceived() : 0;
    }

    @Override
    public long getBytesSent() {
        return delegate != null ? delegate.getBytesSent() : 0;
    }

    @Override
    public long getDownloadSpeed() {
        return delegate != null ? delegate.getDownloadSpeed() : 0;
    }

    @Override
    public long getUploadSpeed() {
        return delegate != null ? delegate.getUploadSpeed() : 0;
    }

    @Override
    public long getETA() {
        return delegate != null ? delegate.getETA() : 0;
    }

    @Override
    public boolean isComplete() {
        //FIXME: we do this differently here because SoundCloud downloads may not have
//the same number of bytes as expected at the end, or maybe we don't
//even know exactly how many bytes to expect in the first place.
//the fix should probably be calculating this number correctly.
//Suggestion: maybe look at the Content-length HTTP header for a size
//if sound cloud sends this when the download starts and update the
//link.getSize() value with this number as it becomes known.
        return delegate != null && (delegate.getStatusCode() == HttpDownload.STATUS_COMPLETE ||
                delegate.getStatusCode() == HttpDownload.STATUS_ERROR);
    }

    @Override
    public List<TransferItem> getItems() {
        return Collections.emptyList();
    }

    @Override
    public void cancel() {
        if (delegate != null) {
            delegate.cancel();
        }
        manager.remove(this);
    }

    @Override
    public boolean isDownloading() {
        return delegate != null ? delegate.isDownloading() : false;
    }

    @Override
    public void cancel(boolean deleteData) {
        if (delegate != null) {
            delegate.cancel(deleteData);
        }
        manager.remove(this);
    }

    public void start() {
        try {
            final HttpDownloadLink link = buildDownloadLink();
            if (link != null) {
                delegate = new HttpDownload(manager, Platforms.temp(), link) {
                    // we could have it here already, but lately SC does not report this number in search results anymore.
                    public long size = link.getSize();

                    @Override
                    public long getSize() {
                        return size;
                    }

                    @Override
                    protected void setSize(long s) {
                        this.size = s;
                    }
                };
                delegate.setListener(new HttpDownloadListener() {

                    @Override
                    public void onComplete(HttpDownload download) {
                        downloadAndUpdateCoverArt(download.getSavePath());

                        FileSystem fs = Platforms.get().fileSystem();
                        if (fs instanceof LollipopFileSystem) {
                            Uri uri = ((LollipopFileSystem) fs).getDocumentUri(Platforms.data());
                            if (uri != null) {
                                safComplete(fs, download.getSavePath());
                            } else {
                                classicComplete(download);
                            }
                        } else {
                            classicComplete(download);
                        }
                    }

                    @Override
                    public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
                        if (headerFields != null && headerFields.containsKey("Content-Length")) {
                            String lengthStr = headerFields.get("Content-Length").get(0);
                            delegate.setSize(Long.valueOf(lengthStr));
                        }
                    }
                });
                delegate.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting youtube download", e);
        }
    }

    private void classicComplete(HttpDownload download) {
        moveFile(download.getSavePath());
        Librarian.instance().scan(getSavePath().getAbsoluteFile());
        File savedFile = getSavePath(); //the update path after the file was moved.
        String hash = String.valueOf(getDisplayName().hashCode());
        Engine.instance().notifyDownloadFinished(getDisplayName(), savedFile, hash);
    }

    private void safComplete(final FileSystem fs, final File file) {
        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File finalFile = new File(Platforms.data(), file.getName());
                    if (fs.copy(file, finalFile)) {
                        SoundcloudDownload.this.savePath = finalFile;
                    } else {
                        // TODO: do something here
                        // error
                        return;
                    }

                    String hash = String.valueOf(getDisplayName().hashCode());
                    Engine.instance().notifyDownloadFinished(getDisplayName(), finalFile, hash);

                    file.delete();

                    Librarian.instance().scan(Uri.fromFile(finalFile.getAbsoluteFile()));
                } catch (Throwable e) {
                    e.printStackTrace();
                    // TODO: do something here
                    // error
                }
            }
        });
    }

    @Override
    public File previewFile() {
        if (isComplete()) {
            return getSavePath();
        } else {
            return delegate != null ? delegate.getSavePath() : null;
        }
    }

    private void moveFile(File savePath) {
        File dataDir = Platforms.data();
        if (!YouTubeDownload.ensureDirectoryExits(dataDir)) {
            // error
            return;
        }
        File finalFile = YouTubeDownload.buildFile(dataDir, savePath.getName());
        try {
            FileUtils.moveFile(savePath, finalFile);
            this.savePath = finalFile;
        } catch (IOException e) {
            e.printStackTrace();
            this.savePath = savePath;
        }
    }

    private void downloadAndUpdateCoverArt(File tempFile) {
        if (tempFile != null && tempFile.exists() && tempFile.length() <= MAX_ACCEPTABLE_SOUNDCLOUD_FILESIZE_FOR_COVERART_FETCH) {
            byte[] coverArtBytes = downloadCoverArt();
            if (coverArtBytes != null && coverArtBytes.length > 0) {
                //Log.v(TAG, "cover art array length (@" + coverArtBytes.hashCode() + "): " + coverArtBytes.length);
                String tempPath = tempFile.getAbsolutePath() + ".tmp";
                File tempTemp = new File(tempPath);
                if (tempFile.renameTo(tempTemp)) {
                    boolean r = setAlbumArt(coverArtBytes, tempPath, tempFile.getAbsolutePath());
                    if (!r) {
                        tempTemp.renameTo(tempFile);
                    } else {
                        tempTemp.delete();
                    }
                }
            }
        }
    }

    private byte[] downloadCoverArt() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Log.v(TAG, "thumbnail url: " + sr.getThumbnailUrl());
            HttpDownload.simpleHTTP(sr.getThumbnailUrl(), baos, 3000);
            return baos.toByteArray();
        } catch (Throwable e) {
            Log.e(TAG, "Error downloading SoundCloud cover art.", e);
        }
        return null;
    }

    private HttpDownloadLink buildDownloadLink() {
        return new SoundcloudDownloadLink(sr);
    }

    private boolean setAlbumArt(byte[] imageBytes, String mp3Filename, String mp3outputFilename) {
        try {
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
            e.printStackTrace();
            return false;
        }
    }
}
