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

import java.nio.charset.StandardCharsets;

/**
 * @author gubatron
 * @author aldenml
 */
final class Bits {
    private Bits() {
    }

    public static byte int3(int x) {
        return (byte) (x >> 24);
    }

    public static byte int2(int x) {
        return (byte) (x >> 16);
    }

    public static byte int1(int x) {
        return (byte) (x >> 8);
    }

    public static byte int0(int x) {
        return (byte) (x);
    }

    public static int int32(byte b3, byte b2, byte b1, byte b0) {
        return (((b3) << 24) |
                ((b2 & 0xff) << 16) |
                ((b1 & 0xff) << 8) |
                ((b0 & 0xff)));
    }

    public static String make4cc(int c) {
        byte[] code = new byte[]{int3(c), int2(c), int1(c), int0(c)};
        return new String(code, StandardCharsets.ISO_8859_1);
    }

    public static int make4cc(String c) {
        byte[] code;
        code = c.getBytes(StandardCharsets.ISO_8859_1);
        return int32(code[0], code[1], code[2], code[3]);
    }

    public static String iso639(byte[] arr) {
        if (arr.length != 2) {
            throw new IllegalArgumentException("array must be of length 2");
        }
        int bits = Bits.int32((byte) 0, (byte) 0, arr[0], arr[1]);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            int c = (bits >> (2 - i) * 5) & 0x1f;
            sb.append((char) (c + 0x60));
        }
        return sb.toString();
    }

    public static byte[] iso639(String s) {
        byte[] arr = Utf8.convert(s);
        if (arr.length != 3) {
            throw new IllegalArgumentException("string must be of length 3");
        }
        int bits = 0;
        for (int i = 0; i < 3; i++) {
            bits += (arr[i] - 0x60) << (2 - i) * 5;
        }
        byte b1 = Bits.int1(bits);
        byte b0 = Bits.int0(bits);
        return new byte[]{b1, b0};
    }
}
