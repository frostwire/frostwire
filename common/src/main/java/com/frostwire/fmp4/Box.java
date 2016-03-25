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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class Box {

    public static final int uuid = Bits.make4cc("uuid");
    public static final int soun = Bits.make4cc("soun");
    public static final int vide = Bits.make4cc("vide");
    public static final int hint = Bits.make4cc("hint");

    public static final int mdat = Bits.make4cc("mdat");
    public static final int ftyp = Bits.make4cc("ftyp");
    public static final int moov = Bits.make4cc("moov");
    public static final int mvhd = Bits.make4cc("mvhd");
    public static final int iods = Bits.make4cc("iods");
    public static final int trak = Bits.make4cc("trak");
    public static final int tkhd = Bits.make4cc("tkhd");
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
    public static final int free = Bits.make4cc("free");
    public static final int skip = Bits.make4cc("skip");

    private static final Map<Integer, BoxLambda> mapping = buildMapping();

    protected int size;
    protected final int type;
    protected Long largesize;
    protected byte[] usertype;

    protected Box parent;
    protected LinkedList<Box> boxes;

    Box(int type) {
        this.type = type;
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

    @Override
    public String toString() {
        return Bits.make4cc(type);
    }

    static Box empty(int type) throws IOException {
        BoxLambda p = mapping.get(type);
        if (p != null) {
            return p.empty();
        } else {
            return new UnknownBox(type);
        }
    }

    private static Map<Integer, BoxLambda> buildMapping() {
        Map<Integer, BoxLambda> map = new HashMap<>();

        map.put(mdat, new BoxLambda() {
            @Override
            public Box empty() {
                return new MediaDataBox();
            }
        });
        map.put(ftyp, new BoxLambda() {
            @Override
            public Box empty() {
                return new FileTypeBox();
            }
        });
        map.put(moov, new BoxLambda() {
            @Override
            public Box empty() {
                return new MovieBox();
            }
        });
        map.put(mvhd, new BoxLambda() {
            @Override
            public Box empty() {
                return new MovieHeaderBox();
            }
        });
        map.put(iods, new BoxLambda() {
            @Override
            public Box empty() {
                return new ObjectDescriptorBox();
            }
        });
        map.put(trak, new BoxLambda() {
            @Override
            public Box empty() {
                return new TrackBox();
            }
        });
        map.put(tkhd, new BoxLambda() {
            @Override
            public Box empty() {
                return new TrackHeaderBox();
            }
        });
        map.put(edts, new BoxLambda() {
            @Override
            public Box empty() {
                return new EditBox();
            }
        });
        map.put(elst, new BoxLambda() {
            @Override
            public Box empty() {
                return new EditListBox();
            }
        });
        map.put(udta, new BoxLambda() {
            @Override
            public Box empty() {
                return new UserDataBox();
            }
        });
        map.put(mdia, new BoxLambda() {
            @Override
            public Box empty() {
                return new MediaBox();
            }
        });
        map.put(mdhd, new BoxLambda() {
            @Override
            public Box empty() {
                return new MediaHeaderBox();
            }
        });
        map.put(hdlr, new BoxLambda() {
            @Override
            public Box empty() {
                return new HandlerBox();
            }
        });
        map.put(minf, new BoxLambda() {
            @Override
            public Box empty() {
                return new MediaInformationBox();
            }
        });
        map.put(vmhd, new BoxLambda() {
            @Override
            public Box empty() {
                return new VideoMediaHeaderBox();
            }
        });
        map.put(smhd, new BoxLambda() {
            @Override
            public Box empty() {
                return new SoundMediaHeaderBox();
            }
        });
        map.put(hmhd, new BoxLambda() {
            @Override
            public Box empty() {
                return new HintMediaHeaderBox();
            }
        });
        map.put(nmhd, new BoxLambda() {
            @Override
            public Box empty() {
                return new NullMediaHeaderBox();
            }
        });
        map.put(dinf, new BoxLambda() {
            @Override
            public Box empty() {
                return new DataInformationBox();
            }
        });
        map.put(dref, new BoxLambda() {
            @Override
            public Box empty() {
                return new DataReferenceBox();
            }
        });
        map.put(url_, new BoxLambda() {
            @Override
            public Box empty() {
                return new DataEntryUrlBox();
            }
        });
        map.put(urn_, new BoxLambda() {
            @Override
            public Box empty() {
                return new DataEntryUrnBox();
            }
        });
        map.put(stbl, new BoxLambda() {
            @Override
            public Box empty() {
                return new SampleTableBox();
            }
        });
        map.put(stsd, new BoxLambda() {
            @Override
            public Box empty() {
                return new SampleDescriptionBox();
            }
        });
        map.put(stts, new BoxLambda() {
            @Override
            public Box empty() {
                return new TimeToSampleBox();
            }
        });
        map.put(ctts, new BoxLambda() {
            @Override
            public Box empty() {
                return new CompositionOffsetBox();
            }
        });
        map.put(stss, new BoxLambda() {
            @Override
            public Box empty() {
                return new SyncSampleBox();
            }
        });
        map.put(stsh, new BoxLambda() {
            @Override
            public Box empty() {
                return new ShadowSyncSampleBox();
            }
        });
        map.put(sbgp, new BoxLambda() {
            @Override
            public Box empty() {
                return new SampleToGroupBox();
            }
        });
        map.put(stsc, new BoxLambda() {
            @Override
            public Box empty() {
                return new SampleToChunkBox();
            }
        });
        map.put(stsz, new BoxLambda() {
            @Override
            public Box empty() {
                return new SampleSizeBox();
            }
        });
        map.put(stz2, new BoxLambda() {
            @Override
            public Box empty() {
                return new CompactSampleSizeBox();
            }
        });
        map.put(stco, new BoxLambda() {
            @Override
            public Box empty() {
                return new ChunkOffsetBox();
            }
        });
        map.put(co64, new BoxLambda() {
            @Override
            public Box empty() {
                return new ChunkLargeOffsetBox();
            }
        });
        map.put(esds, new BoxLambda() {
            @Override
            public Box empty() {
                return new ESDBox();
            }
        });
        map.put(meta, new BoxLambda() {
            @Override
            public Box empty() {
                return new MetaBox();
            }
        });
        map.put(ilst, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleItemListBox();
            }
        });
        map.put(Cnam, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleNameBox();
            }
        });
        map.put(CART, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleArtistBox();
            }
        });
        map.put(aART, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleArtist2Box();
            }
        });
        map.put(Calb, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleAlbumBox();
            }
        });
        map.put(stik, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleMediaTypeBox();
            }
        });
        map.put(covr, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleCoverBox();
            }
        });
        map.put(Ccmt, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleCommentBox();
            }
        });
        map.put(Cgen, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleGenreBox();
            }
        });
        map.put(gnre, new BoxLambda() {
            @Override
            public Box empty() {
                return new AppleGenreIDBox();
            }
        });
        map.put(free, new BoxLambda() {
            @Override
            public Box empty() {
                return new FreeSpaceBox(free);
            }
        });
        map.put(skip, new BoxLambda() {
            @Override
            public Box empty() {
                return new FreeSpaceBox(skip);
            }
        });

        return map;
    }

    private interface BoxLambda {
        Box empty();
    }
}
