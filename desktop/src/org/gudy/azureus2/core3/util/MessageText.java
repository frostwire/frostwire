package org.gudy.azureus2.core3.util;

import org.gudy.azureus2.core3.util.Constants;

import java.util.Locale;

public class MessageText {

    public static final Locale LOCALE_ENGLISH = Constants.LOCALE_ENGLISH;

    public static final Locale LOCALE_DEFAULT = new Locale("", ""); // == english

    private static Locale LOCALE_CURRENT = LOCALE_DEFAULT;

    public static String getString(String s, String[] strings) {
        return s;
    }

    public static String getString(String s) {
        return s;
    }
}
