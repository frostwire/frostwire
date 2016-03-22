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

    public static void read(InputChannel in, int len, ByteBuffer buf) throws IOException {
        if (len <= 0) {
            throw new IllegalArgumentException("len argument must be > 0");
        }

        buf.clear().limit(len);

        int n = 0;
        int r;
        do {
            if ((r = in.read(buf)) < 0) {
                throw new EOFException();
            }
            n += r;
        } while (n < len);

        buf.flip();
    }

    public static void skip(InputChannel in, long len, ByteBuffer buf) throws IOException {
        if (len <= 0) {
            throw new IllegalArgumentException("len argument must be > 0");
        }

        int size = buf.clear().capacity();
        long a = len / size;
        int b = (int) (len % size);

        for (long i = 0; i < a; i++) {
            read(in, size, buf);
        }

        if (b > 0) {
            read(in, b, buf);
        }
    }

    public static void skip(InputChannel in, ByteBuffer buf) throws IOException {
        long len = Long.MAX_VALUE;
        boolean eof = false;
        do {
            try {
                skip(in, len, buf);
            } catch (EOFException e) {
                eof = true;
            }
        } while (!eof);
    }

    public static ByteBuffer get(ByteBuffer buf, int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = buf.getInt();
        }
        return buf;
    }
}
