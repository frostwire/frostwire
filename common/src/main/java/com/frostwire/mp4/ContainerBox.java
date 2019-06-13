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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public class ContainerBox extends Box {
    private static final HashMap<Integer, int[]> mapping = buildMapping();

    ContainerBox(int type) {
        super(type);
        boxes = new LinkedList<>();
    }

    static long length(LinkedList<Box> boxes) {
        long s = 0;
        for (Box b : boxes) {
            b.update();
            if (b.size == 1) {
                s = s + b.largesize;
            } else if (b.size == 0) {
                throw new UnsupportedOperationException();
            } else {
                s = s + b.size;
            }
        }
        return s;
    }

    static void sort(LinkedList<Box> boxes, final int[] list) {
        Collections.sort(boxes, new Comparator<Box>() {
            @Override
            public int compare(Box o1, Box o2) {
                int x = Integer.MAX_VALUE;
                int y = Integer.MAX_VALUE;
                for (int i = 0; i < list.length; i++) {
                    if (list[i] == o1.type) {
                        x = i;
                    }
                    if (list[i] == o2.type) {
                        y = i;
                    }
                }
                return (x < y) ? -1 : ((x == y) ? 0 : 1);
            }
        });
    }

    private static HashMap<Integer, int[]> buildMapping() {
        HashMap<Integer, int[]> map = new HashMap<>();
        map.put(moov, new int[]{mvhd, trak, mvex, ipmc, udta, meta});
        map.put(trak, new int[]{tkhd, tref, edts, mdia, udta, meta});
        map.put(edts, new int[]{elst});
        map.put(mdia, new int[]{mdhd, hdlr, minf});
        map.put(minf, new int[]{vmhd, smhd, hmhd, nmhd, dinf, stbl});
        map.put(dinf, new int[]{dref});
        map.put(stbl, new int[]{stsd, stts, ctts, stsc, stsz, stz2, stco, co64, stss, stsh, padb, stdp, sdtp, sbgp, sgpd, subs});
        map.put(mvex, new int[]{mehd, trex});
        map.put(moof, new int[]{mfhd, traf});
        map.put(traf, new int[]{tfhd, trun, sdtp, sbgp, subs});
        map.put(mfra, new int[]{tfra, mfro});
        map.put(udta, new int[]{cprt, meta});
        map.put(meta, new int[]{hdlr, dinf, ipmc, iloc, ipro, iinf, xml_, bxml, pitm, ilst});
        map.put(ipro, new int[]{sinf});
        map.put(sinf, new int[]{frma, imif, schm, schi});
        map.put(ilst, new int[]{Cnam, CART, aART, Calb, Cgen, gnre, Cday, trkn, stik, covr});
        return map;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
    }

    @Override
    final void update() {
        int[] list = mapping.get(type);
        if (list != null) {
            sort(boxes, list);
        }
        long s = 0;
        s += length(boxes);
        length(s);
    }
}
