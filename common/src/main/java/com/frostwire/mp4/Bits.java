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
