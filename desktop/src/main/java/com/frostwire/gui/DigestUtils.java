/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.gui;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;

public class DigestUtils {
    /**
     * Returns true if the MD5 of the file corresponds to the given MD5 string.
     * It works with lowercase or uppercase, you don't need to worry about that.
     */
    public static boolean checkMD5(File f, String expectedMD5, DigestProgressListener listener) {
        if (isInvalidMD5(expectedMD5)) {
            return false;
        }
        String md5 = getMD5(f, listener);
        return compareMD5(md5, expectedMD5);
    }

    public static boolean compareMD5(String md5a, String md5b) {
        if ((isInvalidMD5(md5a)) || (isInvalidMD5(md5b))) {
            return false;
        }
        return md5a.equalsIgnoreCase(md5b);
    }

    private static boolean isInvalidMD5(String md5) {
        if (md5 == null) {
            return true;
        }
        // Check length AND characters
        return !md5.matches("^[0-9A-Fa-f]{32}+$");
    }

    public static String getMD5(File f) {
        return getMD5(f, null);
    }

    private static String getMD5(File f, DigestProgressListener listener) {
        try {
            return getMD5(new FileInputStream(f), f.length(), listener);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private static String getMD5(InputStream is, long streamLength, DigestProgressListener listener) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[1024 * 4];
            int num_read;
            InputStream in = new BufferedInputStream(is);
            long total_read = 0;
            boolean stopped = false;
            while (!stopped && (num_read = in.read(buf)) != -1) {
                total_read += num_read;
                m.update(buf, 0, num_read);
                if (listener != null) {
                    if (streamLength > 0) {
                        int progressPercentage = (int) (total_read * 100 / streamLength);
                        try {
                            listener.onProgress(progressPercentage);
                        } catch (Exception ignored) {
                        }
                    }
                    if (listener.stopDigesting()) {
                        stopped = true;
                    }
                }
            }
            in.close();
            if (!stopped) {
                StringBuilder result = new StringBuilder(new BigInteger(1, m.digest()).toString(16));
                // pad with zeros if until it's 32 chars long.
                if (result.length() < 32) {
                    int paddingSize = 32 - result.length();
                    for (int i = 0; i < paddingSize; i++) {
                        result.insert(0, "0");
                    }
                }
                return result.toString();
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public interface DigestProgressListener {
        void onProgress(int progressPercentage);

        boolean stopDigesting();
    }
}
