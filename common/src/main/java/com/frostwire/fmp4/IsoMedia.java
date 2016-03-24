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
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class IsoMedia {

    private IsoMedia() {
    }

    static void read(InputChannel ch) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1 * 1024);
        read(ch, -1, null, null, buf);
    }

    static void read(InputChannel ch, long len, ReadListener l) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1 * 1024);
        read(ch, len, null, l, buf);
    }

    public static void read(InputChannel ch, long len, Box p, ReadListener l, ByteBuffer buf) throws IOException {
        long n = ch.count();
        do {
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

            if (p != null) {
                p.boxes().add(b);
            }

            if (l != null) {
                l.onBox(b);
            }

            long length = b.length();
            if (r < length) {
                if (type != Box.mdat) {
                    read(ch, length - r, b, l, buf);
                } else {
                    if (length > 0) {
                        IO.skip(ch, length - r, buf);
                    } else {
                        IO.skip(ch, buf);
                    }
                }
            }
        } while (len == -1 || ch.count() - n < len);
    }

    public static void write(OutputChannel ch, LinkedList<Box> boxes) {

    }

    public interface ReadListener {

        /**
         * Give the opportunity to react on box reading and together
         * with the {@link InputChannel#count()} you can keep a good
         * progress of the reading in the ISO media (stream or file).
         *
         * @param b
         */
        void onBox(Box b);
    }
}
