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

    static void read(InputChannel ch, long len, OnBoxListener l) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1 * 1024);
        read(ch, len, null, l, buf);
    }

    public static boolean read(InputChannel ch, long len, Box p, OnBoxListener l, ByteBuffer buf) throws IOException {
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

            b.parent = p;

            long r = ch.count();
            b.read(ch, buf);
            r = ch.count() - r;

            if (p != null) {
                p.boxes.add(b);
            }

            if (l != null) {
                if (!l.onBox(b)) {
                    return false;
                }
            }

            long length = b.length();
            if (r < length) {
                if (type != Box.mdat) {
                    if (!read(ch, length - r, b, l, buf)) {
                        return false;
                    }
                } else {
                    if (length > 0) {
                        IO.skip(ch, length - r, buf);
                    } else {
                        IO.skip(ch, buf);
                    }
                }
            }
        } while (len == -1 || ch.count() - n < len);

        return true;
    }

    public static void write(OutputChannel ch, LinkedList<Box> boxes, OnBoxListener l) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4 * 1024);
        write(ch, boxes, l, buf);
    }

    public static boolean write(OutputChannel ch, LinkedList<Box> boxes, OnBoxListener l, ByteBuffer buf) throws IOException {
        for (Box b : boxes) {
            buf.putInt(b.size);
            buf.putInt(b.type);
            IO.write(ch, 8, buf);

            if (b.largesize != null) {
                buf.putLong(b.largesize);
                IO.write(ch, 8, buf);
            }

            if (b.usertype != null) {
                buf.put(b.usertype);
                IO.write(ch, 16, buf);
            }

            long length = b.length();
            long w = ch.count();

            b.write(ch, buf);

            if (l != null) {
                if (!l.onBox(b)) {
                    return false;
                }
            }

            if (b.boxes != null) {
                if (!write(ch, b.boxes, l, buf)) {
                    return false;
                }
            }

            w = ch.count() - w;
            if (w != length && b.type != Box.mdat) {
                throw new IOException("Inconsistent box data: " + Bits.make4cc(b.type));
            }
        }

        return true;
    }

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

    public interface OnBoxListener {

        /**
         * Give the opportunity to react on box read/write and together
         * with the channel {@code count()} you can keep a good
         * progress of the progress in the ISO media (stream or file).
         *
         * @param b
         * @return true if you want to stop
         */
        boolean onBox(Box b);
    }
}
