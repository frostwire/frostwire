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

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class IsoMedia {
    private IsoMedia() {
    }

    static boolean read(InputChannel ch, long len, Box p, ByteBuffer buf, OnBoxListener l) throws IOException {
        long n = ch.count();
        do {
            long start = ch.count();
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
            checkHeader(ch, b, ch.count() - start, len == -1 ? -1 : len - (start - n));
            long r = ch.count();
            try {
                b.read(ch, buf);
            } catch (BufferUnderflowException e) {
                // The box declares fewer bytes than its own fields take.
                throw new IOException("Box " + Bits.make4cc(type) + " is shorter than its fields", e);
            }
            r = ch.count() - r;
            long declared = b.length();
            if (declared >= 0 && r > declared) {
                throw new IOException("Box " + Bits.make4cc(type) + " read past its end");
            }
            if (p != null) {
                p.boxes.add(b);
            }
            if (l != null) {
                long pos = ch.count();
                if (!l.onBox(b)) {
                    return false;
                }
                if (pos != ch.count()) {
                    // there was a read inside the listener
                    // this operation is only allowed if the
                    // client read the entire box
                    r = ch.count() - pos;
                    if (r != b.length()) {
                        throw new UnsupportedOperationException("Invalid read inside listener");
                    }
                }
            }
            long length = b.length();
            if (r < length) {
                if (type != Box.mdat && b.boxes != null) {
                    if (!read(ch, length - r, b, buf, l)) {
                        return false;
                    }
                } else if (type != Box.mdat) {
                    // A leaf box with more bytes than its fields: skip them, they aren't children.
                    IO.skip(ch, length - r, buf);
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

    /**
     * Rejects a box whose header doesn't add up before anything is read for it: a size smaller
     * than its own header, or a box larger than what is left of its parent or of the file. Sizes
     * come from the file, and every later read and allocation trusts them.
     *
     * @param headerBytes the bytes the header took
     * @param room        the bytes left in the parent from the start of this box, or -1 if unknown
     */
    static void checkHeader(InputChannel ch, Box b, long headerBytes, long room) throws IOException {
        // What the header itself takes: size and type, a 64-bit size, a uuid's user type.
        long minSize = 8 + (b.size == 1 ? 8 : 0) + (b.type == Box.uuid ? 16 : 0);
        boolean sizeTooSmall = (b.size != 0 && b.size != 1 && b.size < minSize)
                || (b.size == 1 && b.largesize < minSize);
        if (sizeTooSmall) {
            throw new IOException("Invalid size of box " + Bits.make4cc(b.type));
        }
        long length = b.length();
        if (length < 0) {
            return; // the box runs to the end of the file
        }
        if (room >= 0 && headerBytes + length > room) {
            throw new IOException("Box " + Bits.make4cc(b.type) + " is larger than its parent");
        }
        // Media data is skipped, not read, and may be cut short in a file still downloading.
        if (b.type != Box.mdat && length > ch.remaining()) {
            throw new IOException("Box " + Bits.make4cc(b.type) + " is larger than the file");
        }
    }

    public static void read(InputChannel ch, ByteBuffer buf, OnBoxListener l) throws IOException {
        try {
            read(ch, -1, null, buf, l);
        } catch (EOFException e) {
            // ignore, it's the end
        }
    }

    public static boolean write(OutputChannel ch, LinkedList<Box> boxes, ByteBuffer buf, OnBoxListener l) throws IOException {
        buf.clear();
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
                if (!write(ch, b.boxes, buf, l)) {
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

    static void write(OutputChannel ch, int count, int size, BoxEntry[] entries, ByteBuffer buf) throws IOException {
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                if (buf.position() > 0 && buf.remaining() < size) {
                    IO.write(ch, buf.position(), buf);
                }
                entries[i].put(buf);
            }
            if (buf.position() > 0) {
                IO.write(ch, buf.position(), buf);
            }
        }
    }

    public interface OnBoxListener {
        OnBoxListener ALL = new OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                return true;
            }
        };

        /**
         * Give the opportunity to react on box read/write and together
         * with the channel {@code count()} you can keep a good
         * progress of the progress in the ISO media (stream or file).
         *
         * @param b
         * @return true if you want to stop
         */
        boolean onBox(Box b) throws IOException;
    }
}
