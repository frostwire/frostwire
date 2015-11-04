/**
 * Copyright (c) 2009 - 2010 AppWork UG(haftungsbeschränkt) <e-mail@appwork.org>
 * 
 * This file is part of org.appwork.utils
 * 
 * This software is licensed under the Artistic License 2.0,
 * see the LICENSE file or http://www.opensource.org/licenses/artistic-license-2.0.php
 * for details
 */
package com.frostwire.search.youtube.jd;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author thomas
 * 
 */

public class Regex {

    public static String escape(final String pattern) {
        return Pattern.quote(pattern);
    }

    public static String[] getLines(final String arg) {
        if (arg == null) {
            return new String[] {};
        } else {
            final String[] temp = arg.split("[\r\n]{1,2}");
            final int tempLength = temp.length;
            final String[] output = new String[tempLength];
            for (int i = 0; i < tempLength; i++) {
                output[i] = new String(temp[i].trim());
            }
            return output;
        }
    }

    public static boolean matches(final Object str, final Pattern pat) {
        return new Regex(str, pat).matches();
    }

    public static boolean matches(final Object page, final String string) {
        return new Regex(page, string).matches();
    }

    /**
     * @param sslResponse
     * @param string
     * @param string2
     * @return
     */
    public static String replace(final String text, final String regex, final String replacement) {

        return Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE).matcher(text).replaceAll(replacement);
    }

    private Matcher matcher;

    private boolean memOpt = true;

    public Regex(final Matcher matcher) {
        if (matcher != null) {
            this.matcher = matcher;
        }
    }

    public Regex(final Object data, final Pattern pattern) {
        this(data.toString(), pattern);
    }

    public Regex(final Object data, final String pattern) {
        this(data.toString(), pattern);
    }

    public Regex(final Object data, final String pattern, final int flags) {
        this(data.toString(), pattern, flags);
    }

    public Regex(final String data, final Pattern pattern) {
        if (data != null && pattern != null) {
            this.matcher = pattern.matcher(data);
        }
    }

    public Regex(final String data, final String pattern) {
        if (data != null && pattern != null) {
            this.matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(data);
        }
    }

    public Regex(final String data, final String pattern, final int flags) {
        if (data != null && pattern != null) {
            this.matcher = Pattern.compile(pattern, flags).matcher(data);
        }
    }

    /**
     * Gibt die Anzahl der Treffer zurück
     * 
     * @return
     */
    public int count() {
        if (this.matcher == null) {
            return 0;
        } else {
            this.matcher.reset();
            int c = 0;
            final Matcher matchertmp = this.matcher;
            while (matchertmp.find()) {
                c++;
            }
            return c;
        }
    }

    public String[] getColumn(int x) {
        if (this.matcher == null) {
            return null;
        } else {
            x++;
            final Matcher matcher = this.matcher;
            matcher.reset();

            final java.util.List<String> ar = new ArrayList<String>();
            while (matcher.find()) {
                String tmp = matcher.group(x);
                if (tmp != null && this.memOpt) {
                    tmp = new String(tmp);
                }
                ar.add(tmp);
            }
            return ar.toArray(new String[ar.size()]);
        }
    }

    public String getMatch(final int group) {
        if (this.matcher != null) {
            final Matcher matcher = this.matcher;
            matcher.reset();
            if (matcher.find()) {
                final String ret = matcher.group(group + 1);
                if (ret != null && this.memOpt) { return new String(ret); }
                return ret;
            }
        }
        return null;
    }

    public String getMatch(int entry, final int group) {
        if (this.matcher != null) {
            final Matcher matcher = this.matcher;
            matcher.reset();
            // group++;
            entry++;
            int groupCount = 0;
            while (matcher.find()) {
                if (groupCount == group) {
                    final String ret = matcher.group(entry);
                    if (ret != null && this.memOpt) { return new String(ret); }
                    return ret;
                }
                groupCount++;
            }
        }
        return null;
    }

    public Matcher getMatcher() {
        if (this.matcher != null) {
            this.matcher.reset();
        }
        return this.matcher;
    }

    /**
     * Gibt alle Treffer eines Matches in einem 2D array aus
     * 
     * @return
     */
    public String[][] getMatches() {
        if (this.matcher == null) {
            return null;
        } else {
            final Matcher matcher = this.matcher;
            matcher.reset();
            final java.util.List<String[]> ar = new ArrayList<String[]>();
            while (matcher.find()) {
                final int c = matcher.groupCount();
                int d = 1;
                String[] group;
                if (c == 0) {
                    group = new String[c + 1];
                    d = 0;
                } else {
                    group = new String[c];
                }

                for (int i = d; i <= c; i++) {
                    String tmp = matcher.group(i);
                    if (tmp != null && this.memOpt) {
                        tmp = new String(tmp);
                    }
                    group[i - d] = tmp;
                }
                ar.add(group);
            }
            return ar.size() == 0 ? new String[][] {} : ar.toArray(new String[][] {});
        }
    }

    public String[] getRow(final int y) {
        if (this.matcher != null) {
            final Matcher matcher = this.matcher;
            matcher.reset();
            int groupCount = 0;
            while (matcher.find()) {
                if (groupCount == y) {
                    final int c = matcher.groupCount();

                    final String[] group = new String[c];

                    for (int i = 1; i <= c; i++) {
                        String tmp = matcher.group(i);
                        if (tmp != null && this.memOpt) {
                            tmp = new String(tmp);
                        }
                        group[i - 1] = tmp;
                    }
                    return group;
                }
                groupCount++;
            }
        }
        return null;
    }

    public boolean matches() {
        final Matcher matcher = this.matcher;
        if (matcher == null) {
            return false;
        } else {
            matcher.reset();
            return matcher.find();
        }
    }

    /**
     * Setzt den Matcher
     * 
     * @param matcher
     */
    public void setMatcher(final Matcher matcher) {
        this.matcher = matcher;
    }

    public Regex setMemoryOptimized(final boolean t) {
        this.memOpt = t;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder ret = new StringBuilder();
        final String[][] matches = this.getMatches();
        final int matchesLength = matches.length;
        String[] match;
        int matchLength;
        for (int i = 0; i < matchesLength; i++) {
            match = matches[i];
            matchLength = match.length;
            for (int j = 0; j < matchLength; j++) {
                ret.append("match[");
                ret.append(i);
                ret.append("][");
                ret.append(j);
                ret.append("] = ");
                ret.append(match[j]);
                ret.append(System.getProperty("line.separator"));
            }
        }
        this.matcher.reset();
        return ret.toString();
    }

}
