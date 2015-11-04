/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.util;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class ByteUtils {

    private static final char[] DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private ByteUtils() {
    }

    public static int randomInt(int min, int max) {
        Random random = new Random(System.currentTimeMillis());
        return min + random.nextInt(max - min);
    }

    public static byte[] decodeHex(String str) {
        str = str.toLowerCase(Locale.US);
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
        }
        return data;
    }

    public static String  encodeHex(byte[] data) {
        int l = data.length;
        char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = DIGITS[(0xF0 & data[i]) >>> 4];
            out[j++] = DIGITS[0x0F & data[i]];
        }
        return new String(out);
    }

    public static byte[] uuidToByteArray(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] buffer = new byte[16];

        for (int i = 0; i < 8; i++) {
            buffer[i] = (byte) (msb >>> 8 * (7 - i));
        }
        for (int i = 8; i < 16; i++) {
            buffer[i] = (byte) (lsb >>> 8 * (7 - i));
        }

        return buffer;
    }

    public static byte[] longToByteArray(long l) {
        return ByteBuffer.allocate(8).putLong(l).array();
    }

    public static long byteArrayToLong(byte[] arr) {
        return ByteBuffer.wrap(arr).getLong();
    }

    /*
    private static void testLongByteArrayConversions() {
        long l = 3212831239812l;
        byte[] larr = longToByteArray(l);
        long l2 = byteArrayToLong(larr);
        System.out.println("testLongByteArrayConversions passed?" + (l == l2));
        System.out.println("testLongByteArrayConversions Long.MAX_VALUE passed?" + (byteArrayToLong(longToByteArray(Long.MAX_VALUE)) == Long.MAX_VALUE));
        System.out.println("testLongByteArrayConversions Long.MIN_VALUE passed?" + (byteArrayToLong(longToByteArray(Long.MIN_VALUE)) == Long.MIN_VALUE));
        System.out.println("testLongByteArrayConversions 0 passed?" + (byteArrayToLong(longToByteArray(0)) == 0));
        System.out.println("testLongByteArrayConversions 1 passed?" + (byteArrayToLong(longToByteArray(1)) == 1));
    }
    */

    /*
    public static void main(String[] args) {
        testLongByteArrayConversions();
    }
    */
}
