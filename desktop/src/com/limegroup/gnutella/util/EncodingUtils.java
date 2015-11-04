package com.limegroup.gnutella.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class EncodingUtils {

    private EncodingUtils() {}
    
    public static String encode(String string) {
        return encode(string, "8859_1");
    }
    
    public static String encode(String string, String encoding) {
        try {
            return URLEncoder.encode(string, encoding);
        } catch(UnsupportedEncodingException uee) {
            return string;
        }
    }

}
