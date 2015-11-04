
package com.andrew.apollo.format;

import android.text.TextUtils;

public class Capitalize {

    /* This class is never initiated */
    public Capitalize() {
    }

    public static final String capitalize(String str) {
        return capitalize(str, null);
    }

    /**
     * Capitalizes the first character in a string
     * 
     * @param str The string to capitalize
     * @param delimiters The delimiters
     * @return A captitalized string
     */
    public static final String capitalize(String str, char... delimiters) {
        final int delimLen = delimiters == null ? -1 : delimiters.length;
        if (TextUtils.isEmpty(str) || delimLen == 0) {
            return str;
        }
        final char[] buffer = str.toCharArray();
        boolean capitalizeNext = true;
        for (int i = 0; i < buffer.length; i++) {
            char ch = buffer[i];
            if (isDelimiter(ch, delimiters)) {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                buffer[i] = Character.toTitleCase(ch);
                capitalizeNext = false;
            }
        }
        return new String(buffer);
    }

    /**
     * Is the character a delimiter.
     * 
     * @param ch the character to check
     * @param delimiters the delimiters
     * @return true if it is a delimiter
     */
    private static final boolean isDelimiter(char ch, char[] delimiters) {
        if (delimiters == null) {
            return Character.isWhitespace(ch);
        }
        for (char delimiter : delimiters) {
            if (ch == delimiter) {
                return true;
            }
        }
        return false;
    }
}
