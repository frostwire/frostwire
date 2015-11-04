/**
 * Decodes a string from x-www-urlencoded format
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
package com.limegroup.gnutella.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.limewire.service.ErrorService;


public class URLDecoder {

    /**
     * decodes a strong in x-www-urldecoded format and returns the 
     * the decoded string.
     */
    public static String decode(String s) throws IOException {
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '+':
                    sb.append(' ');
                    break;
                case '%':
                    if(i+3 > s.length()) {
                        throw new IOException("invalid url: "+s);
                    }
                    try {
                        sb.append((char)Integer.parseInt(
                            s.substring(i+1,i+3),16));
                    } catch (NumberFormatException e) {
                        throw new IOException("invalid url: "+s);
                    }
                    i += 2;
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        // Undo conversion to external encoding
        String result = sb.toString();
        try {
            byte[] inputBytes = result.getBytes("8859_1");
            result = new String(inputBytes);
        } catch (UnsupportedEncodingException e) {
            // The system should always have 8859_1
            ErrorService.error(e);
        }
        return result;
    }
}


