/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.mp4;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import static com.frostwire.TestUtil.getTestResource;

/**
 * @author gubatron
 * @author aldenml
 */
public class FragmentedDemuxTest {

    @Test
    public void testFragmented1() throws IOException {
        File fIn = getTestResource("/com/frostwire/mp4/test_audio.m4a");
        File fOut = new File(System.getProperty("java.io.tmpdir"), "test_out.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "r");
        RandomAccessFile out = new RandomAccessFile(fOut, "rw");
        final InputChannel chIn = new InputChannel(in.getChannel());
        final OutputChannel chOut = new OutputChannel(out.getChannel());

        final LinkedList<Box> boxes = new LinkedList<>();

        final LinkedList<TimeToSampleBox.Entry> sttsList = new LinkedList<>();
        final LinkedList<CompositionOffsetBox.Entry> cttsList = new LinkedList<>();
        final LinkedList<SyncSampleBox.Entry> stssList = new LinkedList<>();
        final LinkedList<SampleSizeBox.Entry> stszList = new LinkedList<>();
        final LinkedList<SampleToChunkBox.Entry> stscList = new LinkedList<>();
        final LinkedList<ChunkOffsetBox.Entry> stcoList = new LinkedList<>();

        final ByteBuffer buf = ByteBuffer.allocate(10 * 1024);

        // move 200Kb in out to make space for header
        final int mdatOffset = 3 * 1024 * 1024;
        IO.skip(chOut, mdatOffset, buf);

        IsoMedia.read(chIn, fIn.length(), null, buf, new IsoMedia.OnBoxListener() {

            TrackExtendsBox trex;
            TrackRunBox lastTrun;
            int chunkNumber = 1;
            int chunkOffset = mdatOffset;
            int sampleNumber = 1;

            @Override
            public boolean onBox(Box b) {
                if (b.parent == null &&
                        b.type != Box.mdat &&
                        b.type != Box.moof) {
                    boxes.add(b);
                }

                if (b.type == Box.trex) {
                    trex = (TrackExtendsBox) b;
                }

                if (b.type == Box.trun) {
                    lastTrun = (TrackRunBox) b;
                }

                if (b.type != Box.mdat) {
                    return true;
                }

                MediaDataBox mdat = (MediaDataBox) b;
                TrackRunBox trun = lastTrun;
                TrackFragmentHeaderBox tfhd = trun.parent.findFirst(Box.tfhd);

                SampleToChunkBox.Entry e2 = new SampleToChunkBox.Entry();
                e2.first_chunk = chunkNumber;
                e2.samples_per_chunk = trun.sample_count;
                e2.sample_description_index = 1;
                stscList.add(e2);

                ChunkOffsetBox.Entry e3 = new ChunkOffsetBox.Entry();
                e3.chunk_offset = chunkOffset;
                stcoList.add(e3);

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

                    SampleSizeBox.Entry e1 = new SampleSizeBox.Entry();
                    e1.entry_size = entry.sample_size;
                    stszList.add(e1);

                    final int sampleFlags;
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
                    IO.copy(chIn, chOut, mdat.length(), buf);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }

                return true;
            }
        });

        out.close();

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

        Box mvex = Box.findFirst(boxes, Box.mvex);
        mvex.parent.boxes.remove(mvex);

        long newLen = ContainerBox.length(boxes);
        MediaDataBox mdat = new MediaDataBox();
        mdat.length(fOut.length() - newLen);
        boxes.add(mdat);
        newLen = ContainerBox.length(boxes);

        out = new RandomAccessFile(fOut, "rw");
        final OutputChannel chOut1 = new OutputChannel(out.getChannel());
        IsoMedia.write(chOut1, boxes, buf, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                return true;
            }
        });
        out.close();
    }

    @Test
    public void testFragmented3() throws IOException {
        File fIn = getTestResource("/com/frostwire/mp4/test_video.mp4");
        File fOut = new File(System.getProperty("java.io.tmpdir"), "test_out_ref.mp4");

        Mp4Info tags = new Mp4Info();
        tags.compatibleBrands = new int[]{Bits.make4cc("M4A "), Bits.make4cc("mp42"), Bits.make4cc("isom"), Bits.make4cc("\0\0\0\0")};
        tags.title = "ti";
        tags.author = "au";
        tags.album = "sr";
        tags.jpg = null;

        Mp4Demuxer.audio(fIn, fOut, tags, null);
    }

    @Test
    public void testFragmented4() throws IOException {
        File fIn1 = getTestResource("/com/frostwire/mp4/test_video.mp4");
        File fIn2 = getTestResource("/com/frostwire/mp4/test_audio.m4a");
        File fOut = new File(System.getProperty("java.io.tmpdir"), "test_out.mp4");

        Mp4Info tags = new Mp4Info();
        tags.compatibleBrands = new int[]{Bits.make4cc("M4A "), Bits.make4cc("mp42"), Bits.make4cc("isom"), Bits.make4cc("\0\0\0\0")};
        tags.title = "ti";
        tags.author = "au";
        tags.album = "sr";
        tags.jpg = null;

        Mp4Demuxer.muxFragments(fIn1, fIn2, fOut, tags, null);
    }
}
