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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public class Box {
    public static final int uuid = Bits.make4cc("uuid");
    public static final int soun = Bits.make4cc("soun");
    public static final int vide = Bits.make4cc("vide");
    public static final int hint = Bits.make4cc("hint");
    public static final int data = Bits.make4cc("data");
    public static final int MP4_ = Bits.make4cc("MP4 ");
    public static final int M4A_ = Bits.make4cc("M4A ");
    public static final int dash = Bits.make4cc("dash");
    public static final int mp41 = Bits.make4cc("mp41");
    public static final int mp42 = Bits.make4cc("mp42");
    public static final int isom = Bits.make4cc("isom");
    public static final int iso6 = Bits.make4cc("iso6");
    public static final int avc1 = Bits.make4cc("avc1");
    public static final int zero = Bits.make4cc("\0\0\0\0");
    public static final int mdat = Bits.make4cc("mdat");
    public static final int ftyp = Bits.make4cc("ftyp");
    public static final int moov = Bits.make4cc("moov");
    public static final int mvhd = Bits.make4cc("mvhd");
    public static final int iods = Bits.make4cc("iods");
    public static final int trak = Bits.make4cc("trak");
    public static final int tkhd = Bits.make4cc("tkhd");
    public static final int tref = Bits.make4cc("tref");
    public static final int edts = Bits.make4cc("edts");
    public static final int elst = Bits.make4cc("elst");
    public static final int udta = Bits.make4cc("udta");
    public static final int mdia = Bits.make4cc("mdia");
    public static final int mdhd = Bits.make4cc("mdhd");
    public static final int hdlr = Bits.make4cc("hdlr");
    public static final int minf = Bits.make4cc("minf");
    public static final int vmhd = Bits.make4cc("vmhd");
    public static final int smhd = Bits.make4cc("smhd");
    public static final int hmhd = Bits.make4cc("hmhd");
    public static final int nmhd = Bits.make4cc("nmhd");
    public static final int dinf = Bits.make4cc("dinf");
    public static final int dref = Bits.make4cc("dref");
    public static final int url_ = Bits.make4cc("url ");
    public static final int urn_ = Bits.make4cc("urn ");
    public static final int stbl = Bits.make4cc("stbl");
    public static final int stsd = Bits.make4cc("stsd");
    public static final int stts = Bits.make4cc("stts");
    public static final int ctts = Bits.make4cc("ctts");
    public static final int stss = Bits.make4cc("stss");
    public static final int stsh = Bits.make4cc("stsh");
    public static final int sbgp = Bits.make4cc("sbgp");
    public static final int stsc = Bits.make4cc("stsc");
    public static final int stsz = Bits.make4cc("stsz");
    public static final int stz2 = Bits.make4cc("stz2");
    public static final int stco = Bits.make4cc("stco");
    public static final int co64 = Bits.make4cc("co64");
    public static final int esds = Bits.make4cc("esds");
    public static final int meta = Bits.make4cc("meta");
    public static final int ilst = Bits.make4cc("ilst");
    public static final int Cnam = Bits.make4cc("©nam");
    public static final int CART = Bits.make4cc("©ART");
    public static final int aART = Bits.make4cc("aART");
    public static final int Calb = Bits.make4cc("©alb");
    public static final int stik = Bits.make4cc("stik");
    public static final int covr = Bits.make4cc("covr");
    public static final int Ccmt = Bits.make4cc("©cmt");
    public static final int Cgen = Bits.make4cc("©gen");
    public static final int gnre = Bits.make4cc("gnre");
    public static final int Cday = Bits.make4cc("©day");
    public static final int trkn = Bits.make4cc("trkn");
    public static final int free = Bits.make4cc("free");
    public static final int skip = Bits.make4cc("skip");
    public static final int mvex = Bits.make4cc("mvex");
    public static final int trex = Bits.make4cc("trex");
    public static final int sidx = Bits.make4cc("sidx");
    public static final int moof = Bits.make4cc("moof");
    public static final int mfhd = Bits.make4cc("mfhd");
    public static final int traf = Bits.make4cc("traf");
    public static final int tfhd = Bits.make4cc("tfhd");
    public static final int tfdt = Bits.make4cc("tfdt");
    public static final int trun = Bits.make4cc("trun");
    public static final int ipmc = Bits.make4cc("ipmc");
    public static final int padb = Bits.make4cc("padb");
    public static final int stdp = Bits.make4cc("stdp");
    public static final int sdtp = Bits.make4cc("sdtp");
    public static final int sgpd = Bits.make4cc("sgpd");
    public static final int subs = Bits.make4cc("subs");
    public static final int mehd = Bits.make4cc("mehd");
    public static final int mfra = Bits.make4cc("mfra");
    public static final int tfra = Bits.make4cc("tfra");
    public static final int mfro = Bits.make4cc("mfro");
    public static final int cprt = Bits.make4cc("cprt");
    public static final int iloc = Bits.make4cc("iloc");
    public static final int ipro = Bits.make4cc("ipro");
    public static final int iinf = Bits.make4cc("iinf");
    public static final int xml_ = Bits.make4cc("xml ");
    public static final int bxml = Bits.make4cc("bxml");
    public static final int pitm = Bits.make4cc("pitm");
    public static final int sinf = Bits.make4cc("sinf");
    public static final int frma = Bits.make4cc("frma");
    public static final int imif = Bits.make4cc("imif");
    public static final int schm = Bits.make4cc("schm");
    public static final int schi = Bits.make4cc("schi");
    private static final HashMap<Integer, BoxLambda> mapping = buildMapping();
    protected final int type;
    protected int size;
    protected Long largesize;
    protected byte[] usertype;
    protected Box parent;
    protected LinkedList<Box> boxes;

    Box(int type) {
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Box> LinkedList<T> find(LinkedList<Box> boxes, int type) {
        LinkedList<T> l = new LinkedList<>();
        for (Box b : boxes) {
            if (b.type == type) {
                l.add((T) b);
            }
        }
        if (l.isEmpty()) {
            for (Box b : boxes) {
                if (b.boxes != null) {
                    LinkedList<T> t = find(b.boxes, type);
                    if (!t.isEmpty()) {
                        l.addAll(t);
                    }
                }
            }
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Box> T findFirst(LinkedList<Box> boxes, int type) {
        for (Box b : boxes) {
            if (b.type == type) {
                return (T) b;
            }
        }
        for (Box b : boxes) {
            if (b.boxes != null) {
                T t = findFirst(b.boxes, type);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    static Box empty(int type) {
        BoxLambda p = mapping.get(type);
        if (p != null) {
            return p.empty();
        } else {
            return new UnknownBox(type);
        }
    }

    private static HashMap<Integer, BoxLambda> buildMapping() {
        HashMap<Integer, BoxLambda> map = new HashMap<>();
        map.put(mdat, MediaDataBox::new);
        map.put(ftyp, FileTypeBox::new);
        map.put(moov, MovieBox::new);
        map.put(mvhd, MovieHeaderBox::new);
        map.put(iods, ObjectDescriptorBox::new);
        map.put(trak, TrackBox::new);
        map.put(tkhd, TrackHeaderBox::new);
        map.put(edts, EditBox::new);
        map.put(elst, EditListBox::new);
        map.put(udta, UserDataBox::new);
        map.put(mdia, MediaBox::new);
        map.put(mdhd, MediaHeaderBox::new);
        map.put(hdlr, HandlerBox::new);
        map.put(minf, MediaInformationBox::new);
        map.put(vmhd, VideoMediaHeaderBox::new);
        map.put(smhd, SoundMediaHeaderBox::new);
        map.put(hmhd, HintMediaHeaderBox::new);
        map.put(nmhd, NullMediaHeaderBox::new);
        map.put(dinf, DataInformationBox::new);
        map.put(dref, DataReferenceBox::new);
        map.put(url_, DataEntryUrlBox::new);
        map.put(urn_, DataEntryUrnBox::new);
        map.put(stbl, SampleTableBox::new);
        map.put(stsd, SampleDescriptionBox::new);
        map.put(stts, TimeToSampleBox::new);
        map.put(ctts, CompositionOffsetBox::new);
        map.put(stss, SyncSampleBox::new);
        map.put(stsh, ShadowSyncSampleBox::new);
        map.put(sbgp, SampleToGroupBox::new);
        map.put(stsc, SampleToChunkBox::new);
        map.put(stsz, SampleSizeBox::new);
        map.put(stz2, CompactSampleSizeBox::new);
        map.put(stco, ChunkOffsetBox::new);
        map.put(co64, ChunkLargeOffsetBox::new);
        map.put(esds, ESDBox::new);
        map.put(meta, MetaBox::new);
        map.put(ilst, AppleItemListBox::new);
        map.put(Cnam, AppleNameBox::new);
        map.put(CART, AppleArtistBox::new);
        map.put(aART, AppleAlbumArtistBox::new);
        map.put(Calb, AppleAlbumBox::new);
        map.put(stik, AppleMediaTypeBox::new);
        map.put(covr, AppleCoverBox::new);
        map.put(Ccmt, AppleCommentBox::new);
        map.put(Cgen, AppleGenreBox::new);
        map.put(gnre, AppleGenreIDBox::new);
        map.put(Cday, AppleYearBox::new);
        map.put(free, () -> new FreeSpaceBox(free));
        map.put(skip, () -> new FreeSpaceBox(skip));
        map.put(mvex, MovieExtendsBox::new);
        map.put(trex, TrackExtendsBox::new);
        map.put(sidx, SegmentIndexBox::new);
        map.put(moof, MovieFragmentBox::new);
        map.put(mfhd, MovieFragmentHeaderBox::new);
        map.put(traf, TrackFragmentBox::new);
        map.put(tfhd, TrackFragmentHeaderBox::new);
        map.put(tfdt, TrackFragmentBaseMediaDecodeTimeBox::new);
        map.put(trun, TrackRunBox::new);
        return map;
    }

    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        throw new UnsupportedOperationException(Bits.make4cc(type));
    }

    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        throw new UnsupportedOperationException(Bits.make4cc(type));
    }

    void update() {
        throw new UnsupportedOperationException(Bits.make4cc(type));
    }

    final long length() {
        long n = size - 8;
        if (size == 1) {
            n = largesize - 16;
        } else if (size == 0) {
            return -1;
        }
        if (type == uuid) {
            n = n - 16;
        }
        if (n < 0) {
            throw new UnsupportedOperationException("negative long value: " + n);
        }
        return n;
    }

    final void length(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative long value: " + value);
        }
        long s = value;
        if (type == uuid) {
            s = s + 16;
        }
        if (s <= Integer.MAX_VALUE - 8) {
            size = (int) (s + 8);
            largesize = null;
        } else {
            size = 1;
            largesize = s + 16;
        }
    }

    public final <T extends Box> LinkedList<T> find(int type) {
        return boxes != null ? Box.find(boxes, type) : new LinkedList<T>();
    }

    public final <T extends Box> T findFirst(int type) {
        return boxes != null ? Box.findFirst(boxes, type) : null;
    }

    @Override
    public String toString() {
        return Bits.make4cc(type);
    }

    private interface BoxLambda {
        Box empty();
    }
}
