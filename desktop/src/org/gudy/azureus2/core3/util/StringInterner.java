package org.gudy.azureus2.core3.util;

import java.net.URL;

public class StringInterner {

    public static URL internURL(URL url) {
        return url;
    }

    public static byte[] internBytes(byte[] bytes) {
        return bytes;
    }

    public static String intern(String url_str) {
        return url_str;
    }
}
