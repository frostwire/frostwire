/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Merges one fragmented-MP4 video track and one fragmented-MP4 audio track
 * (YouTube DASH style: init ftyp+moov plus moof/mdat segments) into a single
 * regular (non-fragmented) MP4 with both tracks.
 *
 * <p>Sample descriptions (avc1/avcC, mp4a/esds) are carried over verbatim;
 * only sample tables, track/movie durations and chunk offsets are rebuilt.
 * Per-track decode timelines come from tfdt + trun durations, so tracks with
 * different timescales stay in sync without cross-track math.
 *
 * <p>Deliberate v1 limits (fail fast, never guess):
 * <ul>
 *   <li>one video track in the video input, one audio track in the audio input;</li>
 *   <li>every trun carries data_offset (the YouTube shape); base-data-offset
 *       fragments are supported, duration-empty fragments are skipped;</li>
 *   <li>missing sample duration/size must be covered by tfhd/trex defaults;</li>
 *   <li>movie-level udta/meta and per-track edit lists ride along verbatim;
 *       no new edit lists are generated (DASH timelines start at ~0 and AAC
 *       priming of ~23ms is left uncompensated);</li>
 *   <li>outputs over 4GB use 64-bit chunk offsets and a large mdat header.</li>
 * </ul>
 */
public final class Mp4Muxer {

    private Mp4Muxer() {
    }

    /** Mux {@code videoInput} (fMP4, 1 video track) + {@code audioInput} (fMP4, 1 audio track). */
    public static void mux(File videoInput, File audioInput, File output) throws IOException {
        if (videoInput == null || audioInput == null || output == null) {
            throw new IllegalArgumentException("inputs and output are required");
        }
        ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
        FragmentedTrack video;
        FragmentedTrack audio;
        try (RandomAccessFile vRaf = new RandomAccessFile(videoInput, "r");
             RandomAccessFile aRaf = new RandomAccessFile(audioInput, "r")) {
            video = FragmentedTrack.read(vRaf, buf, true);
            audio = FragmentedTrack.read(aRaf, buf, false);
        }
        if (video.samples.isEmpty()) {
            throw new IOException("no video samples in " + videoInput);
        }
        if (audio.samples.isEmpty()) {
            throw new IOException("no audio samples in " + audioInput);
        }
        Movie movie = buildMovie(video, audio);
        writeMovie(movie, video, audio, videoInput, audioInput, output, buf);
    }

    // ------------------------------------------------------------------ model

    static final class FragSample {
        long fileOffset;
        int size;
        long duration;
        long cto;
        boolean sync;
    }

    /** One parsed fragmented track: verbatim template boxes + ordered samples. */
    static final class FragmentedTrack {
        TrackBox trak;
        int timescale;
        long duration;
        List<FragSample> samples = new ArrayList<>();
        /** Samples per output chunk (one chunk per input fragment that held samples). */
        List<Integer> chunkSampleCounts = new ArrayList<>();

        /**
         * Read the first video (or audio) track, expanding all fragments into
         * an ordered sample list. The RandomAccessFile stays open for later
         * sample copying; only positions are recorded here.
         */
        static FragmentedTrack read(RandomAccessFile raf, ByteBuffer buf, boolean wantVideo)
                throws IOException {
            FragmentedTrack track = new FragmentedTrack();
            LinkedList<Box> tops = parseAll(raf, buf);
            MovieBox moov = firstTop(tops, Box.moov);
            if (moov == null) {
                throw new IOException("no moov box (not an MP4 file?)");
            }
            int handler = wantVideo ? Box.vide : Box.soun;
            for (TrackBox trak : moov.<TrackBox>find(Box.trak)) {
                MediaBox mdia = trak.findFirst(Box.mdia);
                HandlerBox hdlr = mdia == null ? null : mdia.findFirst(Box.hdlr);
                if (hdlr != null && hdlr.handler_type == handler) {
                    track.trak = trak;
                    break;
                }
            }
            if (track.trak == null) {
                throw new IOException("no " + (wantVideo ? "video" : "audio") + " track found");
            }
            MediaBox mdia = track.trak.findFirst(Box.mdia);
            MediaHeaderBox mdhd = mdia.findFirst(Box.mdhd);
            track.timescale = mdhd.timescale;
            if (track.timescale <= 0) {
                throw new IOException("invalid track timescale " + track.timescale);
            }
            java.util.Map<Integer, TrackExtendsBox> trex = trexByTrack(moov);
            List<Long> moofOffsets = scanMoofOffsets(raf);
            List<MovieFragmentBox> moofs = new ArrayList<>(topsMoof(tops));
            if (moofs.size() != moofOffsets.size()) {
                throw new IOException("moof parse/offset mismatch (" + moofs.size()
                        + " parsed vs " + moofOffsets.size() + " scanned)");
            }
            int wantedId = trackId(track.trak);
            long runningDts = -1;
            for (int i = 0; i < moofs.size(); i++) {
                runningDts = readFragment(moofs.get(i), moofOffsets.get(i), wantedId,
                        trex, track, runningDts);
            }
            track.duration = 0;
            for (FragSample s : track.samples) {
                track.duration += s.duration;
            }
            return track;
        }

        /**
         * Full top-level walk that keeps going past mdat payloads (payloads are
         * skipped by size, never parsed). IsoFile.head stops at the first mdat,
         * which would hide later fragments.
         */
        private static LinkedList<Box> parseAll(RandomAccessFile raf, ByteBuffer buf)
                throws IOException {
            raf.seek(0);
            InputChannel ch = new InputChannel(raf.getChannel());
            LinkedList<Box> tops = new LinkedList<>();
            IsoMedia.read(ch, buf, (IsoMedia.OnBoxListener) b -> {
                if (b.parent == null) {
                    tops.add(b);
                }
                return true;
            });
            raf.seek(0);
            return tops;
        }

        private static MovieBox firstTop(LinkedList<Box> tops, int type) {            for (Box b : tops) {
                if (b.type == type && b instanceof MovieBox) {
                    return (MovieBox) b;
                }
            }
            return null;
        }

        private static List<MovieFragmentBox> topsMoof(LinkedList<Box> tops) {
            List<MovieFragmentBox> out = new ArrayList<>();
            for (Box b : tops) {
                if (b.type == Box.moof && b instanceof MovieFragmentBox) {
                    out.add((MovieFragmentBox) b);
                }
            }
            return out;
        }

        private static int trackId(TrackBox trak) throws IOException {
            TrackHeaderBox tkhd = trak.findFirst(Box.tkhd);
            if (tkhd == null) {
                throw new IOException("trak has no tkhd");
            }
            return tkhd.trackId();
        }

        private static java.util.Map<Integer, TrackExtendsBox> trexByTrack(MovieBox moov) {
            java.util.Map<Integer, TrackExtendsBox> map = new java.util.HashMap<>();
            MovieExtendsBox mvex = moov.findFirst(Box.mvex);
            if (mvex == null) {
                return map;
            }
            for (TrackExtendsBox trex : mvex.<TrackExtendsBox>find(Box.trex)) {
                map.put(trex.trackId(), trex);
            }
            return map;
        }

        /** Raw top-level scan for moof file offsets (Box objects carry no offsets). */
        private static List<Long> scanMoofOffsets(RandomAccessFile raf) throws IOException {
            List<Long> out = new ArrayList<>();
            long pos = 0;
            long end = raf.length();
            byte[] hdr = new byte[16];
            while (pos + 8 <= end) {
                raf.seek(pos);
                int n = 0;
                while (n < 8) {
                    int r = raf.read(hdr, n, 8 - n);
                    if (r < 0) break;
                    n += r;
                }
                if (n < 8) break;
                long size = ((hdr[0] & 0xFFL) << 24) | ((hdr[1] & 0xFFL) << 16)
                        | ((hdr[2] & 0xFFL) << 8) | (hdr[3] & 0xFFL);
                int type = ((hdr[4] & 0xFF) << 24) | ((hdr[5] & 0xFF) << 16)
                        | ((hdr[6] & 0xFF) << 8) | (hdr[7] & 0xFF);
                int header = 8;
                if (size == 1) {
                    raf.seek(pos + 8);
                    byte[] ext = new byte[8];
                    raf.readFully(ext);
                    size = 0;
                    for (int k = 0; k < 8; k++) {
                        size = (size << 8) | (ext[k] & 0xFFL);
                    }
                    header = 16;
                } else if (size == 0) {
                    size = end - pos;
                }
                if (size < header || pos + size > end || pos + size < pos) {
                    break;
                }
                if (type == Box.moof) {
                    out.add(pos);
                }
                pos += size;
            }
            return out;
        }

        /**
         * Expand one moof for the wanted track. Returns the running DTS after
         * this fragment (or the incoming value when the fragment held no
         * samples, e.g. duration-empty).
         */
        private static long readFragment(MovieFragmentBox moof, long moofOffset, int wantedId,
                                         java.util.Map<Integer, TrackExtendsBox> trex,
                                         FragmentedTrack track, long runningDts) throws IOException {
            for (TrackFragmentBox traf : moof.<TrackFragmentBox>find(Box.traf)) {
                TrackFragmentHeaderBox tfhd = traf.findFirst(Box.tfhd);
                if (tfhd == null || tfhd.track_ID != wantedId) {
                    continue;
                }
                if (tfhd.durationIsEmpty()) {
                    continue;
                }
                TrackExtendsBox defaults = trex.get(wantedId);
                long base = tfhd.baseDataOffsetPresent() ? tfhd.base_data_offset : moofOffset;
                long fragDts = runningDts;
                TrackFragmentBaseMediaDecodeTimeBox tfdt = traf.findFirst(Box.tfdt);
                if (fragDts < 0) {
                    fragDts = (tfdt != null) ? tfdt.base_media_decode_time : 0;
                }
                int fragSamples = 0;
                for (TrackRunBox trun : traf.<TrackRunBox>find(Box.trun)) {
                    if (!trun.dataOffsetPresent()) {
                        throw new IOException("trun without data_offset (unsupported shape)");
                    }
                    long cursor = base + (long) trun.data_offset;
                    for (int j = 0; j < trun.sample_count; j++) {
                        TrackRunBox.Entry e = trun.entries[j];
                        long dur = trun.sampleDurationPresent() ? unsigned(e.sample_duration)
                                : tfhd.defaultSampleDurationPresent() ? unsigned(tfhd.default_sample_duration)
                                : defaults != null ? unsigned(defaults.default_sample_duration)
                                : -1;
                        int size = trun.sampleSizePresent() ? e.sample_size
                                : tfhd.defaultSampleSizePresent() ? tfhd.default_sample_size
                                : defaults != null ? defaults.default_sample_size
                                : -1;
                        if (dur < 0 || size <= 0) {
                            throw new IOException("sample without duration/size and no defaults"
                                    + " (frag dts=" + fragDts + ")");
                        }
                        int flags;
                        if (trun.sampleFlagsPresent()) {
                            flags = e.sample_flags;
                        } else if (j == 0 && trun.firstSampleFlagsPresent()) {
                            flags = trun.first_sample_flags;
                        } else if (tfhd.defaultSampleFlagsPresent()) {
                            flags = tfhd.default_sample_flags;
                        } else if (defaults != null) {
                            flags = defaults.default_sample_flags;
                        } else {
                            flags = 0;
                        }
                        FragSample s = new FragSample();
                        s.fileOffset = cursor;
                        s.size = size;
                        s.duration = dur;
                        s.cto = trun.sampleCompositionTimeOffsetsPresent()
                                ? e.sample_composition_time_offset : 0;
                        s.sync = (flags & 0x10000) == 0;
                        track.samples.add(s);
                        cursor += size;
                        fragDts += dur;
                        fragSamples++;
                    }
                }
                if (fragSamples > 0) {
                    track.chunkSampleCounts.add(fragSamples);
                    runningDts = fragDts;
                }
            }
            return runningDts;
        }

        private static long unsigned(int v) {
            return v & 0xFFFFFFFFL;
        }
    }

    // ------------------------------------------------------------------ build

    private static final class Movie {
        FileTypeBox ftyp;
        MovieBox moov;
        long totalSampleBytes;
    }

    private static Movie buildMovie(FragmentedTrack video, FragmentedTrack audio) throws IOException {
        Movie movie = new Movie();
        movie.ftyp = standardFtyp();
        movie.moov = new MovieBox();
        movie.moov.boxes.add(movieHeader(video, audio));
        TrackBox videoTrak = buildTrak(video, 1);
        TrackBox audioTrak = buildTrak(audio, 2);
        movie.moov.boxes.add(videoTrak);
        movie.moov.boxes.add(audioTrak);
        // Copy movie-level metadata verbatim when present (titles survive the merge).
        MovieBox videoMoov = parentMoov(video.trak);
        if (videoMoov != null) {
            for (Box b : videoMoov.boxes) {
                if (b.type == Box.udta || b.type == Box.meta) {
                    movie.moov.boxes.add(b);
                }
            }
        }
        movie.totalSampleBytes = 0;
        for (FragSample s : video.samples) {
            movie.totalSampleBytes += s.size;
        }
        for (FragSample s : audio.samples) {
            movie.totalSampleBytes += s.size;
        }
        return movie;
    }

    private static FileTypeBox standardFtyp() {
        FileTypeBox ftyp = new FileTypeBox();
        ftyp.major_brand = Box.isom;
        ftyp.minor_version = 512;
        ftyp.compatible_brands = new int[]{Box.isom, Bits.make4cc("iso2"), Box.avc1, Box.mp41};
        ftyp.update();
        return ftyp;
    }

    private static MovieHeaderBox movieHeader(FragmentedTrack video, FragmentedTrack audio) {
        MovieHeaderBox src = null;
        MovieBox moov = parentMoov(video.trak);
        if (moov != null) {
            src = moov.findFirst(Box.mvhd);
        }
        MovieHeaderBox mvhd = new MovieHeaderBox();
        long videoMs = video.duration * 1000 / video.timescale;
        long audioMs = audio.duration * 1000 / audio.timescale;
        mvhd.timescale = 1000;
        mvhd.duration = Math.max(videoMs, audioMs);
        if (src != null) {
            mvhd.creation_time = src.creation_time;
            mvhd.modification_time = src.modification_time;
            mvhd.rate = src.rate;
            mvhd.volume = src.volume;
        } else {
            mvhd.rate = 0x00010000;
            mvhd.volume = 0x0100;
        }
        mvhd.next_track_ID = 3;
        mvhd.update();
        return mvhd;
    }

    private static MovieBox parentMoov(TrackBox trak) {
        Box p = trak.parent;
        while (p != null && !(p instanceof MovieBox)) {
            p = p.parent;
        }
        return (MovieBox) p;
    }

    private static TrackBox buildTrak(FragmentedTrack track, int outId) throws IOException {
        TrackBox trak = track.trak;
        TrackHeaderBox tkhd = trak.findFirst(Box.tkhd);
        if (tkhd == null) {
            throw new IOException("trak has no tkhd");
        }
        tkhd.trackId(outId);
        tkhd.duration = track.duration * 1000 / track.timescale;
        MediaBox mdia = trak.findFirst(Box.mdia);
        if (mdia == null) {
            throw new IOException("trak has no mdia");
        }
        MediaHeaderBox mdhd = mdia.findFirst(Box.mdhd);
        if (mdhd == null) {
            throw new IOException("trak has no mdhd");
        }
        mdhd.duration = track.duration;
        MediaInformationBox minf = mdia.findFirst(Box.minf);
        if (minf == null) {
            throw new IOException("trak has no minf");
        }
        SampleTableBox stbl = minf.findFirst(Box.stbl);
        if (stbl == null) {
            throw new IOException("trak has no stbl");
        }
        SampleTableBox fresh = new SampleTableBox();
        SampleDescriptionBox stsd = stbl.findFirst(Box.stsd);
        if (stsd == null) {
            throw new IOException("trak has no stsd");
        }
        fresh.boxes.add(stsd);
        fresh.boxes.add(buildStts(track));
        fresh.boxes.add(buildStsc(track));
        fresh.boxes.add(buildStsz(track));
        fresh.boxes.add(new ChunkOffsetBox());
        if (needsStss(track)) {
            fresh.boxes.add(buildStss(track));
        }
        if (needsCtts(track)) {
            fresh.boxes.add(buildCtts(track));
        }
        minf.boxes.remove(stbl);
        minf.boxes.add(fresh);
        minf.update();
        mdia.update();
        trak.update();
        return trak;
    }

    private static TimeToSampleBox buildStts(FragmentedTrack track) {
        List<TimeToSampleBox.Entry> runs = new ArrayList<>();
        long currentDelta = -1;
        int currentCount = 0;
        for (FragSample s : track.samples) {
            if (s.duration != currentDelta) {
                if (currentCount > 0) {
                    TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
                    e.sample_count = currentCount;
                    e.sample_delta = (int) currentDelta;
                    runs.add(e);
                }
                currentDelta = s.duration;
                currentCount = 1;
            } else {
                currentCount++;
            }
        }
        if (currentCount > 0) {
            TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
            e.sample_count = currentCount;
            e.sample_delta = (int) currentDelta;
            runs.add(e);
        }
        TimeToSampleBox stts = new TimeToSampleBox();
        stts.entry_count = runs.size();
        stts.entries = runs.toArray(new TimeToSampleBox.Entry[0]);
        stts.update();
        return stts;
    }

    private static SampleToChunkBox buildStsc(FragmentedTrack track) {
        SampleToChunkBox stsc = new SampleToChunkBox();
        stsc.entry_count = track.chunkSampleCounts.size();
        stsc.entries = new SampleToChunkBox.Entry[stsc.entry_count];
        for (int i = 0; i < stsc.entry_count; i++) {
            SampleToChunkBox.Entry e = new SampleToChunkBox.Entry();
            e.first_chunk = i + 1;
            e.samples_per_chunk = track.chunkSampleCounts.get(i);
            e.sample_description_index = 1;
            stsc.entries[i] = e;
        }
        stsc.update();
        return stsc;
    }

    private static SampleSizeBox buildStsz(FragmentedTrack track) {
        SampleSizeBox stsz = new SampleSizeBox();
        stsz.sample_size = 0; // sizes vary: per-sample table follows
        stsz.sample_count = track.samples.size();
        stsz.entries = new SampleSizeBox.Entry[stsz.sample_count];
        for (int i = 0; i < stsz.sample_count; i++) {
            SampleSizeBox.Entry e = new SampleSizeBox.Entry();
            e.entry_size = track.samples.get(i).size;
            stsz.entries[i] = e;
        }
        stsz.update();
        return stsz;
    }

    private static boolean needsStss(FragmentedTrack track) {
        for (FragSample s : track.samples) {
            if (!s.sync) {
                return true;
            }
        }
        return false;
    }

    private static SyncSampleBox buildStss(FragmentedTrack track) {
        List<Integer> syncs = new ArrayList<>();
        for (int i = 0; i < track.samples.size(); i++) {
            if (track.samples.get(i).sync) {
                syncs.add(i + 1);
            }
        }
        SyncSampleBox stss = new SyncSampleBox();
        stss.entry_count = syncs.size();
        stss.entries = new SyncSampleBox.Entry[syncs.size()];
        for (int i = 0; i < syncs.size(); i++) {
            SyncSampleBox.Entry e = new SyncSampleBox.Entry();
            e.sample_number = syncs.get(i);
            stss.entries[i] = e;
        }
        stss.update();
        return stss;
    }

    private static boolean needsCtts(FragmentedTrack track) {
        for (FragSample s : track.samples) {
            if (s.cto != 0) {
                return true;
            }
        }
        return false;
    }

    private static CompositionOffsetBox buildCtts(FragmentedTrack track) {
        List<CompositionOffsetBox.Entry> runs = new ArrayList<>();
        long currentCto = Long.MIN_VALUE;
        int currentCount = 0;
        for (FragSample s : track.samples) {
            if (s.cto != currentCto) {
                if (currentCount > 0) {
                    CompositionOffsetBox.Entry e = new CompositionOffsetBox.Entry();
                    e.sample_count = currentCount;
                    e.sample_offset = (int) currentCto;
                    runs.add(e);
                }
                currentCto = s.cto;
                currentCount = 1;
            } else {
                currentCount++;
            }
        }
        if (currentCount > 0) {
            CompositionOffsetBox.Entry e = new CompositionOffsetBox.Entry();
            e.sample_count = currentCount;
            e.sample_offset = (int) currentCto;
            runs.add(e);
        }
        CompositionOffsetBox ctts = new CompositionOffsetBox();
        boolean v1 = false;
        for (FragSample s : track.samples) {
            if (s.cto < 0) {
                v1 = true;
                break;
            }
        }
        ctts.version = v1 ? (byte) 1 : (byte) 0;
        ctts.entry_count = runs.size();
        ctts.entries = runs.toArray(new CompositionOffsetBox.Entry[0]);
        ctts.update();
        return ctts;
    }

    // ------------------------------------------------------------------ write

    private static void writeMovie(Movie movie, FragmentedTrack video, FragmentedTrack audio,
                                   File videoInput, File audioInput, File output, ByteBuffer buf)
            throws IOException {
        movie.moov.update();
        long moovSize = movie.moov.length() + 8;
        long ftypSize = movie.ftyp.length() + 8;
        boolean largeMdat = movie.totalSampleBytes > 0xFFFFFFFFL - 16;
        // First pass with zero offsets to learn moov size (offsets don't change it).
        fillChunkOffsets(movie, video, audio, ftypSize + moovSize + (largeMdat ? 16 : 8));
        movie.moov.update();
        moovSize = movie.moov.length() + 8;
        fillChunkOffsets(movie, video, audio, ftypSize + moovSize + (largeMdat ? 16 : 8));
        movie.moov.update();
        swapChunkOffsetTables(movie, needsLargeOffsets(movie));
        movie.moov.update();
        try (RandomAccessFile vRaf = new RandomAccessFile(videoInput, "r");
             RandomAccessFile aRaf = new RandomAccessFile(audioInput, "r");
             RandomAccessFile out = new RandomAccessFile(output, "rw")) {
            out.setLength(0);
            OutputChannel ch = new OutputChannel(out.getChannel());
            IsoMedia.write(ch, singleList(movie.ftyp), buf, IsoMedia.OnBoxListener.ALL);
            IsoMedia.write(ch, singleList((Box) movie.moov), buf, IsoMedia.OnBoxListener.ALL);
            writeMdatHeader(ch, buf, movie.totalSampleBytes, largeMdat);
            copySamples(vRaf, video, out);
            copySamples(aRaf, audio, out);
        }
    }

    private static LinkedList<Box> singleList(Box b) {
        LinkedList<Box> list = new LinkedList<>();
        list.add(b);
        return list;
    }

    private static boolean needsLargeOffsets(Movie movie) {
        for (TrackBox trak : movie.moov.<TrackBox>find(Box.trak)) {
            ChunkOffsetBox stco = outputStbl(trak).findFirst(Box.stco);
            if (stco != null && stco.entries != null) {
                for (ChunkOffsetBox.Entry e : stco.entries) {
                    if ((e.chunk_offset & 0xFFFFFFFFL) >= 0x100000000L) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Chunk file offsets for one track in file order, given the mdat payload start. */
    private static long[] chunkOffsetsFor(FragmentedTrack track, long dataStart) {
        long[] offsets = new long[track.chunkSampleCounts.size()];
        long cursor = dataStart;
        int si = 0;
        for (int c = 0; c < offsets.length; c++) {
            offsets[c] = cursor;
            int n = track.chunkSampleCounts.get(c);
            for (int k = 0; k < n; k++) {
                cursor += track.samples.get(si++).size;
            }
        }
        return offsets;
    }

    private static long trackBytes(FragmentedTrack track) {
        long total = 0;
        for (FragSample s : track.samples) {
            total += s.size;
        }
        return total;
    }

    /** Fill one track's stco from computed offsets; returns the next free file position. */
    private static long fillOneTrack(Movie movie, int handler, FragmentedTrack track, long dataStart) {
        long[] offsets = chunkOffsetsFor(track, dataStart);
        SampleTableBox stbl = outputStbl(movie, handler);
        ChunkOffsetBox stco = stbl.findFirst(Box.stco);
        if (stco == null) {
            throw new IllegalStateException("fresh stbl lost its stco");
        }
        stco.entry_count = offsets.length;
        stco.entries = new ChunkOffsetBox.Entry[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            ChunkOffsetBox.Entry e = new ChunkOffsetBox.Entry();
            // Truncated only when co64 replaces this table below (sizes unaffected).
            e.chunk_offset = (int) offsets[i];
            stco.entries[i] = e;
        }
        stco.update();
        return dataStart + trackBytes(track);
    }

    private static void fillChunkOffsets(Movie movie, FragmentedTrack video, FragmentedTrack audio,
                                         long dataStart) {
        long cursor = fillOneTrack(movie, Box.vide, video, dataStart);
        fillOneTrack(movie, Box.soun, audio, cursor);
    }

    /** The rebuilt stbl of an output trak, matched by media handler type. */
    private static SampleTableBox outputStbl(Movie movie, int handler) {
        for (TrackBox trak : movie.moov.<TrackBox>find(Box.trak)) {
            MediaBox mdia = trak.findFirst(Box.mdia);
            HandlerBox hdlr = mdia == null ? null : mdia.findFirst(Box.hdlr);
            if (hdlr != null && hdlr.handler_type == handler) {
                return outputStbl(trak);
            }
        }
        throw new IllegalStateException("output trak not found for handler " + handler);
    }

    private static SampleTableBox outputStbl(TrackBox trak) {
        MediaBox mdia = trak.findFirst(Box.mdia);
        MediaInformationBox minf = mdia == null ? null : mdia.findFirst(Box.minf);
        SampleTableBox stbl = minf == null ? null : minf.findFirst(Box.stbl);
        if (stbl == null) {
            throw new IllegalStateException("output trak has no stbl");
        }
        return stbl;
    }

    private static void swapChunkOffsetTables(Movie movie, boolean large) {
        if (!large) {
            return;
        }
        for (TrackBox trak : movie.moov.<TrackBox>find(Box.trak)) {
            MediaBox mdia = trak.findFirst(Box.mdia);
            MediaInformationBox minf = mdia == null ? null : mdia.findFirst(Box.minf);
            SampleTableBox stbl = minf == null ? null : minf.findFirst(Box.stbl);
            if (stbl == null) {
                continue;
            }
            ChunkOffsetBox stco = stbl.findFirst(Box.stco);
            if (stco == null) {
                continue;
            }
            ChunkLargeOffsetBox co64 = new ChunkLargeOffsetBox();
            co64.entry_count = stco.entry_count;
            co64.entries = new ChunkLargeOffsetBox.Entry[stco.entry_count];
            for (int i = 0; i < stco.entry_count; i++) {
                ChunkLargeOffsetBox.Entry e = new ChunkLargeOffsetBox.Entry();
                e.chunk_offset = stco.entries[i].chunk_offset & 0xFFFFFFFFL;
                co64.entries[i] = e;
            }
            co64.update();
            stbl.boxes.remove(stco);
            stbl.boxes.add(co64);
            stbl.update();
        }
    }

    private static void writeMdatHeader(OutputChannel ch, ByteBuffer buf, long payloadBytes,
                                        boolean large) throws IOException {
        buf.clear();
        if (large) {
            buf.putInt(1);
            buf.putInt(Box.mdat);
            buf.putLong(payloadBytes + 16);
        } else {
            buf.putInt((int) (payloadBytes + 8));
            buf.putInt(Box.mdat);
        }
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    private static void copySamples(RandomAccessFile in, FragmentedTrack track, RandomAccessFile out)
            throws IOException {
        byte[] bounce = new byte[256 * 1024];
        for (FragSample s : track.samples) {
            in.seek(s.fileOffset);
            long left = s.size;
            while (left > 0) {
                int n = (int) Math.min(bounce.length, left);
                in.readFully(bounce, 0, n);
                out.write(bounce, 0, n);
                left -= n;
            }
        }
    }
}
