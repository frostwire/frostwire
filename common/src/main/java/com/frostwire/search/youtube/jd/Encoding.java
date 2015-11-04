//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.frostwire.search.youtube.jd;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class Encoding {

    /**
     * "http://rapidshare.com&#x2F;&#x66;&#x69;&#x6C;&#x65;&#x73;&#x2F;&#x35;&#x34;&#x35;&#x34;&#x31;&#x34;&#x38;&#x35;&#x2F;&#x63;&#x63;&#x66;&#x32;&#x72;&#x73;&#x64;&#x66;&#x2E;&#x72;&#x61;&#x72;"
     * ; Wandelt alle hexkodierten zeichen in diesem Format in normalen text um
     *
     * @param str
     * @return decoded string
     */
    public static String htmlDecode(String str) {
        if (str == null) { return null; }
        try {
            str = URLDecoder.decode(str, "UTF-8");
        } catch (final Throwable e) {
            e.printStackTrace();
        }
        return Encoding.htmlOnlyDecode(str);
    }

    public static String htmlOnlyDecode(String str) {
        if (str == null) { return null; }
        str = HTMLEntities.unhtmlentities(str);

        str = HTMLEntities.unhtmlAmpersand(str);
        str = HTMLEntities.unhtmlAngleBrackets(str);
        str = HTMLEntities.unhtmlDoubleQuotes(str);
        str = HTMLEntities.unhtmlQuotes(str);
        str = HTMLEntities.unhtmlSingleQuotes(str);
        return str;
    }

    public static void main(String[] args) {
        String test=  "new encoding &#39";
        System.out.println((test));
    }
    public static boolean isUrlCoded(final String str) {
        if (str == null) { return false; }
        try {
            if (URLDecoder.decode(str, "UTF-8").length() != str.length()) {
                return true;
            } else {
                return false;
            }
        } catch (final Exception e) {
            return false;
        }
    }

    public static String urlDecode(String urlcoded, final boolean isUrl) {
        if (urlcoded == null) { return null; }
        if (isUrl) {
            urlcoded = urlcoded.replaceAll("%2F", "/");
            urlcoded = urlcoded.replaceAll("%3A", ":");
            urlcoded = urlcoded.replaceAll("%3F", "?");
            urlcoded = urlcoded.replaceAll("%3D", "=");
            urlcoded = urlcoded.replaceAll("%26", "&");
            urlcoded = urlcoded.replaceAll("%23", "#");
        } else {
            try {
                urlcoded = URLDecoder.decode(urlcoded, "UTF-8");
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        return urlcoded;
    }

    public static String urlEncode_light(final String url) {
        if (url == null) { return null; }
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < url.length(); i++) {
            final char ch = url.charAt(i);
            if (ch == ' ') {
                sb.append("%20");
            } else if (ch >= 33 && ch <= 38) {
                sb.append(ch);
                continue;
            } else if (ch >= 40 && ch <= 59) {
                sb.append(ch);
                continue;
            } else if (ch == 61) {
                sb.append(ch);
                continue;
            } else if (ch >= 63 && ch <= 95) {
                sb.append(ch);
                continue;
            } else if (ch >= 97 && ch <= 126) {
                sb.append(ch);
                continue;
            } else {
                try {
                    sb.append(URLEncoder.encode(String.valueOf(ch), "UTF-8"));
                } catch (final Exception e) {
                    e.printStackTrace();
                    return url;
                }
            }
        }
        return sb.toString();
    }

    public static String UTF8Decode(final String str, final String sourceEncoding) {
        if (str == null) { return null; }
        try {
            if (sourceEncoding != null) {
                return new String(str.getBytes(sourceEncoding), "UTF-8");
            } else {
                return new String(str.getBytes(), "UTF-8");
            }
        } catch (final UnsupportedEncodingException e) {
            e.printStackTrace();
            return str;
        }
    }
}
