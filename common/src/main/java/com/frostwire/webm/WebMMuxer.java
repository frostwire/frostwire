/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.webm;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges one WebM video track and one WebM audio track (YouTube DASH style)
 * into a single WebM file. Codec bytes (including CodecPrivate) ride along
 * verbatim, so any WebM-contained codecs work without codec-specific code.
 *
 * <p>Output layout: EBML header (copied from the video input), one Segment
 * with known size (Info with updated Duration, both tracks renumbered 1/2,
 * time-merged Clusters of SimpleBlocks). No SeekHead, Cues, chapters, tags
 * or attachments — valid WebM that trades index-assisted seeking for mux
 * simplicity.
 *
 * <p>Deliberate v1 limits (fail fast, never guess):
 * <ul>
 *   <li>both inputs must share the same TimecodeScale (1,000,000 in practice);</li>
 *   <li>laced blocks are rejected;</li>
 *   <li>each input must hold at least one packet on the wanted track.</li>
 * </ul>
 */
public final class WebMMuxer {

    private static final long MAX_CLUSTER_SPAN = 30000;

    private WebMMuxer() {
    }

    /** Mux the first video track of {@code videoInput} + first audio of {@code audioInput}. */
    public static void mux(File videoInput, File audioInput, File output) throws IOException {
        if (videoInput == null || audioInput == null || output == null) {
            throw new IllegalArgumentException("inputs and output are required");
        }
        WebMDemuxer.Media video;
        WebMDemuxer.Media audio;
        try (RandomAccessFile vRaf = new RandomAccessFile(videoInput, "r");
             RandomAccessFile aRaf = new RandomAccessFile(audioInput, "r")) {
            video = WebMDemuxer.read(vRaf);
            audio = WebMDemuxer.read(aRaf);
        }
        if (video.timecodeScale != audio.timecodeScale) {
            throw new IOException("TimecodeScale mismatch: video=" + video.timecodeScale
                    + " audio=" + audio.timecodeScale);
        }
        WebMDemuxer.WebMTrack videoTrack = firstTrackOfType(video, 1);
        WebMDemuxer.WebMTrack audioTrack = firstTrackOfType(audio, 2);
        if (videoTrack == null) {
            throw new IOException("no video track in " + videoInput);
        }
        if (audioTrack == null) {
            throw new IOException("no audio track in " + audioInput);
        }
        if (videoTrack.packets.isEmpty()) {
            throw new IOException("no video packets in " + videoInput);
        }
        if (audioTrack.packets.isEmpty()) {
            throw new IOException("no audio packets in " + audioInput);
        }
        long timecodeScale = video.timecodeScale;
        List<OutBlock> merged = merge(videoTrack, audioTrack);
        List<OutCluster> clusters = clusterize(merged);
        byte[] tracks = buildTracks(video, videoTrack, audio, audioTrack);
        byte[] info = buildInfo(timecodeScale, endTimestamp(videoTrack, audioTrack));
        long segmentSize = info.length + tracks.length + clustersSize(clusters);
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (RandomAccessFile vRaf = new RandomAccessFile(videoInput, "r");
             RandomAccessFile aRaf = new RandomAccessFile(audioInput, "r");
             RandomAccessFile out = new RandomAccessFile(output, "rw")) {
            out.setLength(0);
            out.write(video.ebmlHeader);
            writeElement(out, Id.SEGMENT, segmentSize);
            out.write(info);
            out.write(tracks);
            for (OutCluster cluster : clusters) {
                ByteWriter clusterBody = new ByteWriter();
                clusterBody.writeElement(Id.TIMECODE, uintBytes(cluster.timecode));
                for (OutBlock block : cluster.blocks) {
                    RandomAccessFile src = block.video ? vRaf : aRaf;
                    byte[] payload = new byte[block.packet.size];
                    src.seek(block.packet.fileOffset);
                    src.readFully(payload);
                    clusterBody.writeElement(Id.SIMPLE_BLOCK,
                            simpleBlockBytes(block.outTrack,
                                    block.packet.timestamp - cluster.timecode,
                                    block.packet.keyframe, payload));
                }
                byte[] body = clusterBody.toByteArray();
                writeElement(out, Id.CLUSTER, body.length);
                out.write(body);
            }
        }
    }

    // ------------------------------------------------------------------ model

    static final class Id {
        static final byte[] SEGMENT = {(byte) 0x18, 0x53, (byte) 0x80, 0x67};
        static final byte[] INFO = {0x15, 0x49, (byte) 0xA9, 0x66};
        static final byte[] TIMECODE_SCALE = {0x2A, (byte) 0xD7, (byte) 0xB1};
        static final byte[] DURATION = {0x44, (byte) 0x89};
        static final byte[] MUXING_APP = {0x4D, (byte) 0x80};
        static final byte[] WRITING_APP = {0x57, 0x41};
        static final byte[] TRACKS = {0x16, 0x54, (byte) 0xAE, 0x6B};
        static final byte[] TRACK_ENTRY = {(byte) 0xAE};
        static final byte[] TRACK_NUMBER = {(byte) 0xD7};
        static final byte[] TRACK_UID = {0x73, (byte) 0xC5};
        static final byte[] CLUSTER = {0x1F, 0x43, (byte) 0xB6, 0x75};
        static final byte[] TIMECODE = {(byte) 0xE7};
        static final byte[] SIMPLE_BLOCK = {(byte) 0xA3};
    }

    static final class OutBlock {
        boolean video;
        long outTrack;
        WebMDemuxer.Packet packet;
    }

    static final class OutCluster {
        long timecode;
        final List<OutBlock> blocks = new ArrayList<>();
    }

    private static WebMDemuxer.WebMTrack firstTrackOfType(WebMDemuxer.Media media, int type) {
        for (WebMDemuxer.WebMTrack track : media.tracks) {
            if (track.type == type) {
                return track;
            }
        }
        return null;
    }

    /** Time-merged packet list (video first on timestamp ties, as authored). */
    static List<OutBlock> merge(WebMDemuxer.WebMTrack video, WebMDemuxer.WebMTrack audio) {
        List<OutBlock> out = new ArrayList<>(video.packets.size() + audio.packets.size());
        int vi = 0;
        int ai = 0;
        while (vi < video.packets.size() || ai < audio.packets.size()) {
            boolean takeVideo;
            if (vi >= video.packets.size()) {
                takeVideo = false;
            } else if (ai >= audio.packets.size()) {
                takeVideo = true;
            } else {
                takeVideo = video.packets.get(vi).timestamp <= audio.packets.get(ai).timestamp;
            }
            OutBlock block = new OutBlock();
            if (takeVideo) {
                block.video = true;
                block.outTrack = 1;
                block.packet = video.packets.get(vi++);
            } else {
                block.video = false;
                block.outTrack = 2;
                block.packet = audio.packets.get(ai++);
            }
            out.add(block);
        }
        return out;
    }

    /** Split into clusters: new cluster when the int16 relative range would overflow. */
    static List<OutCluster> clusterize(List<OutBlock> merged) {
        List<OutCluster> clusters = new ArrayList<>();
        OutCluster current = null;
        for (OutBlock block : merged) {
            if (current == null || block.packet.timestamp - current.timecode > MAX_CLUSTER_SPAN
                    || block.packet.timestamp < current.timecode) {
                current = new OutCluster();
                current.timecode = block.packet.timestamp;
                clusters.add(current);
            }
            current.blocks.add(block);
        }
        return clusters;
    }

    private static long endTimestamp(WebMDemuxer.WebMTrack video, WebMDemuxer.WebMTrack audio) {
        return Math.max(trackEnd(video), trackEnd(audio));
    }

    private static long trackEnd(WebMDemuxer.WebMTrack track) {
        int n = track.packets.size();
        if (n == 0) {
            return 0;
        }
        long last = track.packets.get(n - 1).timestamp;
        if (n == 1) {
            return last + 20;
        }
        // Median inter-packet gap: robust against long inter-cluster gaps that
        // would corrupt a mean (clustered media has a few huge deltas).
        List<Long> deltas = new ArrayList<>(n - 1);
        for (int i = 1; i < n; i++) {
            deltas.add(track.packets.get(i).timestamp - track.packets.get(i - 1).timestamp);
        }
        deltas.sort(null);
        long step = Math.max(1, deltas.get(deltas.size() / 2));
        return last + step;
    }

    private static byte[] uintBytes(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative uint: " + value);
        }
        int len = 1;
        while (len < 8 && value >= (1L << (8 * len))) {
            len++;
        }
        byte[] out = new byte[len];
        for (int i = len - 1; i >= 0; i--) {
            out[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return out;
    }

    private static byte[] float64Bytes(double value) {
        long bits = Double.doubleToLongBits(value);
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (bits & 0xFF);
            bits >>= 8;
        }
        return out;
    }

    private static byte[] buildInfo(long timecodeScale, long endTimestamp) {
        ByteWriter info = new ByteWriter();
        info.writeElement(Id.TIMECODE_SCALE, uintBytes(timecodeScale));
        // Duration is milliseconds: ticks * nanoseconds-per-tick / 1e6.
        double millis = endTimestamp * ((double) timecodeScale / 1000000.0);
        info.writeElement(Id.DURATION, float64Bytes(millis));
        byte[] app = "FrostWire".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        info.writeElement(Id.MUXING_APP, app);
        info.writeElement(Id.WRITING_APP, app);
        ByteWriter outer = new ByteWriter();
        outer.writeElement(Id.INFO, info.toByteArray());
        return outer.toByteArray();
    }

    private static byte[] buildTracks(WebMDemuxer.Media video, WebMDemuxer.WebMTrack videoTrack,
                                      WebMDemuxer.Media audio, WebMDemuxer.WebMTrack audioTrack)
            throws IOException {
        ByteWriter tracks = new ByteWriter();
        tracks.writeElement(Id.TRACK_ENTRY,
                renumberedEntry(video, videoTrack, 1));
        tracks.writeElement(Id.TRACK_ENTRY,
                renumberedEntry(audio, audioTrack, 2));
        ByteWriter outer = new ByteWriter();
        outer.writeElement(Id.TRACKS, tracks.toByteArray());
        return outer.toByteArray();
    }

    /** Verbatim TrackEntry *content* bytes with TrackNumber/TrackUID rewritten to {@code outNumber}. */
    static byte[] renumberedEntry(WebMDemuxer.Media media, WebMDemuxer.WebMTrack track, long outNumber)
            throws IOException {
        int index = media.tracks.indexOf(track);
        if (index < 0 || index >= media.trackEntryBytes.size()) {
            throw new IOException("track entry bytes missing");
        }
        return renumberEntryBytes(media.trackEntryBytes.get(index), outNumber);
    }

    static byte[] renumberEntryBytes(byte[] entry, long outNumber) throws IOException {
        // Rebuild the entry: copy children, replacing TrackNumber/TrackUID.
        // Parsed via a scratch file (entries are tiny) so Ebml stays file-based.
        File tmp = File.createTempFile("webm-entry-", ".bin");
        try {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                fos.write(entry);
            }
            List<byte[]> parts = new ArrayList<>();
            long total = 0;
            try (RandomAccessFile mem = new RandomAccessFile(tmp, "r")) {
                long pos = 0;
                while (pos + 2 <= entry.length) {
                    Ebml.Element e = Ebml.readElement(mem, pos);
                    if (e.payloadEnd > entry.length) {
                        throw new IOException("track entry overrun");
                    }
                    byte[] payload = Ebml.readBytes(mem, e);
                    byte[] rebuilt;
                    if (e.id == WebMDemuxer.ID_TRACKNUMBER || e.id == WebMDemuxer.ID_TRACKUID) {
                        rebuilt = uintBytes(outNumber);
                    } else {
                        rebuilt = payload;
                    }
                    byte[] element = concat(e.idBytes, Ebml.encodeSize(rebuilt.length), rebuilt);
                    parts.add(element);
                    total += element.length;
                    pos = e.payloadEnd;
                }
            }
            byte[] out = new byte[(int) total];
            int off = 0;
            for (byte[] part : parts) {
                System.arraycopy(part, 0, out, off, part.length);
                off += part.length;
            }
            return out;
        } finally {
            tmp.delete();
        }
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, off, part.length);
            off += part.length;
        }
        return out;
    }

    private static void writeElement(RandomAccessFile out, byte[] id, long size) throws IOException {
        out.write(id);
        out.write(Ebml.encodeSize(size));
    }

    private static void writeElement(RandomAccessFile out, byte[] id, byte[] payload)
            throws IOException {
        writeElement(out, id, payload.length);
        out.write(payload);
    }

    private static long clustersSize(List<OutCluster> clusters) {
        long total = 0;
        for (OutCluster cluster : clusters) {
            long body = 0;
            body += elementSize(Id.TIMECODE.length, uintBytes(cluster.timecode).length);
            for (OutBlock block : cluster.blocks) {
                int trackLen = Ebml.vintLength(block.outTrack);
                // track vint + int16 ts + flags + payload
                long payload = (long) trackLen + 2 + 1 + block.packet.size;
                body += elementSize(Id.SIMPLE_BLOCK.length, payload);
            }
            total += elementSize(Id.CLUSTER.length, body);
        }
        return total;
    }

    private static long elementSize(int idLen, long payloadSize) {
        return idLen + Ebml.encodeSize(payloadSize).length + payloadSize;
    }

    /** Full SimpleBlock content (track vint + int16 ts + flags + payload); caller frames it. */
    static byte[] simpleBlockBytes(long trackNumber, long relativeTs, boolean keyframe, byte[] payload) {
        // Track numbers in blocks coincide with size encoding for small values;
        // clusterize keeps ts in int16 range.
        byte[] track = Ebml.encodeSize(trackNumber);
        int ts = (int) relativeTs;
        byte[] out = new byte[track.length + 2 + 1 + payload.length];
        System.arraycopy(track, 0, out, 0, track.length);
        out[track.length] = (byte) ((ts >> 8) & 0xFF);
        out[track.length + 1] = (byte) (ts & 0xFF);
        out[track.length + 2] = (byte) (keyframe ? 0x80 : 0x00);
        System.arraycopy(payload, 0, out, track.length + 3, payload.length);
        return out;
    }

    /** Growing byte buffer with EBML element writers. */
    static final class ByteWriter {
        private byte[] buf = new byte[1024];
        private int count;

        void write(byte[] bytes) {
            ensure(count + bytes.length);
            System.arraycopy(bytes, 0, buf, count, bytes.length);
            count += bytes.length;
        }

        void writeRaw(byte[] bytes) {
            write(bytes);
        }

        void writeElement(byte[] id, byte[] payload) {
            write(id);
            write(Ebml.encodeSize(payload.length));
            write(payload);
        }

        byte[] toByteArray() {
            byte[] out = new byte[count];
            System.arraycopy(buf, 0, out, 0, count);
            return out;
        }

        private void ensure(int needed) {
            if (needed <= buf.length) {
                return;
            }
            int size = Math.max(buf.length * 2, needed);
            byte[] grown = new byte[size];
            System.arraycopy(buf, 0, grown, 0, count);
            buf = grown;
        }
    }
}
