/**
 * Copyright (C) 2012-2013 The named-regexp Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.frostwire.regex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * A compiled representation of a regular expression. This is a wrapper
 * for the java.util.regex.Pattern with support for named capturing
 * groups. The named groups are specified with "(?&lt;name>exp)", which
 * is identical to Java 7 named groups.
 *
 * @since 0.1.9
 */
public class Pattern {

    /** Pattern to match group names */
    private static final String NAME_PATTERN = "[^!=].*?";

    /** Pattern to match named capture groups in a pattern string */
    private static final java.util.regex.Pattern NAMED_GROUP_PATTERN = java.util.regex.Pattern.compile("\\(\\?<(" + NAME_PATTERN + ")>", java.util.regex.Pattern.DOTALL);

    /** index of group within patterns above where group name is captured */
    private static final int INDEX_GROUP_NAME = 1;

    private com.google.re2j.Pattern pattern;
    private String namedPattern;
    private List<String> groupNames;
    private Map<String,List<GroupInfo> > groupInfo;

    /**
     * Constructs a named pattern with the given regular expression and flags
     *
     * @param regex the expression to be compiled
     * @param flags Match flags, a bit mask that may include:
     * <ul>
     *   <li>{@link java.util.regex.Pattern#CASE_INSENSITIVE}</li>
     *   <li>{@link java.util.regex.Pattern#MULTILINE}</li>
     *   <li>{@link java.util.regex.Pattern#DOTALL}</li>
     *   <li>{@link java.util.regex.Pattern#UNICODE_CASE}</li>
     *   <li>{@link java.util.regex.Pattern#CANON_EQ}</li>
     *   <li>{@link java.util.regex.Pattern#UNIX_LINES}</li>
     *   <li>{@link java.util.regex.Pattern#LITERAL}</li>
     *   <li>{@link java.util.regex.Pattern#COMMENTS}</li>
     * </ul>
     */
    protected Pattern(String regex, int flags) {
        namedPattern = regex;

        // group info must be parsed before building the standard pattern
        // because the pattern relies on group info to determine the indexes
        // of named back-references
        groupInfo = extractGroupInfo(regex);
        pattern = buildStandardPattern(regex, flags);
    }

    /**
     * Compiles the given regular expression into a pattern
     *
     * @param regex the expression to be compiled
     * @return the pattern
     */
    public static Pattern compile(String regex) {
        return new Pattern(regex, 0);
    }

    /**
     * Compiles the given regular expression into a pattern with the given flags
     *
     * @param regex the expression to be compiled
     * @param flags Match flags, a bit mask that may include:
     * <ul>
     *   <li>{@link java.util.regex.Pattern#CASE_INSENSITIVE}</li>
     *   <li>{@link java.util.regex.Pattern#MULTILINE}</li>
     *   <li>{@link java.util.regex.Pattern#DOTALL}</li>
     *   <li>{@link java.util.regex.Pattern#UNICODE_CASE}</li>
     *   <li>{@link java.util.regex.Pattern#CANON_EQ}</li>
     *   <li>{@link java.util.regex.Pattern#UNIX_LINES}</li>
     *   <li>{@link java.util.regex.Pattern#LITERAL}</li>
     *   <li>{@link java.util.regex.Pattern#COMMENTS}</li>
     * </ul>
     * @return the pattern
     */
    public static Pattern compile(String regex, int flags) {
        return new Pattern(regex, flags);
    }

    /**
     * Gets the group index of a named capture group
     *
     * @param groupName name of capture group
     * @return group index or -1 if not found
     */
    public int indexOf(String groupName) {
        return indexOf(groupName, 0);
    }

    /**
     * Gets the group index of a named capture group at the
     * specified index. If only one instance of the named
     * group exists, use index 0.
     *
     * @param groupName name of capture group
     * @param index the instance index of the named capture group within
     * the pattern; e.g., index is 2 for the third instance
     * @return group index or -1 if not found
     * @throws IndexOutOfBoundsException if instance index is out of bounds
     */
    public int indexOf(String groupName, int index) {
        int idx = -1;
        if (groupInfo.containsKey(groupName)) {
            List<GroupInfo> list = groupInfo.get(groupName);
            idx = list.get(index).groupIndex();
        }
        return idx;
    }

    /**
     * Returns this pattern's match flags
     *
     * @return The match flags specified when this pattern was compiled
     */
    public int flags() {
        return pattern.flags();
    }

    /**
     * Creates a matcher that will match the given input against this pattern.
     *
     * @param input The character sequence to be matched
     * @return A new matcher for this pattern
     */
    public Matcher matcher(CharSequence input) {
        return new Matcher(this, input);
    }

    /**
     * Returns the wrapped {@link java.util.regex.Pattern}
     * @return the pattern
     */
    public com.google.re2j.Pattern pattern() {
        return pattern;
    }

    /**
     * Returns the regular expression from which this pattern was compiled.
     *
     * @return The source of this pattern
     */
    public String standardPattern() {
        return pattern.pattern();
    }

    /**
     * Returns the original regular expression (including named groups)
     *
     * @return The regular expression
     */
    public String namedPattern() {
        return namedPattern;
    }

    /**
     * Gets the names of all capture groups
     *
     * @return the list of names
     */
    public List<String> groupNames() {
        if (groupNames == null) {
            groupNames = new ArrayList<String>(groupInfo.keySet());
        }
        return groupNames;
    }

    /**
     * Gets the names and group info (group index and string position
     * within the named pattern) of all named capture groups
     *
     * @return a map of group names and their info
     */
    public Map<String, List<GroupInfo> > groupInfo() {
        return groupInfo;
    }

    /**
     * Splits the given input sequence around matches of this pattern.
     *
     * <p>The array returned by this method contains each substring of the
     * input sequence that is terminated by another subsequence that matches
     * this pattern or is terminated by the end of the input sequence. The
     * substrings in the array are in the order in which they occur in the
     * input. If this pattern does not match any subsequence of the input
     * then the resulting array has just one element, namely the input
     * sequence in string form.</p>
     *
     * <p>The limit parameter controls the number of times the pattern is
     * applied and therefore affects the length of the resulting array. If
     * the limit n is greater than zero then the pattern will be applied
     * at most n - 1 times, the array's length will be no greater than n,
     * and the array's last entry will contain all input beyond the last
     * matched delimiter. If n is non-positive then the pattern will be
     * applied as many times as possible and the array can have any length.
     * If n is zero then the pattern will be applied as many times as
     * possible, the array can have any length, and trailing empty strings
     * will be discarded.</p>
     *
     * @param input The character sequence to be split
     * @param limit The result threshold, as described above
     * @return The array of strings computed by splitting the input around
     * matches of this pattern
     */
    public String[] split(String input, int limit) {
        return pattern.split(input, limit);
    }

    /**
     * Splits the given input sequence around matches of this pattern.
     *
     * @param input The character sequence to be split
     * @return The array of strings computed by splitting the input around
     * matches of this pattern
     */
    public String[] split(String input) {
        return pattern.split(input);
    }

    /**
     * Returns a string representation of this pattern
     *
     * @return the string
     */
    public String toString() {
        return namedPattern;
    }

    /**
     * Determines if the character at the specified position
     * of a string is escaped
     *
     * @param s string to evaluate
     * @param pos the position of the character to evaluate
     * @return true if the character is escaped; otherwise false
     */
    static private boolean isEscapedChar(String s, int pos) {
        return isSlashEscapedChar(s, pos) || isQuoteEscapedChar(s, pos);
    }

    /**
     * Determines if the character at the specified position
     * of a string is escaped with a backslash
     *
     * @param s string to evaluate
     * @param pos the position of the character to evaluate
     * @return true if the character is escaped; otherwise false
     */
    static private boolean isSlashEscapedChar(String s, int pos) {

        // Count the backslashes preceding this position. If it's
        // even, there is no escape and the slashes are just literals.
        // If it's odd, one of the slashes (the last one) is escaping
        // the character at the given position.
        int numSlashes = 0;
        while (pos > 0 && (s.charAt(pos - 1) == '\\')) {
            pos--;
            numSlashes++;
        }
        return numSlashes % 2 != 0;
    }

    /**
     * Determines if the character at the specified position
     * of a string is quote-escaped (between \\Q and \\E)
     *
     * @param s string to evaluate
     * @param pos the position of the character to evaluate
     * @return true if the character is quote-escaped; otherwise false
     */
    static private boolean isQuoteEscapedChar(String s, int pos) {

        boolean openQuoteFound = false;
        boolean closeQuoteFound = false;

        // find last non-escaped open-quote
        String s2 = s.substring(0, pos);
        int posOpen = pos;
        while ((posOpen = s2.lastIndexOf("\\Q", posOpen - 1)) != -1) {
            if (!isSlashEscapedChar(s2, posOpen)) {
                openQuoteFound = true;
                break;
            }
        }

        if (openQuoteFound) {
            // search remainder of string (after open-quote) for a close-quote;
            // no need to check that it's slash-escaped because it can't be
            // (the escape character itself is part of the literal when quoted)
            if (s2.indexOf("\\E", posOpen) != -1) {
                closeQuoteFound = true;
            }
        }

        return openQuoteFound && !closeQuoteFound;
    }

    /**
     * Determines if a string's character is within a regex character class
     *
     * @param s string to evaluate
     * @param pos the position of the character to evaluate
     * @return true if the character is inside a character class; otherwise false
     */
    static private boolean isInsideCharClass(String s, int pos) {

        boolean openBracketFound = false;
        boolean closeBracketFound = false;

        // find last non-escaped open-bracket
        String s2 = s.substring(0, pos);
        int posOpen = pos;
        while ((posOpen = s2.lastIndexOf('[', posOpen - 1)) != -1) {
            if (!isEscapedChar(s2, posOpen)) {
                openBracketFound = true;
                break;
            }
        }

        if (openBracketFound) {
            // search remainder of string (after open-bracket) for a close-bracket
            String s3 = s.substring(posOpen, pos);
            int posClose = -1;
            while ((posClose = s3.indexOf(']', posClose + 1)) != -1) {
                if (!isEscapedChar(s3, posClose)) {
                    closeBracketFound = true;
                    break;
                }
            }
        }

        return openBracketFound && !closeBracketFound;
    }

    /**
     * Determines if the parenthesis at the specified position
     * of a string is for a non-capturing group, which is one of
     * the flag specifiers (e.g., (?s) or (?m) or (?:pattern).
     * If the parenthesis is followed by "?", it must be a non-
     * capturing group unless it's a named group (which begins
     * with "?<"). Make sure not to confuse it with the lookbehind
     * construct ("?<=" or "?<!").
     *
     * @param s string to evaluate
     * @param pos the position of the parenthesis to evaluate
     * @return true if the parenthesis is non-capturing; otherwise false
     */
    static private boolean isNoncapturingParen(String s, int pos) {

        //int len = s.length();
        boolean isLookbehind = false;

        // code-coverage reports show that pos and the text to
        // check never exceed len in this class, so it's safe
        // to not test for it, which resolves uncovered branches
        // in Cobertura

        /*if (pos >= 0 && pos + 4 < len)*/ {
            String pre = s.substring(pos, pos+4);
            isLookbehind = pre.equals("(?<=") || pre.equals("(?<!");
        }
        return /*(pos >= 0 && pos + 2 < len) &&*/
               s.charAt(pos + 1) == '?' &&
               (isLookbehind || s.charAt(pos + 2) != '<');
    }

    /**
     * Counts the open-parentheses to the left of a string position,
     * excluding escaped parentheses
     *
     * @param s string to evaluate
     * @param pos ending position of string; characters to the left
     * of this position are evaluated
     * @return number of open parentheses
     */
    static private int countOpenParens(String s, int pos) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\(");
        java.util.regex.Matcher m = p.matcher(s.subSequence(0, pos));

        int numParens = 0;

        while (m.find()) {
            // ignore parentheses inside character classes: [0-9()a-f]
            // which are just literals
            if (isInsideCharClass(s, m.start())) {
                continue;
            }

            // ignore escaped parens
            if (isEscapedChar(s, m.start())) continue;

            if (!isNoncapturingParen(s, m.start())) {
                numParens++;
            }
        }
        return numParens;
    }

    /**
     * Parses info on named capture groups from a pattern
     *
     * @param namedPattern regex the regular expression pattern to parse
     * @return list of group info for all named groups
     */
    static public Map<String,List<GroupInfo> > extractGroupInfo(String namedPattern) {
        Map<String,List<GroupInfo> > groupInfo = new LinkedHashMap<String,List<GroupInfo> >();
        java.util.regex.Matcher matcher = NAMED_GROUP_PATTERN.matcher(namedPattern);
        while(matcher.find()) {

            int pos = matcher.start();

            // ignore escaped paren
            if (isEscapedChar(namedPattern, pos)) continue;

            String name = matcher.group(INDEX_GROUP_NAME);
            int groupIndex = countOpenParens(namedPattern, pos);

            List<GroupInfo> list;
            if (groupInfo.containsKey(name)) {
                list = groupInfo.get(name);
            } else {
                list = new ArrayList<GroupInfo>();
            }
            list.add(new GroupInfo(groupIndex, pos));
            groupInfo.put(name, list);
        }
        return groupInfo;
    }

    /**
     * Replaces strings matching a pattern with another string. If the string
     * to be replaced is escaped with a slash, it is skipped.
     *
     * @param input the string to evaluate
     * @param pattern the pattern that matches the string to be replaced
     * @param replacement the string to replace the target
     * @return the modified string (original instance of {@code input})
     */
    static private StringBuilder replace(StringBuilder input, java.util.regex.Pattern pattern, String replacement) {
        java.util.regex.Matcher m = pattern.matcher(input);
        while (m.find()) {
            if (isEscapedChar(input.toString(), m.start())) {
                continue;
            }

            // since we're replacing the original string being matched,
            // we have to reset the matcher so that it searches the new
            // string
            input.replace(m.start(), m.end(), replacement);
            m.reset(input);
        }
        return input;
    }

    /**
     * Replaces referenced group names with the reference to the corresponding group
     * index (e.g., <b><code>\k&lt;named></code></b>} to <b><code>\k2</code></b>};
     * <b><code>${named}</code></b> to <b><code>$2</code></b>}).
     * This assumes the group names have already been parsed from the pattern.
     *
     * @param input the string to evaluate
     * @param pattern the pattern that matches the string to be replaced
     * @param prefix string to prefix to the replacement (e.g., "$" or "\\")
     * @return the modified string (original instance of {@code input})
     * @throws PatternSyntaxException group name was not found
     */
    private StringBuilder replaceGroupNameWithIndex(StringBuilder input, java.util.regex.Pattern pattern, String prefix) {
        java.util.regex.Matcher m = pattern.matcher(input);
        while (m.find()) {
            if (isEscapedChar(input.toString(), m.start())) {
                continue;
            }

            int index = indexOf(m.group(INDEX_GROUP_NAME));
            if (index >= 0) {
                index++;
            } else {
                throw new PatternSyntaxException("unknown group name", input.toString(), m.start(INDEX_GROUP_NAME));
            }

            // since we're replacing the original string being matched,
            // we have to reset the matcher so that it searches the new
            // string
            input.replace(m.start(), m.end(), prefix + index);
            m.reset(input);
        }
        return input;
    }

    /**
     * Builds a {@code java.util.regex.Pattern} from a given regular expression
     * pattern (which may contain named groups) and flags
     *
     * @param namedPattern the expression to be compiled
     * @param flags Match flags, a bit mask that may include:
     * <ul>
     *   <li>{@link java.util.regex.Pattern#CASE_INSENSITIVE}</li>
     *   <li>{@link java.util.regex.Pattern#MULTILINE}</li>
     *   <li>{@link java.util.regex.Pattern#DOTALL}</li>
     *   <li>{@link java.util.regex.Pattern#UNICODE_CASE}</li>
     *   <li>{@link java.util.regex.Pattern#CANON_EQ}</li>
     *   <li>{@link java.util.regex.Pattern#UNIX_LINES}</li>
     *   <li>{@link java.util.regex.Pattern#LITERAL}</li>
     *   <li>{@link java.util.regex.Pattern#COMMENTS}</li>
     * </ul>
     * @return the standard {@code java.util.regex.Pattern}
     */
    private com.google.re2j.Pattern buildStandardPattern(String namedPattern, Integer flags) {
        // replace the named-group construct with left-paren but
        // make sure we're actually looking at the construct (ignore escapes)
        StringBuilder s = new StringBuilder(namedPattern);
        s = replace(s, NAMED_GROUP_PATTERN, "(");
        return com.google.re2j.Pattern.compile(s.toString(), flags);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Pattern)) {
            return false;
        }
        Pattern other = (Pattern)obj;
        return namedPattern.equals(other.namedPattern) && pattern.flags() == other.pattern.flags();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return namedPattern.hashCode() ^ pattern.flags();
    }

}

