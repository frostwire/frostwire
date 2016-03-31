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

import org.junit.Test;

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
public class SimpleReadTest {

    private static final ByteBuffer BUF = ByteBuffer.allocate(100 * 1024);

    @Test
    public void testRead() throws IOException {
        File f = new File("/Users/aldenml/Downloads/year.mp4");
        RandomAccessFile in = new RandomAccessFile(f, "r");
        InputChannel ch = new InputChannel(in.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(ch, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                if (b instanceof UnknownBox) {
                    System.out.print("Unknow box: ");
                }
                System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return true;
            }
        });

        System.out.println("Num boxes: " + boxes.size());
    }

    @Test
    public void testCopy() throws IOException {
        File fIn = new File("/Users/aldenml/Downloads/test_raw.m4a");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        InputChannel chIn = new InputChannel(in.getChannel());
        OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(chIn, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });

        IsoMedia.write(chOut, boxes, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println("Write: " + Bits.make4cc(b.type));
                return true;
            }
        });

        ByteBuffer buf = ByteBuffer.allocate(4 * 1024);
        IO.copy(chIn, chOut, fIn.length() - chIn.count(), buf);
    }

    @Test
    public void testCopyUpdate() throws IOException {
        File fIn = new File("/Users/aldenml/Downloads/test_raw.m4a");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        InputChannel chIn = new InputChannel(in.getChannel());
        OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(chIn, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });

        for (Box b : boxes) {
            if (b.type != Box.mdat) {
                b.update();
            }
        }

        IsoMedia.write(chOut, boxes, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println("Write: " + Bits.make4cc(b.type));
                return true;
            }
        });

        ByteBuffer buf = ByteBuffer.allocate(4 * 1024);
        IO.copy(chIn, chOut, fIn.length() - chIn.count(), buf);
    }

    @Test
    public void testExtractAudioSimple() throws IOException {
        File fIn = new File("/Users/aldenml/Downloads/test.mp4");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        InputChannel chIn = new InputChannel(in.getChannel());
        OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(chIn, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                //System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });

        // find
        VideoMediaHeaderBox vmhd = Box.findFirst(boxes, Box.vmhd);
        TrackBox trak = (TrackBox) vmhd.parent.parent.parent;
        MovieBox moov = (MovieBox) trak.parent;

        ListIterator<Box> it = moov.boxes.listIterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (b.equals(trak)) {
                it.set(FreeSpaceBox.free(b.length()));
            }
        }

        IsoMedia.write(chOut, boxes, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println("Write: " + Bits.make4cc(b.type));
                return true;
            }
        });

        ByteBuffer buf = ByteBuffer.allocate(4 * 1024);
        IO.copy(chIn, chOut, fIn.length() - chIn.count(), buf);
    }

    @Test
    public void testExtractAudioSamples() throws IOException {
        File fIn = new File("/Users/aldenml/Downloads/test.mp4");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        InputChannel chIn = new InputChannel(in.getChannel());
        OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(chIn, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                //System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });

        // find
        VideoMediaHeaderBox vmhd = Box.findFirst(boxes, Box.vmhd);
        SoundMediaHeaderBox smhd = Box.findFirst(boxes, Box.smhd);

        TrackBox trak = (TrackBox) vmhd.parent.parent.parent;
        MovieBox moov = (MovieBox) trak.parent;

        ListIterator<Box> it = moov.boxes.listIterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (b.equals(trak)) {
                it.set(FreeSpaceBox.free(b.length()));
            }
        }

        trak = (TrackBox) smhd.parent.parent.parent;
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

        IsoMedia.write(chOut, boxes, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println("Write: " + Bits.make4cc(b.type));
                return true;
            }
        });

        ByteBuffer buf = ByteBuffer.allocate(4 * 1024);
        for (int i = 0; i < stco.entry_count; i++) {
            int pos = (int) chIn.count();

            int skp = stco.entries[i].chunk_offset - pos;
            IO.skip(chIn, skp, buf);
            IO.skip(chOut, skp, buf);

            IO.copy(chIn, chOut, chunkSize[i], buf);
        }
    }

    @Test
    public void testExtractAudioCompact() throws IOException {
        File fIn = new File("/Users/aldenml/Downloads/test.mp4");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        InputChannel chIn = new InputChannel(in.getChannel());
        OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(chIn, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                //System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });

        // find
        MediaDataBox mdat = Box.findFirst(boxes, Box.mdat);
        VideoMediaHeaderBox vmhd = Box.findFirst(boxes, Box.vmhd);
        SoundMediaHeaderBox smhd = Box.findFirst(boxes, Box.smhd);

        TrackBox trak = (TrackBox) vmhd.parent.parent.parent;
        MovieBox moov = (MovieBox) trak.parent;

        ListIterator<Box> it = moov.boxes.listIterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (b.equals(trak)) {
                it.set(FreeSpaceBox.free(b.length()));
            }
        }

        trak = (TrackBox) smhd.parent.parent.parent;
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

        IsoMedia.write(chOut, boxes, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println("Write: " + Bits.make4cc(b.type));
                return true;
            }
        });

        ByteBuffer buf = ByteBuffer.allocate(4 * 1024);
        for (int i = 0; i < stco.entry_count; i++) {
            int pos = (int) chIn.count();

            int skp = chunkOffsetOrg[i] - pos;
            IO.skip(chIn, skp, buf);

            IO.copy(chIn, chOut, chunkSize[i], buf);
        }
    }

    @Test
    public void testReadFragmented() throws IOException {
        File f = new File("/Users/aldenml/Downloads/test_raw.m4a");
        RandomAccessFile in = new RandomAccessFile(f, "r");
        InputChannel ch = new InputChannel(in.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(ch, BUF, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                if (b instanceof UnknownBox) {
                    System.out.print("Unknow box: ");
                }
                System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return true;
            }
        });

        LinkedList<TrackFragmentBox> trafs = Box.find(boxes, Box.traf);
        for (TrackFragmentBox traf : trafs) {
            TrackFragmentHeaderBox tfhd = traf.findFirst(Box.tfhd);
            TrackRunBox trun = traf.findFirst(Box.trun);
            for (TrackRunBox.Entry e : trun.entries) {
                if (trun.sampleDurationPresent()) {
                    System.out.println("trun.sampleDurationPresent() -> true");
                } else {
                    System.out.println("trun.sampleDurationPresent() -> false");
                }
            }
        }

        System.out.println("Num boxes: " + boxes.size());
    }

    @Test
    public void testCount() throws IOException {
        File f = new File("/Users/aldenml/Downloads/audio_frag.mp4");
        RandomAccessFile in = new RandomAccessFile(f, "r");
        int n = IsoFile.count(in, Box.moof, BUF);

        System.out.println("Num boxes: " + n);
    }
}
