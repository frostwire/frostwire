/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Mp4Muxer tests on tiny synthetic fragmented inputs
 * ({@code v-in.mp4}: 2 frags x 2 h264 samples, trex duration fallback,
 * non-sync samples, composition offsets; {@code a-in.m4a}: 2 frags x 3 AAC
 * samples, both tracks id 1 to exercise renumbering).
 *
 * <p>Output sample tables are walked independently (stco/stsc/stsz) and every
 * sample payload is byte-compared against the fixture fills.
 */
class Mp4MuxerTest {

    private static File fixture(String name) throws Exception {
        URL resource = Mp4MuxerTest.class.getResource("/mp4/" + name);
        assertNotNull(resource, "missing fixture " + name);
        File tmp = File.createTempFile("muxer-test-", "-" + name);
        tmp.deleteOnExit();
        try (java.io.InputStream in = resource.openStream()) {
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    private static final class DecodedTrack {
        int handler;
        int trackId;
        int timescale;
        long duration;
        List<byte[]> samples = new ArrayList<>();
        List<Long> durations = new ArrayList<>();
        List<Long> ctos = new ArrayList<>();
        List<Integer> syncs = new ArrayList<>();
    }

    /** Read every sample of a regular-MP4 track via its own chunk tables. */
    private static DecodedTrack readTrack(RandomAccessFile raf, TrackBox trak) throws Exception {
        DecodedTrack out = new DecodedTrack();
        MediaBox mdia = trak.findFirst(Box.mdia);
        HandlerBox hdlr = mdia.findFirst(Box.hdlr);
        out.handler = hdlr.handler_type;
        TrackHeaderBox tkhd = trak.findFirst(Box.tkhd);
        out.trackId = tkhd.trackId();
        MediaHeaderBox mdhd = mdia.findFirst(Box.mdhd);
        out.timescale = mdhd.timescale;
        out.duration = mdhd.duration;
        MediaInformationBox minf = mdia.findFirst(Box.minf);
        SampleTableBox stbl = minf.findFirst(Box.stbl);
        ChunkOffsetBox stco = stbl.findFirst(Box.stco);
        assertNotNull(stco, "expected stco (fixtures are small)");
        SampleToChunkBox stsc = stbl.findFirst(Box.stsc);
        SampleSizeBox stsz = stbl.findFirst(Box.stsz);
        TimeToSampleBox stts = stbl.findFirst(Box.stts);
        // Expand durations.
        List<Long> durations = new ArrayList<>();
        if (stts != null && stts.entries != null) {
            for (TimeToSampleBox.Entry e : stts.entries) {
                for (int i = 0; i < e.sample_count; i++) {
                    durations.add(e.sample_delta & 0xFFFFFFFFL);
                }
            }
        }
        // Expand composition offsets (absent box = all zero).
        List<Long> ctos = new ArrayList<>();
        CompositionOffsetBox ctts = stbl.findFirst(Box.ctts);
        if (ctts != null && ctts.entries != null) {
            for (CompositionOffsetBox.Entry e : ctts.entries) {
                for (int i = 0; i < e.sample_count; i++) {
                    ctos.add((long) e.sample_offset);
                }
            }
        }
        while (ctos.size() < durations.size()) {
            ctos.add(0L);
        }
        // Sync map (absent box = all sync).
        boolean[] sync = new boolean[durations.size()];
        java.util.Arrays.fill(sync, true);
        SyncSampleBox stss = stbl.findFirst(Box.stss);
        if (stss != null && stss.entries != null) {
            java.util.Arrays.fill(sync, false);
            for (SyncSampleBox.Entry e : stss.entries) {
                sync[e.sample_number - 1] = true;
            }
        }
        // Walk chunks.
        int sampleIdx = 0;
        for (int c = 0; c < stco.entries.length; c++) {
            long offset = stco.entries[c].chunk_offset & 0xFFFFFFFFL;
            int perChunk = stsc.entries[stsc.entries.length - 1].samples_per_chunk;
            for (int i = stsc.entries.length - 1; i >= 0; i--) {
                if (stsc.entries[i].first_chunk <= c + 1) {
                    perChunk = stsc.entries[i].samples_per_chunk;
                    break;
                }
            }
            raf.seek(offset);
            for (int k = 0; k < perChunk; k++) {
                int size = stsz.entries[sampleIdx].entry_size;
                byte[] payload = new byte[size];
                raf.readFully(payload);
                out.samples.add(payload);
                out.durations.add(durations.get(sampleIdx));
                out.ctos.add(ctos.get(sampleIdx));
                if (sync[sampleIdx]) {
                    out.syncs.add(sampleIdx + 1);
                }
                sampleIdx++;
            }
        }
        assertEquals(durations.size(), sampleIdx);
        return out;
    }

    /** Sample description entry + codec config ride along verbatim from inputs. */
    private static void assertSampleDescription(MovieBox moov, int handler,
                                                String entryFourcc, String configFourcc) {
        TrackBox trak = trakByHandler(moov, handler);
        assertNotNull(trak);
        MediaBox mdia = trak.findFirst(Box.mdia);
        MediaInformationBox minf = mdia.findFirst(Box.minf);
        SampleTableBox stbl = minf.findFirst(Box.stbl);
        SampleDescriptionBox stsd = stbl.findFirst(Box.stsd);
        assertNotNull(stsd);
        boolean foundEntry = false;
        boolean foundConfig = false;
        if (stsd.entries != null) {
          for (SampleEntry entry : stsd.entries) {
            if (entry.toString().equals(entryFourcc)) {
              foundEntry = true;
              if (entry.boxes != null) {
                for (Box child : entry.boxes) {
                  if (child.toString().equals(configFourcc)) {
                    foundConfig = true;
                  }
                }
              }
            }
          }
        }
        assertTrue(foundEntry, "missing " + entryFourcc);
        assertTrue(foundConfig, "missing " + configFourcc);
    }

    private static TrackBox trakByHandler(MovieBox moov, int handler) {        for (TrackBox trak : moov.<TrackBox>find(Box.trak)) {
            MediaBox mdia = trak.findFirst(Box.mdia);
            HandlerBox hdlr = mdia == null ? null : mdia.findFirst(Box.hdlr);
            if (hdlr != null && hdlr.handler_type == handler) {
                return trak;
            }
        }
        return null;
    }

    @Test
    void muxesFragmentedTracksIntoRegularMp4() throws Exception {
        File video = fixture("v-in.mp4");
        File audio = fixture("a-in.m4a");
        File out = File.createTempFile("muxer-test-out-", ".mp4");
        out.deleteOnExit();
        Mp4Muxer.mux(video, audio, out);
        assertTrue(out.length() > 0);

        ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
        DecodedTrack v;
        DecodedTrack a;
        try (RandomAccessFile raf = new RandomAccessFile(out, "r")) {
            LinkedList<Box> tops = IsoFile.head(raf, buf);
            // moov before mdat.
            int moovPos = -1;
            int mdatPos = -1;
            for (int i = 0; i < tops.size(); i++) {
                if (tops.get(i).type == Box.moov) moovPos = i;
                if (tops.get(i).type == Box.mdat && mdatPos < 0) mdatPos = i;
            }
            assertTrue(moovPos >= 0 && mdatPos > moovPos, "moov must precede mdat");
            MovieBox moov = null;
            for (Box b : tops) {
                if (b.type == Box.moov) moov = (MovieBox) b;
            }
            assertNotNull(moov);
            // No fragments in output.
            for (Box b : tops) {
                assertTrue(b.type != Box.moof, "output must not be fragmented");
            }
            MovieHeaderBox mvhd = moov.findFirst(Box.mvhd);
            assertEquals(1000, mvhd.timescale);
            v = readTrack(raf, trakByHandler(moov, Box.vide));
            a = readTrack(raf, trakByHandler(moov, Box.soun));
            assertSampleDescription(moov, Box.vide, "avc1", "avcC");
            assertSampleDescription(moov, Box.soun, "mp4a", "esds");
        }

        assertEquals(1, v.trackId);
        assertEquals(15360, v.timescale);
        assertEquals(2048, v.duration);
        assertEquals(4, v.samples.size());
        assertEquals(List.of(100, 120, 110, 130),
                v.samples.stream().map(b -> b.length).toList());
        assertEquals(List.of(512L, 512L, 512L, 512L), v.durations);
        assertEquals(List.of(0L, 1024L, 0L, 0L), v.ctos);
        assertEquals(List.of(1, 3), v.syncs);
        assertEquals(1, v.samples.get(0)[0]);
        assertEquals(4, v.samples.get(3)[0]);

        assertEquals(2, a.trackId);
        assertEquals(48000, a.timescale);
        assertEquals(6144, a.duration);
        assertEquals(6, a.samples.size());
        assertEquals(List.of(50, 60, 55, 65, 70, 75),
                a.samples.stream().map(b -> b.length).toList());
        assertEquals(List.of(1024L, 1024L, 1024L, 1024L, 1024L, 1024L), a.durations);
        assertEquals(List.of(1, 2, 3, 4, 5, 6), a.syncs);
        assertEquals(10, a.samples.get(0)[0] & 0xFF);
        assertEquals(15, a.samples.get(5)[0] & 0xFF);

        // Movie duration covers the longest track (audio 139ms).
        try (RandomAccessFile raf = new RandomAccessFile(out, "r")) {
            LinkedList<Box> tops = IsoFile.head(raf, buf);
            MovieBox moov = null;
            for (Box b : tops) {
                if (b.type == Box.moov) moov = (MovieBox) b;
            }
            MovieHeaderBox mvhd = moov.findFirst(Box.mvhd);
            assertEquals(133, mvhd.duration);
            assertEquals(3, mvhd.next_track_ID);
        }
    }

    @Test
    void rejectsAudioAsVideo() throws Exception {
        File audio = fixture("a-in.m4a");
        File out = File.createTempFile("muxer-test-err-", ".mp4");
        out.deleteOnExit();
        assertThrows(java.io.IOException.class, () -> Mp4Muxer.mux(audio, audio, out));
    }
}
