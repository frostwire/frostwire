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

package com.frostwire.search.youtube;

import com.frostwire.search.AbstractCrawledSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.youtube.YouTubeExtractor.LinkInfo;
import com.frostwire.util.StringUtils;
import org.apache.commons.io.FilenameUtils;

import static com.frostwire.search.youtube.YouTubeUtils.buildDownloadUrl;

/**
 * @author gubatron
 * @author aldenml
 */
public class YouTubeCrawledSearchResult extends AbstractCrawledSearchResult implements HttpSearchResult {

    private final LinkInfo video;
    private final LinkInfo audio;
    private final String filename;
    private final String displayName;
    private final long creationTime;
    private final long size;
    private final String downloadUrl;

    public YouTubeCrawledSearchResult(YouTubeSearchResult sr, LinkInfo video, LinkInfo audio) {
        super(sr);

        this.video = video;
        this.audio = audio;

        this.filename = buildFilename(video, audio);
        this.displayName = FilenameUtils.getBaseName(this.filename);
        this.creationTime = audio != null ? audio.date.getTime() : video.date.getTime();
        this.size = buildSize((int) sr.getSize(), video, audio);
        this.downloadUrl = buildDownloadUrl(video, audio);
    }

    public LinkInfo getVideo() {
        return video;
    }

    public LinkInfo getAudio() {
        return audio;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String getDownloadUrl() {
        return downloadUrl;
    }

    private String buildFilename(LinkInfo video, LinkInfo audio) {
        String filename;
        if (video != null && audio == null) {
            filename = String.format("%s_%s_%s_%s.%s", video.filename, video.format.video, video.format.audio, video.format.quality, video.format.ext);
        } else if (video == null && audio != null) {
            String ext = audio.format.ext.equals("mp4") ? "m4a" : audio.format.ext;
            filename = String.format("%s_%s_%s_%s.%s", audio.filename, audio.format.video, audio.format.audio, audio.format.quality, ext);
        } else if (video != null && audio != null) {
            filename = String.format("%s_%s_%s_%s.%s", video.filename, video.format.video, audio.format.audio, video.format.quality, "mp4");
        } else {
            throw new IllegalArgumentException("No track defined");
        }

        return filename;
    }

    /**
     * Upper guess to determine the duration in bytes, using highest bitrate of the stream.
     *
     * @param durationInSeconds
     * @param linfo
     * @return (([(video bitrate)] + [(audio bitrate)])*durationInSeconds)/8
     */
    private long buildSize(int durationInSeconds, LinkInfo linfo) {
        long result = -1;
        double bitRateSum = 0;

        switch (linfo.fmt) {
            case 5:// new Format("flv", "H263", "MP3", "240p"));
                bitRateSum = 0.25 + 64d / 1024d;
                break;
            case 6://, new Format("flv", "H263", "MP3", "270p"));
                bitRateSum = 0.8 + 64d / 1024d;
                break;
            case 17://, new Format("3gp", "H264", "AAC", "144p"));
                bitRateSum = 0.05 + 24d / 1024d;
                break;
            case 18://, new Format("mp4", "H264", "AAC", "360p"));
                bitRateSum = 0.5 + 96d / 1024d;
                break;
            case 22://, new Format("mp4", "H264", "AAC", "720p"));
                bitRateSum = 2.9 + 192d / 1024d;
                break;
            case 34://, new Format("flv", "H264", "AAC", "360p"));
                bitRateSum = 0.5 + 128d / 1024d;
                break;
            case 35://, new Format("flv", "H264", "AAC", "480p"));
                bitRateSum = 1 + 128d / 1024d;
                break;
            case 36://, new Format("3gp", "H264", "AAC", "240p"));
                bitRateSum = 0.17 + 38d / 1024d;
                break;
            case 37://, new Format("mp4", "H264", "AAC", "1080p"));
                bitRateSum = 5.9 + 192d / 1024d;
                break;
            case 38://, new Format("mp4", "H264", "AAC", "3072p"));
                bitRateSum = 5 + 192d / 1024d;
                break;
            case 43://, new Format("webm", "VP8", "Vorbis", "360p"));
                bitRateSum = 0.5 + 128d / 1024d;
                break;
            case 44://, new Format("webm", "VP8", "Vorbis", "480p"));
                bitRateSum = 1 + 128d / 1024d;
                break;
            case 45://, new Format("webm", "VP8", "Vorbis", "720p"));
                bitRateSum = 2 + 192d / 1024d;
                break;
            case 46://, new Format("webm", "VP8", "Vorbis", "1080p"));
                bitRateSum = 3 + 192d / 1024d;
                break;
            case 82://, new Format("mp4", "H264", "AAC", "360p"));
                bitRateSum = 0.5 + 96d / 1024d;
                break;
            case 83://, new Format("mp4", "H264", "AAC", "240p"));
                bitRateSum = 0.5 + 96d / 1024d;
                break;
            case 84://, new Format("mp4", "H264", "AAC", "720p"));
                bitRateSum = 2.9 + 152d / 1024d;
                break;
            case 85://, new Format("mp4", "H264", "AAC", "520p"));
                bitRateSum = 2.9 + 152d / 1024d;
                break;
            case 100://, new Format("webm", "VP8", "Vorbis", "360p"));
                bitRateSum = 0.5 + 128d / 1024d;
                break;
            case 101://, new Format("webm", "VP8", "Vorbis", "360p"));
                bitRateSum = 1 + 192d / 1024d;
                break;
            case 102://, new Format("webm", "VP8", "Vorbis", "720p"));
                bitRateSum = 2 + 192d / 1024d;
                break;
            // dash video
            case 133://, new Format("m4v", "H264", "", "240p"));
                bitRateSum = 0.3 + 256d / 1024d;
                break;
            case 134://, new Format("m4v", "H264", "", "360p"));
                bitRateSum = 0.4 + 256d / 1024d;
                break;
            case 135://, new Format("m4v", "H264", "", "480p"));
                bitRateSum = 1 + 256d / 1024d;
                break;
            case 136://, new Format("m4v", "H264", "", "720p"));
                bitRateSum = 1.5 + 256d / 1024d;
                break;
            case 137://, new Format("m4v", "H264", "", "1080p"));
                bitRateSum = 2.9 + 256d / 1024d;
                break;
            // dash audio
            case 139://, new Format("m4a", "", "AAC", "48k"));
                bitRateSum = 48d / 1024d;
                break;
            case 140://, new Format("m4a", "", "AAC", "128k"));
                bitRateSum = 128d / 1024d;
                break;
            case 141://, new Format("m4a", "", "AAC", "256k"));
                bitRateSum = 256d / 1024d;
                break;
        }

        bitRateSum = bitRateSum * 1024 * 1024; //Mbits to bits.
        result = (long) (Math.ceil((bitRateSum * durationInSeconds) / 8));
        return result;
    }

    private long buildSize(int durationInSeconds, LinkInfo video, LinkInfo audio) {
        long size = UNKNOWN_SIZE;

        if (durationInSeconds != UNKNOWN_SIZE) {
            if (video != null && audio == null) {
                size = buildSize(durationInSeconds, video);
            } else if (video == null && audio != null) {
                size = buildSize(durationInSeconds, audio);
            } else if (video != null && audio != null) {
                size = buildSize(durationInSeconds, video);
            } else {
                throw new IllegalArgumentException("No track defined");
            }
        }
        return size;
    }

    @Override
    public String getThumbnailUrl() {
        return getBestThumbnailUrl(video != null ? video : audio);
    }

    private String getBestThumbnailUrl(LinkInfo linfo) {
        String thumbnailUrl = null;
        if (linfo != null && linfo.thumbnails != null) {
            if (!StringUtils.isNullOrEmpty(linfo.thumbnails.hq)) {
                thumbnailUrl = linfo.thumbnails.hq;
            } else if (!StringUtils.isNullOrEmpty(linfo.thumbnails.mq)) {
                thumbnailUrl = linfo.thumbnails.mq;
            } else if (!StringUtils.isNullOrEmpty(linfo.thumbnails.normal)) {
                thumbnailUrl = linfo.thumbnails.normal;
            }
        }
        return thumbnailUrl;
    }
}
