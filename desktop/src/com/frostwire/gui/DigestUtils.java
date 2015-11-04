package com.frostwire.gui;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;

public class DigestUtils {
    public final static boolean checkMD5(File f, String expectedMD5) {
        return checkMD5(f, expectedMD5, null);
    }

    /**
     * Returns true if the MD5 of the file corresponds to the given MD5 string.
     * It works with lowercase or uppercase, you don't need to worry about that.
     * 
     * @param f
     * @param expectedMD5
     * @return
     * @throws Exception
     */
    public final static boolean checkMD5(File f, String expectedMD5, DigestProgressListener listener) {
        if (!isValidMD5(expectedMD5)) {
            return false;
        }

        String md5 = getMD5(f, listener);
        return compareMD5(md5, expectedMD5);
    }

    public final static boolean compareMD5(String md5a, String md5b) {
        if ((!isValidMD5(md5a)) || (!isValidMD5(md5b))) {
            return false;
        }

        return md5a.equalsIgnoreCase(md5b);
    }

    private static boolean isValidMD5(String md5) {
        if (md5 == null) {
            return false;
        }
        
        // Check length AND characters
        return md5.matches("^[0-9A-Fa-f]{32}+$");
    }

    public final static String getMD5(File f) {
        return getMD5(f, null);
    }
    
    public final static String getMD5(File f, DigestProgressListener listener) {
        try {
            return getMD5(new FileInputStream(f), f.length(), listener);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public final static String getMD5(InputStream is, long streamLength, DigestProgressListener listener) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");

            byte[] buf = new byte[1024*4];
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
                        } catch (Exception e) {
                        }
                    }
                    
                    if (listener.stopDigesting()) {
                        stopped = true;
                    }
                }
            }

            
            in.close();

            if (!stopped) {
                String result = new BigInteger(1, m.digest()).toString(16);
    
                // pad with zeros if until it's 32 chars long.
                if (result.length() < 32) {
                    int paddingSize = 32 - result.length();
                    for (int i = 0; i < paddingSize; i++) {
                        result = "0" + result;
                    }
                }
                return result;
            } else {
                return null;
            }


        } catch (Exception e) {
            return null;
        }
    }
    
    public final static String getMD5(String s) {
        try {
            byte[] bytes = s.getBytes("UTF-8");
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return getMD5(bais,bytes.length,null);

        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public interface DigestProgressListener {
        public void onProgress(int progressPercentage);
        public boolean stopDigesting();
    }
}
