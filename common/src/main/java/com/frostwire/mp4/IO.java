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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
final class IO {
    private IO() {
    }

    public static void read(InputChannel ch, int len, ByteBuffer buf) throws IOException {
        if (len <= 0) {
            throw new IllegalArgumentException("len argument must be > 0");
        }
        buf.clear().limit(len);
        int n = 0;
        int r;
        do {
            if ((r = ch.read(buf)) < 0) {
                throw new EOFException();
            }
            n += r;
        } while (n < len);
        buf.flip();
    }

    public static void skip(InputChannel ch, long len, ByteBuffer buf) throws IOException {
        if (len <= 0) {
            throw new IllegalArgumentException("len argument must be > 0");
        }
        int size = buf.clear().capacity();
        long a = len / size;
        int b = (int) (len % size);
        for (long i = 0; i < a; i++) {
            read(ch, size, buf);
        }
        if (b > 0) {
            read(ch, b, buf);
        }
    }

    public static void skip(InputChannel ch, ByteBuffer buf) throws IOException {
        long len = Long.MAX_VALUE;
        boolean eof = false;
        do {
            try {
                skip(ch, len, buf);
            } catch (EOFException e) {
                eof = true;
            }
        } while (!eof);
    }

    public static void write(OutputChannel ch, int len, ByteBuffer buf) throws IOException {
        if (len <= 0) {
            throw new IllegalArgumentException("len argument must be > 0");
        }
        buf.flip().limit(len);
        int n = 0;
        do {
            n += ch.write(buf);
        } while (n < len);
        buf.clear();
    }

    public static void skip(OutputChannel ch, long len, ByteBuffer buf) throws IOException {
        if (len <= 0) {
            throw new IllegalArgumentException("len argument must be > 0");
        }
        int size = buf.clear().capacity();
        long a = len / size;
        int b = (int) (len % size);
        for (long i = 0; i < a; i++) {
            write(ch, size, buf);
        }
        if (b > 0) {
            write(ch, b, buf);
        }
    }

    public static void copy(InputChannel src, OutputChannel dst, long len, ByteBuffer buf) throws IOException {
        if (len <= 0) {
            throw new IllegalArgumentException("len argument must be > 0");
        }
        int size = buf.clear().capacity();
        long a = len / size;
        int b = (int) (len % size);
        for (long i = 0; i < a; i++) {
            read(src, size, buf);
            write(dst, size, buf);
        }
        if (b > 0) {
            read(src, b, buf);
            write(dst, b, buf);
        }
    }

    public static ByteBuffer get(ByteBuffer buf, int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = buf.getInt();
        }
        return buf;
    }

    public static ByteBuffer get(ByteBuffer buf, short[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = buf.getShort();
        }
        return buf;
    }

    public static byte[] str(ByteBuffer buf) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte b;
        while ((b = buf.get()) != 0) {
            out.write(b);
        }
        return out.toByteArray();
    }

    public static ByteBuffer put(ByteBuffer buf, int[] arr) {
        for (int anArr : arr) {
            buf.putInt(anArr);
        }
        return buf;
    }

    public static ByteBuffer put(ByteBuffer buf, short[] arr) {
        for (short anArr : arr) {
            buf.putShort(anArr);
        }
        return buf;
    }

    public static void close(Closeable f) {
        try {
            if (f != null) {
                f.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }
}
