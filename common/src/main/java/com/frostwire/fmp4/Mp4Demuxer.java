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

package com.frostwire.fmp4;

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

    public static void track(int id, Mp4Tags tags, RandomAccessFile in, RandomAccessFile out) throws IOException {
        LinkedList<Box> head = IsoMedia.head(in);
        boolean fragments = Box.findFirst(head, Box.mvex) != null;
        track(id, fragments, head, tags, in, out);
    }

    public static void audio(File intput, File output, Mp4Tags tags) throws IOException {
        RandomAccessFile in = new RandomAccessFile(intput, "r");
        RandomAccessFile out = new RandomAccessFile(output, "rw");

        try {
            LinkedList<Box> head = IsoMedia.head(in);

            // find audio track
            SoundMediaHeaderBox smhd = Box.findFirst(head, Box.smhd);
            TrackBox trak = (TrackBox) smhd.parent.parent.parent;
            TrackHeaderBox tkhd = trak.findFirst(Box.tkhd);

            boolean fragments = Box.findFirst(head, Box.mvex) != null;
            track(tkhd.trackId(), fragments, head, tags, in, out);

        } finally {
            close(in);
            close(out);
        }
    }

    private static void track(int id, boolean fragments, LinkedList<Box> head, Mp4Tags tags, RandomAccessFile in, RandomAccessFile out) throws IOException {
        out.setLength(0);

        if (fragments) {
            trackFragments(id, head, tags, in, out);
        } else {
            trackSimple(id, tags, in, out);
        }
    }

    private static void trackFragments(int id, LinkedList<Box> head, Mp4Tags tags, RandomAccessFile fIn, RandomAccessFile fOut) throws IOException {
        final int trackId = id;
        final ByteBuffer buf = ByteBuffer.allocate(10 * 1024);
        final int mdatOffset = calcMdatOffset(head, fIn.length(), tags);
        fOut.seek(mdatOffset);

        final InputChannel in = new InputChannel(fIn.getChannel());
        final OutputChannel out = new OutputChannel(fOut.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        final LinkedList<TimeToSampleBox.Entry> sttsList = new LinkedList<>();
        final LinkedList<CompositionOffsetBox.Entry> cttsList = new LinkedList<>();
        final LinkedList<SyncSampleBox.Entry> stssList = new LinkedList<>();
        final LinkedList<SampleSizeBox.Entry> stszList = new LinkedList<>();
        final LinkedList<SampleToChunkBox.Entry> stscList = new LinkedList<>();
        final LinkedList<ChunkOffsetBox.Entry> stcoList = new LinkedList<>();

        IsoMedia.read(in, fIn.length(), null, new IsoMedia.OnBoxListener() {

            TrackExtendsBox trex;
            TrackRunBox trun;
            MediaDataBox mdat;
            int chunkNumber = 1;
            int chunkOffset = mdatOffset;
            int sampleNumber = 1;

            @Override
            public boolean onBox(Box b) {
                if (b.parent == null && b.type != Box.mdat && b.type != Box.moof) {
                    boxes.add(b);
                }

                if (b.type == Box.trex) {
                    trex = (TrackExtendsBox) b;
                    if (trex.track_ID != trackId) {
                        trex = null;
                    }
                }

                if (b.type == Box.trun) {
                    trun = (TrackRunBox) b;
                }

                if (b.type == Box.mdat) {
                    mdat = (MediaDataBox) b;
                }

                if (b.type != Box.mdat) {
                    return true;
                }

                TrackFragmentHeaderBox tfhd = trun.parent.findFirst(Box.tfhd);

                if (tfhd.track_ID != trackId) {
                    return true;
                }

                SampleToChunkBox.Entry stscEntry = new SampleToChunkBox.Entry();
                stscEntry.first_chunk = chunkNumber;
                stscEntry.samples_per_chunk = trun.sample_count;
                stscEntry.sample_description_index = 1;
                stscList.add(stscEntry);

                ChunkOffsetBox.Entry stcoEntry = new ChunkOffsetBox.Entry();
                stcoEntry.chunk_offset = chunkOffset;
                stcoList.add(stcoEntry);

                boolean first = true;
                for (TrackRunBox.Entry entry : trun.entries) {
                    if (trun.sampleDurationPresent()) {
                        if (sttsList.isEmpty() ||
                                sttsList.get(sttsList.size() - 1).sample_delta != entry.sample_duration) {
                            TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
                            e.sample_count = 1;
                            e.sample_delta = entry.sample_duration;
                            sttsList.add(e);
                        } else {
                            TimeToSampleBox.Entry e = sttsList.get(sttsList.size() - 1);
                            e.sample_count += 1;
                        }
                    } else {
                        if (tfhd.defaultSampleDurationPresent()) {
                            TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
                            e.sample_count = 1;
                            e.sample_delta = tfhd.default_sample_duration;
                            sttsList.add(e);
                        } else {
                            TimeToSampleBox.Entry e = new TimeToSampleBox.Entry();
                            e.sample_count = 1;
                            e.sample_delta = trex.default_sample_duration;
                            sttsList.add(e);
                        }
                    }

                    if (trun.sampleCompositionTimeOffsetsPresent()) {
                        if (cttsList.isEmpty() ||
                                cttsList.get(cttsList.size() - 1).sample_offset != entry.sample_composition_time_offset) {
                            CompositionOffsetBox.Entry e = new CompositionOffsetBox.Entry();
                            e.sample_count = 1;
                            e.sample_offset = entry.sample_composition_time_offset;
                            cttsList.add(e);
                        } else {
                            CompositionOffsetBox.Entry e = cttsList.get(cttsList.size() - 1);
                            e.sample_count += 1;
                        }
                    }

                    SampleSizeBox.Entry stszEntry = new SampleSizeBox.Entry();
                    stszEntry.entry_size = entry.sample_size;
                    stszList.add(stszEntry);

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
                                sampleFlags = trex.default_sample_flags;
                            }
                        }
                    }

                    // is difference sample
                    if (((sampleFlags & 0x00010000) >> 16) > 0) {
                        // iframe
                        SyncSampleBox.Entry e = new SyncSampleBox.Entry();
                        e.sample_number = sampleNumber;
                        stssList.add(e);
                    }
                    sampleNumber++;
                    first = false;
                }
                chunkNumber++;
                chunkOffset += (int) mdat.length();

                try {
                    IO.copy(in, out, mdat.length(), buf);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                return true;
            }
        }, buf);

        // remove other tracks
        MovieBox moov = Box.findFirst(boxes, Box.moov);
        ListIterator<Box> it = moov.boxes.listIterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (b.type != Box.trak) {
                continue;
            }

            TrackHeaderBox tkhd = b.findFirst(Box.tkhd);
            if (tkhd.trackId() != trackId) {
                it.remove();
            }
        }

        // recreate sample tables
        SampleTableBox stbl = Box.findFirst(boxes, Box.stbl);
        TimeToSampleBox stts = stbl.findFirst(Box.stts);
        if (stts != null) {
            stts.entry_count = sttsList.size();
            stts.entries = sttsList.toArray(new TimeToSampleBox.Entry[0]);
        }
        CompositionOffsetBox ctts = stbl.findFirst(Box.ctts);
        if (ctts != null) {
            ctts.entry_count = cttsList.size();
            ctts.entries = cttsList.toArray(new CompositionOffsetBox.Entry[0]);
        }
        SyncSampleBox stss = stbl.findFirst(Box.stss);
        if (stss != null) {
            stss.entry_count = stssList.size();
            stss.entries = stssList.toArray(new SyncSampleBox.Entry[0]);
        }
        SampleSizeBox stsz = stbl.findFirst(Box.stsz);
        if (stsz != null) {
            stsz.sample_size = 0;
            stsz.sample_count = stszList.size();
            stsz.entries = stszList.toArray(new SampleSizeBox.Entry[0]);
        }
        SampleToChunkBox stsc = stbl.findFirst(Box.stsc);
        if (stsc != null) {
            stsc.entry_count = stscList.size();
            stsc.entries = stscList.toArray(new SampleToChunkBox.Entry[0]);
        }
        ChunkOffsetBox stco = stbl.findFirst(Box.stco);
        if (stco != null) {
            stco.entry_count = stcoList.size();
            stco.entries = stcoList.toArray(new ChunkOffsetBox.Entry[0]);
        }

        // some fixes
        Box mvex = Box.findFirst(boxes, Box.mvex);
        mvex.parent.boxes.remove(mvex);
        Box sidx = Box.findFirst(boxes, Box.sidx);
        boxes.remove(sidx);
        TrackHeaderBox tkhd = Box.findFirst(boxes, Box.tkhd);
        tkhd.enabled(true);
        tkhd.inMovie(true);
        tkhd.inPreview(true);
        tkhd.inPoster(true);
        MediaHeaderBox mdhd = Box.findFirst(boxes, Box.mdhd);
        mdhd.language("eng");

        // add tags
        if (tags != null) {
            if (tags.compatibleBrands != null) {
                FileTypeBox ftyp = Box.findFirst(boxes, Box.ftyp);
                ftyp.compatible_brands = tags.compatibleBrands;
            }
            // remove old udta boxes
            for (UserDataBox b : moov.<UserDataBox>find(Box.udta)) {
                moov.boxes.remove(b);
            }
            UserDataBox udta = createUdta(tags);
            moov.boxes.add(udta);
        }

        MediaDataBox mdat = new MediaDataBox();
        mdat.length(0);
        boxes.add(mdat);
        long len = ContainerBox.length(boxes); // this update the boxes
        if (len > mdatOffset) {
            throw new IOException("Movie header bigger than mdat offset");
        }
        mdat.length(fOut.length() - len);

        fOut.seek(0);
        IsoMedia.write(out, boxes, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                return true;
            }
        }, buf);
    }

    private static void trackSimple(int id, Mp4Tags tags, RandomAccessFile fIn, RandomAccessFile fOut) throws IOException {
        int trackId = id;
        ByteBuffer buf = ByteBuffer.allocate(10 * 1024);
        InputChannel in = new InputChannel(fIn.getChannel());
        OutputChannel out = new OutputChannel(fOut.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(in, fIn.length(), null, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        }, buf);

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

        // add tags
        if (tags != null) {
            if (tags.compatibleBrands != null) {
                FileTypeBox ftyp = Box.findFirst(boxes, Box.ftyp);
                ftyp.compatible_brands = tags.compatibleBrands;
            }
            // remove old udta boxes
            for (UserDataBox b : moov.<UserDataBox>find(Box.udta)) {
                moov.boxes.remove(b);
            }
            UserDataBox udta = createUdta(tags);
            moov.boxes.add(udta);
        }

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

        IsoMedia.write(out, boxes, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                return true;
            }
        }, buf);

        for (int i = 0; i < stco.entry_count; i++) {
            int pos = (int) in.count();

            int skp = chunkOffsetOrg[i] - pos;
            IO.skip(in, skp, buf);

            IO.copy(in, out, chunkSize[i], buf);
        }
    }

    private static int calcMdatOffset(LinkedList<Box> head, long length, Mp4Tags tags) throws IOException {
        MovieFragmentBox moof = Box.findFirst(head, Box.moof);
        MediaDataBox mdat = Box.findFirst(head, Box.mdat);

        int n = (int) (length / mdat.size);
        int len = n * moof.size * 4;
        len += 100000; // header room
        len += tags != null && tags.jpg != null ? tags.jpg.length : 0;

        return len;
    }

    private static UserDataBox createUdta(Mp4Tags tags) {
        //"/moov/udta/meta/ilst/covr/data"
        UserDataBox udta = new UserDataBox();

        MetaBox meta = new MetaBox();
        udta.boxes.add(meta);

        HandlerBox hdlr = new HandlerBox();
        hdlr.handler_type = Bits.make4cc("mdir");
        meta.boxes.add(hdlr);

        AppleItemListBox ilst = new AppleItemListBox();
        meta.boxes.add(ilst);

        if (tags.title != null) {
            AppleNameBox cnam = new AppleNameBox();
            cnam.value(tags.title);
            ilst.boxes.add(cnam);
        }

        if (tags.author != null) {
            AppleArtistBox cART = new AppleArtistBox();
            cART.value(tags.author);
            ilst.boxes.add(cART);
        }

        if (tags.title != null || tags.author != null) {
            AppleArtist2Box aART = new AppleArtist2Box();
            aART.value(tags.title + " " + tags.author);
            ilst.boxes.add(aART);
        }

        if (tags.title != null || tags.author != null || tags.source != null) {
            AppleAlbumBox calb = new AppleAlbumBox();
            calb.value(tags.title + " " + tags.author + " via " + tags.source);
            ilst.boxes.add(calb);
        }

        AppleMediaTypeBox stik = new AppleMediaTypeBox();
        stik.value(1);
        ilst.boxes.add(stik);

        if (tags.jpg != null) {
            AppleCoverBox covr = new AppleCoverBox();
            covr.setJpg(tags.jpg);
            ilst.boxes.add(covr);
        }

        return udta;
    }

    private static void close(RandomAccessFile f) {
        try {
            if (f != null) {
                f.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }
}
