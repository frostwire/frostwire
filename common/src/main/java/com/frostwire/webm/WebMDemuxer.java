/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.webm;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal WebM/Matroska demuxer: tracks (number, type, codec id + private
 * bytes) and time-ordered packets (timestamp, keyframe, file offset, size).
 *
 * <p>Understands SimpleBlock and BlockGroup/Block; laced blocks are rejected
 * (neither YouTube nor ffmpeg emits them). Codec payloads are never parsed.
 */
public final class WebMDemuxer {

    // EBML IDs (length marker included, as read by Ebml.readId).
    static final long ID_EBML = 0x1A45DFA3L;
    static final long ID_SEGMENT = 0x18538067L;
    static final long ID_INFO = 0x1549A966L;
    static final long ID_TIMECODESCALE = 0x2AD7B1L;
    static final long ID_DURATION = 0x4489L;
    static final long ID_TRACKS = 0x1654AE6BL;
    static final long ID_TRACKENTRY = 0xAEL;
    static final long ID_TRACKNUMBER = 0xD7L;
    static final long ID_TRACKUID = 0x73C5L;
    static final long ID_TRACKTYPE = 0x83L;
    static final long ID_CODECID = 0x86L;
    static final long ID_CODECPRIVATE = 0x63A2L;
    static final long ID_CLUSTER = 0x1F43B675L;
    static final long ID_TIMECODE = 0xE7L;
    static final long ID_SIMPLEBLOCK = 0xA3L;
    static final long ID_BLOCKGROUP = 0xA0L;
    static final long ID_BLOCK = 0xA1L;

    private WebMDemuxer() {
    }

    public static final class WebMTrack {
        /** Track number as stored in the file. */
        public long number;
        /** 1 = video, 2 = audio. */
        public int type;
        public String codecId = "";
        public byte[] codecPrivate = new byte[0];
        public final List<Packet> packets = new ArrayList<>();
    }

    public static final class Packet {
        /** Absolute timestamp in TimecodeScale units. */
        public long timestamp;
        public boolean keyframe;
        public long fileOffset;
        public int size;
    }

    public static final class Media {
        public long timecodeScale = 1000000;
        public double segmentDuration = -1;
        /** Raw EBML header bytes (copied verbatim into muxed output). */
        public byte[] ebmlHeader = new byte[0];
        /** Raw Info payload children we preserve (MuxingApp etc. are rebuilt). */
        public final List<WebMTrack> tracks = new ArrayList<>();
        /** Raw TrackEntry bytes per track, for verbatim rebuild. */
        public final List<byte[]> trackEntryBytes = new ArrayList<>();
    }

    /** Parse tracks + packets. Positions (not bytes) are recorded for packets. */
    public static Media read(RandomAccessFile raf) throws IOException {
        Media media = new Media();
        Ebml.Element ebml = Ebml.readElement(raf, 0);
        if (ebml.id != ID_EBML) {
            throw new IOException("not an EBML file");
        }
        // Full header bytes (ID + size + content) for verbatim output prefix.
        byte[] header = new byte[(int) (ebml.headerLength + ebml.size)];
        raf.seek(0);
        raf.readFully(header);
        media.ebmlHeader = header;
        Ebml.Element segment = null;
        long pos = ebml.payloadEnd;
        long end = raf.length();
        while (pos + 2 <= end) {
            Ebml.Element e = Ebml.readElement(raf, pos);
            if (e.id == ID_SEGMENT) {
                segment = e;
                break;
            }
            pos = e.payloadEnd;
        }
        if (segment == null) {
            throw new IOException("no Segment found");
        }
        // Two passes: tracks must be known before cluster packets resolve.
        List<Ebml.Element> children =
                Ebml.children(raf, segment.payloadOffset, segment.payloadEnd);
        for (Ebml.Element child : children) {
            if (child.id == ID_INFO) {
                readInfo(raf, child, media);
            } else if (child.id == ID_TRACKS) {
                readTracks(raf, child, media);
            }
        }
        for (Ebml.Element child : children) {
            if (child.id == ID_CLUSTER) {
                readCluster(raf, child, media);
            }
        }
        if (media.tracks.isEmpty()) {
            throw new IOException("no tracks found");
        }
        return media;
    }

    private static void readInfo(RandomAccessFile raf, Ebml.Element info, Media media)
            throws IOException {
        for (Ebml.Element child : Ebml.children(raf, info.payloadOffset, info.payloadEnd)) {
            if (child.id == ID_TIMECODESCALE) {
                media.timecodeScale = Ebml.readUint(raf, child);
            } else if (child.id == ID_DURATION) {
                media.segmentDuration = Ebml.readFloat(raf, child);
            }
        }
        if (media.timecodeScale <= 0) {
            media.timecodeScale = 1000000;
        }
    }

    private static void readTracks(RandomAccessFile raf, Ebml.Element tracks, Media media)
            throws IOException {
        for (Ebml.Element entry : Ebml.children(raf, tracks.payloadOffset, tracks.payloadEnd)) {
            if (entry.id != ID_TRACKENTRY) {
                continue;
            }
            WebMTrack track = new WebMTrack();
            long entrySize = entry.payloadEnd - entry.payloadOffset;
            if (entrySize > Integer.MAX_VALUE) {
                throw new IOException("track entry too large");
            }
            byte[] raw = new byte[(int) entrySize];
            raf.seek(entry.payloadOffset);
            raf.readFully(raw);
            media.trackEntryBytes.add(raw);
            for (Ebml.Element field : Ebml.children(raf, entry.payloadOffset, entry.payloadEnd)) {
                if (field.id == ID_TRACKNUMBER) {
                    track.number = Ebml.readUint(raf, field);
                } else if (field.id == ID_TRACKTYPE) {
                    track.type = (int) Ebml.readUint(raf, field);
                } else if (field.id == ID_CODECID) {
                    track.codecId = Ebml.readString(raf, field);
                } else if (field.id == ID_CODECPRIVATE) {
                    track.codecPrivate = Ebml.readBytes(raf, field);
                }
            }
            media.tracks.add(track);
        }
    }

    private static void readCluster(RandomAccessFile raf, Ebml.Element cluster, Media media)
            throws IOException {
        long timecode = 0;
        boolean haveTimecode = false;
        for (Ebml.Element child : Ebml.children(raf, cluster.payloadOffset, cluster.payloadEnd)) {
            if (child.id == ID_TIMECODE) {
                timecode = Ebml.readUint(raf, child);
                haveTimecode = true;
            } else if (child.id == ID_SIMPLEBLOCK) {
                if (!haveTimecode) {
                    throw new IOException("SimpleBlock before cluster Timecode");
                }
                readBlock(raf, child, timecode, media);
            } else if (child.id == ID_BLOCKGROUP) {
                if (!haveTimecode) {
                    throw new IOException("BlockGroup before cluster Timecode");
                }
                for (Ebml.Element inner :
                        Ebml.children(raf, child.payloadOffset, child.payloadEnd)) {
                    if (inner.id == ID_BLOCK) {
                        readBlock(raf, inner, timecode, media);
                        break;
                    }
                }
            }
        }
    }

    private static void readBlock(RandomAccessFile raf, Ebml.Element block, long clusterTimecode,
                                  Media media) throws IOException {
        long[] trackNo = Ebml.readVint(raf, block.payloadOffset);
        int header = (int) trackNo[1];
        long tsPos = block.payloadOffset + header;
        raf.seek(tsPos);
        int tsHi = raf.read();
        int tsLo = raf.read();
        if (tsHi < 0 || tsLo < 0) {
            throw new IOException("truncated block timestamp");
        }
        int relative = (short) ((tsHi << 8) | tsLo);
        int flagsPos = (int) (tsPos + 2);
        raf.seek(flagsPos);
        int flags = raf.read();
        if (flags < 0) {
            throw new IOException("truncated block flags");
        }
        if (((flags >> 1) & 3) != 0) {
            throw new IOException("laced blocks unsupported (flags=0x"
                    + Integer.toHexString(flags) + ")");
        }
        long payloadOffset = flagsPos + 1;
        int payloadSize = (int) (block.payloadEnd - payloadOffset);
        if (payloadSize <= 0) {
            throw new IOException("empty block payload");
        }
        WebMTrack track = trackByNumber(media, trackNo[0]);
        if (track == null) {
            return; // block for an unknown track (e.g. subtitles): skip
        }
        Packet packet = new Packet();
        packet.timestamp = clusterTimecode + relative;
        packet.keyframe = (flags & 0x80) != 0;
        packet.fileOffset = payloadOffset;
        packet.size = payloadSize;
        track.packets.add(packet);
    }

    private static WebMTrack trackByNumber(Media media, long number) {
        for (WebMTrack track : media.tracks) {
            if (track.number == number) {
                return track;
            }
        }
        return null;
    }
}
