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

package com.frostwire.transfers;

import com.frostwire.util.Logger;
import com.frostwire.mp4.Box;
import com.frostwire.mp4.IsoFile;
import com.frostwire.mp4.Mp4Demuxer;
import com.frostwire.mp4.Mp4Info;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.search.youtube.YouTubeExtractor;
import com.frostwire.util.HttpClientFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
public class YouTubeDownload extends BaseHttpDownload {

    private static final Logger LOG = Logger.getLogger(YouTubeDownload.class);

    private final YouTubeCrawledSearchResult sr;
    private final DownloadType downloadType;

    private final File tempVideo;
    private final File tempAudio;

    private long demuxerReadCount;

    public YouTubeDownload(YouTubeCrawledSearchResult sr) {
        super(convert(sr));
        this.sr = sr;
        this.downloadType = buildDownloadType(sr);

        FileSystem fs = Platforms.fileSystem();
        String filename = getSavePath().getName();
        tempVideo = buildTempFile(fs, filename, ".temp.m4v");
        tempAudio = buildTempFile(fs, filename, ".temp.m4a");
    }

    @Override
    public File previewFile() {
        if (isComplete()) {
            return getSavePath();
        } else {
            // intentionally not using FileSystem here
            if (tempVideo.exists()) {
                return tempVideo;
            }

            if (tempAudio.exists()) {
                return tempAudio;
            }
        }

        return null;
    }

    @Override
    protected void onHttpComplete() throws Throwable {
        boolean callSuper = true;
        if (downloadType == DownloadType.DASH) {
            FileSystem fs = Platforms.fileSystem();
            if (fs.exists(tempVideo) && !fs.exists(tempAudio)) {
                start(sr.getAudio().link, tempAudio, false);
                callSuper = false;
            }
        }

        if (callSuper) {
            super.onHttpComplete();
        }
    }

    @Override
    protected void onFinishing() throws Throwable {
        if (downloadType == DownloadType.VIDEO) {

            removeUdta(tempVideo);
            moveAndComplete(tempVideo, savePath);

        } else if (downloadType == DownloadType.DEMUX) {

            state = TransferState.DEMUXING;
            Mp4Demuxer.audio(tempAudio.getAbsoluteFile(), tempPath, buildMp4Info(true), readCount -> demuxerReadCount = readCount);
            moveAndComplete(tempPath, savePath);

            FileSystem fs = Platforms.fileSystem();
            if (!fs.delete(tempAudio)) {
                LOG.warn("Error deleting temporary audio file: " + tempAudio);
            }

        } else if (downloadType == DownloadType.DASH) {
            // intentionally not using FileSystem here
            if (tempVideo.exists() && tempAudio.exists()) {
                state = TransferState.DEMUXING;
                Mp4Demuxer.muxFragments(tempVideo.getAbsoluteFile(), tempAudio.getAbsoluteFile(), tempPath.getAbsoluteFile(), buildMp4Info(false), null);

                moveAndComplete(tempPath, savePath);

                FileSystem fs = Platforms.fileSystem();
                if (!fs.delete(tempVideo)) {
                    LOG.warn("Error deleting temporary video file: " + tempVideo);
                }
                if (!fs.delete(tempAudio)) {
                    LOG.warn("Error deleting temporary audio file: " + tempAudio);
                }

            } else {
                complete(TransferState.ERROR);
            }
        }
    }

    public void start() {
        if (downloadType == DownloadType.DEMUX) {
            start(sr.getAudio().link, tempAudio, false);
        } else {
            start(sr.getVideo().link, tempVideo, false);
        }
    }

    public int demuxingProgress() {
        if (state == TransferState.DEMUXING) {
            if (demuxerReadCount > 0) { // in case fmp4 fail
                long len = tempAudio.length();
                int r = len > 0 ? (int) (demuxerReadCount * 100 / len) : 0;
                return r;
            }
        }

        return 0;
    }

    protected boolean isDemuxDownload() {
        return downloadType == DownloadType.DEMUX;
    }

    private static Info convert(YouTubeCrawledSearchResult sr) {
        return new Info(sr.getDownloadUrl(), sr.getFilename(), sr.getDisplayName(), sr.getSize());
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

    private static File buildTempFile(FileSystem fs, String filename, String ext) {
        String name = FilenameUtils.getBaseName(filename);
        return buildFile(fs, Platforms.temp(), name + "." + ext);
    }

    private static void removeUdta(File mp4) throws IOException {
        RandomAccessFile f = new RandomAccessFile(mp4, "rw");
        try {
            IsoFile.free(f, Box.udta, ByteBuffer.allocate(100 * 1024));
        } finally {
            IOUtils.closeQuietly(f);
        }
    }

    private Mp4Info buildMp4Info(boolean audio) {
        String title = sr.getDisplayName();
        String author = sr.getSource();
        String source = "YouTube.com";

        if (author != null && author.startsWith("YouTube - ")) {
            author = author.replace("YouTube - ", "") + " (YouTube)";
        } else {
            YouTubeExtractor.LinkInfo audioLinkInfo = ((YouTubeCrawledSearchResult) sr.getParent()).getAudio();
            if (audioLinkInfo != null && audioLinkInfo.user != null) {
                author = audioLinkInfo.user + " (YoutTube)";
            }
        }

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

    private enum DownloadType {
        VIDEO, DASH, DEMUX
    }
}
