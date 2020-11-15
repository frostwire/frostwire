/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.mp4;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Mp4Demuxer {
    public static void audio(File input, File output, Mp4Info inf, DemuxerListener l) throws IOException {
        RandomAccessFile in = new RandomAccessFile(input, "r");
        RandomAccessFile out = new RandomAccessFile(output, "rw");
        out.setLength(0);
        try {
            ByteBuffer buf = ByteBuffer.allocate(100 * 1024);
            LinkedList<Box> head = IsoFile.head(in, buf);
            // find audio track
            SoundMediaHeaderBox smhd = Box.findFirst(head, Box.smhd);
            TrackBox trak = (TrackBox) smhd.parent.parent.parent;
            TrackHeaderBox tkhd = trak.findFirst(Box.tkhd);
            boolean fragments = Box.findFirst(head, Box.mvex) != null;
            if (fragments) {
                muxFragments(new RandomAccessFile[]{in}, out, inf, buf, l);
            } else {
                trackSimple(tkhd.trackId(), in, out, inf, buf, l);
            }
        } finally {
            IO.close(in);
            IO.close(out);
        }
    }

    public static void muxFragments(File video, File audio, File output, Mp4Info inf, DemuxerListener l) throws IOException {
        RandomAccessFile v_in = new RandomAccessFile(video, "r");
        RandomAccessFile a_in = new RandomAccessFile(audio, "r");
        RandomAccessFile out = new RandomAccessFile(output, "rw");
        out.setLength(0);
        try {
            ByteBuffer buf = ByteBuffer.allocate(100 * 1024);
            muxFragments(new RandomAccessFile[]{v_in, a_in}, out, inf, buf, l);
        } finally {
            IO.close(v_in);
            IO.close(a_in);
            IO.close(out);
        }
    }

    private static void trackSimple(int id, RandomAccessFile input, RandomAccessFile output, Mp4Info inf, ByteBuffer buf, final DemuxerListener l) throws IOException {
        int trackId = id;
        final InputChannel in = new InputChannel(input.getChannel());
        final OutputChannel out = new OutputChannel(output.getChannel());
        final LinkedList<Box> boxes = new LinkedList<>();
        IsoMedia.read(in, input.length(), null, buf, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                notifyCount(l, in.count());
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });
        FileTypeBox ftyp = Box.findFirst(boxes, Box.ftyp);
        ftyp.major_brand = inf.majorBrand;
        ftyp.minor_version = 0;
        ftyp.compatible_brands = inf.compatibleBrands;
        MovieBox moov = Box.findFirst(boxes, Box.moov);
        TrackBox trak = null;
        // remove other tracks
        ListIterator<Box> it = moov.boxes.listIterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (b.type != Box.trak) {
                continue;
            }
            TrackHeaderBox tkhd = b.findFirst(Box.tkhd);
            if (tkhd.trackId() != trackId) {
                it.remove();
            } else {
                trak = (TrackBox) b;
            }
        }
        // some fixes
        TrackHeaderBox tkhd = Box.findFirst(boxes, Box.tkhd);
        tkhd.enabled(true);
        tkhd.inMovie(true);
        tkhd.inPreview(true);
        tkhd.inPoster(true);
        MediaHeaderBox mdhd = Box.findFirst(boxes, Box.mdhd);
        mdhd.language("eng");
        // remove old udta boxes
        for (UserDataBox b : moov.<UserDataBox>find(Box.udta)) {
            moov.boxes.remove(b);
        }
        UserDataBox udta = createUdta(inf);
        moov.boxes.add(udta);
        MediaDataBox mdat = Box.findFirst(boxes, Box.mdat);
        SampleToChunkBox stsc = trak.findFirst(Box.stsc);
        SampleSizeBox stsz = trak.findFirst(Box.stsz);
        ChunkOffsetBox stco = trak.findFirst(Box.stco);
        int[] chunkSize = new int[stco.entry_count];
        int chunkIdx = 0;
        int sampleIdx = 0;
        for (int i = 0; i < stsc.entry_count; i++) {
            int a = stsc.entries[i].first_chunk;
            int b = i < stsc.entry_count - 1 ? stsc.entries[i + 1].first_chunk : a + 1;
            for (int j = a; j < b; j++) {
                int sampleSize = 0;
                for (int k = 0; k < stsc.entries[i].samples_per_chunk; k++) {
                    sampleSize += stsz.sample_size != 0 ? stsz.sample_size : stsz.entries[sampleIdx].entry_size;
                    sampleIdx++;
                }
                chunkSize[chunkIdx] += sampleSize;
                chunkIdx++;
            }
        }
        int[] chunkOffsetOrg = new int[stco.entry_count];
        int offset = (int) (ContainerBox.length(boxes) - mdat.length());
        for (int i = 0; i < stco.entry_count; i++) {
            chunkOffsetOrg[i] = stco.entries[i].chunk_offset;
            stco.entries[i].chunk_offset = offset;
            offset += chunkSize[i];
        }
        IsoMedia.write(out, boxes, buf, IsoMedia.OnBoxListener.ALL);
        for (int i = 0; i < stco.entry_count; i++) {
            int pos = (int) in.count();
            int skp = chunkOffsetOrg[i] - pos;
            if (skp == 0 && i == stco.entry_count - 1) {
                // we are at the end with no more data to copy
                continue;
            }
            IO.skip(in, skp, buf);
            notifyCount(l, in.count());
            IO.copy(in, out, chunkSize[i], buf);
            notifyCount(l, in.count());
        }
    }

    private static void muxFragments(RandomAccessFile[] inputs, RandomAccessFile output, Mp4Info inf, ByteBuffer buf, DemuxerListener l) throws IOException {
        int n = inputs.length;
        InputChannel[] ins = new InputChannel[n];
        FragmentCtx[] ctxs = new FragmentCtx[n];
        for (int i = 0; i < n; i++) {
            RandomAccessFile input = inputs[i];
            ins[i] = new InputChannel(input.getChannel());
            ctxs[i] = new FragmentCtx(input.length());
        }
        OutputChannel out = new OutputChannel(output.getChannel());
        for (int i = 0; i < n; i++) {
            FragmentCtx ctx = ctxs[i];
            ctx.moov = readUntil(ins[i], Box.moov, buf);
            ctx.trex = ctx.moov.findFirst(Box.trex);
        }
        long mdatOffset = 0;
        boolean readChunk;
        do {
            readChunk = false;
            for (int i = 0; i < n; i++) {
                InputChannel in = ins[i];
                FragmentCtx ctx = ctxs[i];
                if (in.count() >= ctx.len) {
                    continue;
                }
                readChunk = true;
                ctx.moof = readUntil(in, Box.moof, buf);
                ctx.mdat = readUntil(in, Box.mdat, buf);
            }
            if (mdatOffset == 0) {
                mdatOffset = calcMdatOffset(ctxs, inf);
                output.seek(mdatOffset);
            }
            long readCount = 0;
            for (int i = 0; i < n; i++) {
                InputChannel in = ins[i];
                FragmentCtx ctx = ctxs[i];
                if (in.count() >= ctx.len) {
                    continue;
                }
                processChunk(ctx, mdatOffset + out.count());
                IO.copy(in, out, ctx.mdat.length(), buf);
                readCount += in.count();
            }
            notifyCount(l, readCount);
        } while (readChunk);
        LinkedList<Box> boxes = new LinkedList<>();
        FileTypeBox ftyp = new FileTypeBox();
        ftyp.major_brand = inf.majorBrand;
        ftyp.minor_version = 0;
        ftyp.compatible_brands = inf.compatibleBrands;
        boxes.add(ftyp);
        MovieBox moov = new MovieBox();
        moov.boxes.add(ctxs[0].moov.findFirst(Box.mvhd));
        for (int i = 0; i < n; i++) {
            TrackBox trak = createTrak(i + 1, ctxs[i]);
            moov.boxes.add(trak);
        }
        boxes.add(moov);
        UserDataBox udta = createUdta(inf);
        moov.boxes.add(udta);
        MediaDataBox mdat = new MediaDataBox();
        mdat.length(0);
        boxes.add(mdat);
        long len = ContainerBox.length(boxes); // this update the boxes
        if (len > mdatOffset) {
            throw new IOException("Movie header bigger than mdat offset");
        }
        mdat.length(output.length() - len);
        output.seek(0);
        IsoMedia.write(out, boxes, buf, IsoMedia.OnBoxListener.ALL);
    }

    private static <T extends Box> T readNext(final InputChannel ch, final ByteBuffer buf) throws IOException {
        IO.read(ch, 8, buf);
        int size = buf.getInt();
        int type = buf.getInt();
        Long largesize = null;
        if (size == 1) {
            IO.read(ch, 8, buf);
            largesize = buf.getLong();
        }
        byte[] usertype = null;
        if (type == Box.uuid) {
            usertype = new byte[16];
            IO.read(ch, 16, buf);
            buf.get(usertype);
        }
        Box b = Box.empty(type);
        b.size = size;
        b.largesize = largesize;
        b.usertype = usertype;
        long r = ch.count();
        b.read(ch, buf);
        r = ch.count() - r;
        long length = b.length();
        if (r < length) {
            if (type != Box.mdat) {
                IsoMedia.read(ch, length - r, b, buf, null);
            }
        }
        return (T) b;
    }

    private static <T extends Box> T readUntil(final InputChannel ch, int type, final ByteBuffer buf) throws IOException {
        Box b = null;
        try {
            while ((b = readNext(ch, buf)).type != type) ;
        } catch (EOFException e) {
            // ignore
        }
        return (T) b;
    }

    private static int calcMdatOffset(FragmentCtx[] ctxs, Mp4Info inf) {
        int len = 0;
        for (FragmentCtx ctx : ctxs) {
            MovieFragmentBox moof = ctx.moof;
            MediaDataBox mdat = ctx.mdat;
            int n = (int) (ctx.len / mdat.size);
            len += n * moof.size * 4;
        }
        len += 100000; // header extra room
        len += inf.jpg != null ? inf.jpg.length : 0;
        return len;
    }

    private static void processChunk(FragmentCtx ctx, long offset) {
        TrackFragmentHeaderBox tfhd = ctx.moof.findFirst(Box.tfhd);
        TrackRunBox trun = ctx.moof.findFirst(Box.trun);
        SampleToChunkBox.Entry stscEntry = new SampleToChunkBox.Entry();
        stscEntry.first_chunk = ctx.chunkNumber;
        stscEntry.samples_per_chunk = trun.sample_count;
        stscEntry.sample_description_index = 1;
        ctx.stscList.add(stscEntry);
        ChunkOffsetBox.Entry stcoEntry = new ChunkOffsetBox.Entry();
        stcoEntry.chunk_offset = (int) offset;
        ctx.stcoList.add(stcoEntry);
        boolean first = true;
        for (TrackRunBox.Entry entry : trun.entries) {
            if (trun.sampleDurationPresent()) {
                if (ctx.sttsList.isEmpty() ||
                        ctx.sttsList.get(ctx.sttsList.size() - 1).sample_delta != entry.sample_duration) {
                    TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
                    e.sample_count = 1;
                    e.sample_delta = entry.sample_duration;
                    ctx.sttsList.add(e);
                } else {
                    TimeToSampleBox.Entry e = ctx.sttsList.get(ctx.sttsList.size() - 1);
                    e.sample_count += 1;
                }
            } else {
                if (tfhd.defaultSampleDurationPresent()) {
                    TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
                    e.sample_count = 1;
                    e.sample_delta = tfhd.default_sample_duration;
                    ctx.sttsList.add(e);
                } else {
                    TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
                    e.sample_count = 1;
                    e.sample_delta = ctx.trex.default_sample_duration;
                    ctx.sttsList.add(e);
                }
            }
            if (trun.sampleCompositionTimeOffsetsPresent()) {
                if (ctx.cttsList.isEmpty() ||
                        ctx.cttsList.get(ctx.cttsList.size() - 1).sample_offset != entry.sample_composition_time_offset) {
                    CompositionOffsetBox.Entry e = new CompositionOffsetBox.Entry();
                    e.sample_count = 1;
                    e.sample_offset = entry.sample_composition_time_offset;
                    ctx.cttsList.add(e);
                } else {
                    CompositionOffsetBox.Entry e = ctx.cttsList.get(ctx.cttsList.size() - 1);
                    e.sample_count += 1;
                }
            }
            SampleSizeBox.Entry stszEntry = new SampleSizeBox.Entry();
            stszEntry.entry_size = entry.sample_size;
            ctx.stszList.add(stszEntry);
            int sampleFlags;
            if (trun.sampleFlagsPresent()) {
                sampleFlags = entry.sample_flags;
            } else {
                if (first && trun.firstSampleFlagsPresent()) {
                    sampleFlags = trun.first_sample_flags;
                } else {
                    if (tfhd.defaultSampleFlagsPresent()) {
                        sampleFlags = tfhd.default_sample_flags;
                    } else {
                        sampleFlags = ctx.trex.default_sample_flags;
                    }
                }
            }
            // is difference sample
            if (((sampleFlags & 0x00010000) >> 16) > 0) {
                // iframe
                SyncSampleBox.Entry e = new SyncSampleBox.Entry();
                e.sample_number = ctx.sampleNumber;
                ctx.stssList.add(e);
            }
            ctx.sampleNumber++;
            first = false;
        }
        ctx.chunkNumber++;
    }

    private static TrackBox createTrak(int id, FragmentCtx ctx) {
        SampleTableBox stbl = ctx.moov.findFirst(Box.stbl);
        TimeToSampleBox stts = stbl.findFirst(Box.stts);
        if (stts != null) {
            stts.entry_count = ctx.sttsList.size();
            stts.entries = ctx.sttsList.toArray(new TimeToSampleBox.Entry[0]);
        }
        CompositionOffsetBox ctts = stbl.findFirst(Box.ctts);
        if (ctts != null) {
            ctts.entry_count = ctx.cttsList.size();
            ctts.entries = ctx.cttsList.toArray(new CompositionOffsetBox.Entry[0]);
        }
        SyncSampleBox stss = stbl.findFirst(Box.stss);
        if (stss != null) {
            stss.entry_count = ctx.stssList.size();
            stss.entries = ctx.stssList.toArray(new SyncSampleBox.Entry[0]);
        }
        SampleSizeBox stsz = stbl.findFirst(Box.stsz);
        if (stsz != null) {
            stsz.sample_size = 0;
            stsz.sample_count = ctx.stszList.size();
            stsz.entries = ctx.stszList.toArray(new SampleSizeBox.Entry[0]);
        }
        SampleToChunkBox stsc = stbl.findFirst(Box.stsc);
        if (stsc != null) {
            stsc.entry_count = ctx.stscList.size();
            stsc.entries = ctx.stscList.toArray(new SampleToChunkBox.Entry[0]);
        }
        ChunkOffsetBox stco = stbl.findFirst(Box.stco);
        if (stco != null) {
            stco.entry_count = ctx.stcoList.size();
            stco.entries = ctx.stcoList.toArray(new ChunkOffsetBox.Entry[0]);
        }
        TrackBox trak = ctx.moov.findFirst(Box.trak);
        // some fixes
        TrackHeaderBox tkhd = trak.findFirst(Box.tkhd);
        tkhd.trackId(id);
        tkhd.enabled(true);
        tkhd.inMovie(true);
        tkhd.inPreview(true);
        tkhd.inPoster(true);
        MediaHeaderBox mdhd = trak.findFirst(Box.mdhd);
        mdhd.language("eng");
        return trak;
    }

    private static UserDataBox createUdta(Mp4Info inf) {
        UserDataBox udta = new UserDataBox();
        MetaBox meta = new MetaBox();
        udta.boxes.add(meta);
        HandlerBox hdlr = new HandlerBox();
        hdlr.handler_type = Bits.make4cc("mdir");
        meta.boxes.add(hdlr);
        AppleItemListBox ilst = new AppleItemListBox();
        meta.boxes.add(ilst);
        if (inf.title != null) {
            AppleNameBox cnam = new AppleNameBox();
            cnam.value(inf.title);
            ilst.boxes.add(cnam);
        }
        if (inf.author != null) {
            AppleArtistBox cART = new AppleArtistBox();
            cART.value(inf.author);
            ilst.boxes.add(cART);
            AppleAlbumArtistBox aART = new AppleAlbumArtistBox();
            aART.value(inf.author);
            ilst.boxes.add(aART);
        }
        if (inf.album != null) {
            AppleAlbumBox calb = new AppleAlbumBox();
            calb.value(inf.album);
            ilst.boxes.add(calb);
        }
        AppleMediaTypeBox stik = new AppleMediaTypeBox();
        stik.value(1);
        ilst.boxes.add(stik);
        //moov/udta/meta/ilst/covr/data
        if (inf.jpg != null) {
            AppleCoverBox covr = new AppleCoverBox();
            covr.setJpg(inf.jpg);
            ilst.boxes.add(covr);
        }
        return udta;
    }

    private static void notifyCount(DemuxerListener l, long count) {
        if (l != null) {
            l.onRead(count);
        }
    }

    public interface DemuxerListener {
        void onRead(long count);
    }

    private static final class FragmentCtx {
        final long len;
        final LinkedList<TimeToSampleBox.Entry> sttsList;
        final LinkedList<CompositionOffsetBox.Entry> cttsList;
        final LinkedList<SyncSampleBox.Entry> stssList;
        final LinkedList<SampleSizeBox.Entry> stszList;
        final LinkedList<SampleToChunkBox.Entry> stscList;
        final LinkedList<ChunkOffsetBox.Entry> stcoList;
        MovieBox moov;
        TrackExtendsBox trex;
        MovieFragmentBox moof;
        MediaDataBox mdat;
        int sampleNumber;
        int chunkNumber;
        public FragmentCtx(long len) {
            this.len = len;
            sttsList = new LinkedList<>();
            cttsList = new LinkedList<>();
            stssList = new LinkedList<>();
            stszList = new LinkedList<>();
            stscList = new LinkedList<>();
            stcoList = new LinkedList<>();
            sampleNumber = 1;
            chunkNumber = 1;
        }
    }
}
