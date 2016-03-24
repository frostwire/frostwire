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

import com.frostwire.mp4.Track;
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

    @Test
    public void testRead() throws IOException {
        File f = new File("/Users/aldenml/Downloads/test.mp4");
        RandomAccessFile in = new RandomAccessFile(f, "r");
        InputChannel ch = new InputChannel(in.getChannel());

        IsoMedia.read(ch, f.length(), new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println(Bits.make4cc(b.type));
                return true;
            }
        });
    }

    @Test
    public void testCopy() throws IOException {
        File fIn = new File("/Users/aldenml/Downloads/test.mp4");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        InputChannel chIn = new InputChannel(in.getChannel());
        OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(chIn, fIn.length(), new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                System.out.println(Bits.make4cc(b.type));
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });

        IsoMedia.write(chOut, boxes, new IsoMedia.OnBoxListener() {
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
        File fIn = new File("/Users/aldenml/Downloads/test.mp4");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        InputChannel chIn = new InputChannel(in.getChannel());
        OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        IsoMedia.read(chIn, fIn.length(), new IsoMedia.OnBoxListener() {
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

        IsoMedia.write(chOut, boxes, new IsoMedia.OnBoxListener() {
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

        IsoMedia.read(chIn, fIn.length(), new IsoMedia.OnBoxListener() {
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
        VideoMediaHeaderBox vmhd = IsoMedia.<VideoMediaHeaderBox>find(boxes, Box.vmhd).getFirst();
        TrackBox trak = (TrackBox) vmhd.parent.parent.parent;
        MovieBox moov = (MovieBox) trak.parent;

        ListIterator<Box> it = moov.boxes.listIterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (b.equals(trak)) {
                it.set(FreeSpaceBox.free(b.length()));
            }
        }

        IsoMedia.write(chOut, boxes, new IsoMedia.OnBoxListener() {
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

        IsoMedia.read(chIn, fIn.length(), new IsoMedia.OnBoxListener() {
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
        VideoMediaHeaderBox vmhd = IsoMedia.<VideoMediaHeaderBox>find(boxes, Box.vmhd).getFirst();
        SoundMediaHeaderBox smhd = IsoMedia.<SoundMediaHeaderBox>find(boxes, Box.smhd).getFirst();

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
        SampleToChunkBox stsc = IsoMedia.<SampleToChunkBox>find(trak.boxes, Box.stsc).getFirst();
        SampleSizeBox stsz = IsoMedia.<SampleSizeBox>find(trak.boxes, Box.stsz).getFirst();
        ChunkOffsetBox stco = IsoMedia.<ChunkOffsetBox>find(trak.boxes, Box.stco).getFirst();

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

        IsoMedia.write(chOut, boxes, new IsoMedia.OnBoxListener() {
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

        IsoMedia.read(chIn, fIn.length(), new IsoMedia.OnBoxListener() {
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
        MediaDataBox mdat = IsoMedia.<MediaDataBox>find(boxes, Box.mdat).getFirst();
        VideoMediaHeaderBox vmhd = IsoMedia.<VideoMediaHeaderBox>find(boxes, Box.vmhd).getFirst();
        SoundMediaHeaderBox smhd = IsoMedia.<SoundMediaHeaderBox>find(boxes, Box.smhd).getFirst();

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
        SampleToChunkBox stsc = IsoMedia.<SampleToChunkBox>find(trak.boxes, Box.stsc).getFirst();
        SampleSizeBox stsz = IsoMedia.<SampleSizeBox>find(trak.boxes, Box.stsz).getFirst();
        ChunkOffsetBox stco = IsoMedia.<ChunkOffsetBox>find(trak.boxes, Box.stco).getFirst();

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

        IsoMedia.write(chOut, boxes, new IsoMedia.OnBoxListener() {
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
}
