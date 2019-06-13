/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.settings;

import com.limegroup.gnutella.MediaType;
import org.limewire.setting.*;
import org.limewire.util.CommonUtils;

import java.io.File;

/**
 * Settings for searches.
 */
public final class SearchSettings extends LimeProps {
    /**
     * Amount of time to wait in milliseconds before showing details page
     */
    public static final long SHOW_DETAILS_DELAY = 8000;
    /**
     * Setting for the maximum number of bytes to allow in queries.
     */
    public static final IntSetting MAX_QUERY_LENGTH =
            FACTORY.createIntSetting("MAX_QUERY_LENGTH", 512);
    public static final int MAXIMUM_PARALLEL_SEARCH = 10;
    /**
     * The maximum number of simultaneous searches to allow.
     */
    public static final IntSetting PARALLEL_SEARCH =
            FACTORY.createIntSetting("PARALLEL_SEARCH", MAXIMUM_PARALLEL_SEARCH);
    /**
     * Whether or not to enable the spam filter.
     */
    public static final BooleanSetting ENABLE_SPAM_FILTER =
            FACTORY.createBooleanSetting("ENABLE_SPAM_FILTER", true);
    public static final FileSetting SMART_SEARCH_DATABASE_FOLDER = FACTORY.createFileSetting("SMART_SEARCH_DATABASE_FOLDER", new File(CommonUtils.getUserSettingsDir(), "search_db"));
    public static final StringSetting LAST_MEDIA_TYPE_USED = FACTORY.createStringSetting("LAST_MEDIA_TYPE_USED", MediaType.getAudioMediaType().getMimeType());
    public static final BooleanSetting SMART_SEARCH_ENABLED = FACTORY.createBooleanSetting("SMART_SEARCH_ENABLED", true);
    public static final BooleanSetting SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START = FACTORY.createBooleanSetting("SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START", false);
    /**
     * Constant for the characters that are banned from search
     * strings.
     */
    private static final char[] BAD_CHARS = {
            '_', '#', '!', '|', '?', '<', '>', '^', '(', ')',
            ':', ';', '/', '\\', '[', ']',
            '\t', '\n', '\r', '\f', // these cannot be last or first 'cause they're trimmed
            '{', '}', '@',
            /* CHARACTERS THAT TURN AFTER NORMALIZATION TO BAD CHARS */
            // Characters that turn into ';'
            '\u037E', // GREEK QUESTION MARK
            '\uFE54', // SMALL SEMICOLON
            '\uFF1B', // FULL WIDTH SEMICOLON
            // Characters that turn into '!'
            '\u203C', // DOUBLE EXCLAMATION MARK
            '\u2048', // QUESTION EXCLAMATION MARK
            '\u2049', // EXCLAMATION QUESTION MARK
            '\uFE57', // SMALL EXCLAMATION MARK
            '\uFF01', // FULL WIDTH EXCLAMATION MARK
            // Characters that turn into '?'
            '\u2047', // DOUBLE QUESTION MARK
            // '\u2048', // QUESTION EXCLAMATION MARK (see '!')
            // '\u2049', // EXCLAMATION QUESTION MARK (see '!')
            '\uFE56', // SMALL QUESTION MARK
            '\uFF1F', // FULL WIDTH QUESTION MARK
            // Characters that turn into '('
            '\u207D', // SUPERSCRIPT LEFT PARENTHESIS
            '\u208D', // SUBSCRIPT LEFT PARENTHESIS
            '\u2474', // PARENTHESIZED DIGIT ONE
            '\u2475', // PARENTHESIZED DIGIT TWO
            '\u2476', // PARENTHESIZED DIGIT THREE
            '\u2477', // PARENTHESIZED DIGIT FOUR
            '\u2478', // PARENTHESIZED DIGIT FIVE
            '\u2479', // PARENTHESIZED DIGIT SIX
            '\u247A', // PARENTHESIZED DIGIT SEVEN
            '\u247B', // PARENTHESIZED DIGIT EIGHT
            '\u247C', // PARENTHESIZED DIGIT NINE
            '\u247D', // PARENTHESIZED NUMBER TEN
            '\u247E', // PARENTHESIZED NUMBER ELEVEN
            '\u247F', // PARENTHESIZED NUMBER TWELVE
            '\u2480', // PARENTHESIZED NUMBER THIRTEEN
            '\u2481', // PARENTHESIZED NUMBER FOURTEEN
            '\u2482', // PARENTHESIZED NUMBER FIFTEEN
            '\u2483', // PARENTHESIZED NUMBER SIXTEEN
            '\u2484', // PARENTHESIZED NUMBER SEVENTEEN
            '\u2485', // PARENTHESIZED NUMBER EIGHTEEN
            '\u2486', // PARENTHESIZED NUMBER NINETEEN
            '\u2487', // PARENTHESIZED NUMBER TWENTY
            '\u249C', // PARENTHESIZED LATIN SMALL LETTER A
            '\u249D', // PARENTHESIZED LATIN SMALL LETTER B
            '\u249E', // PARENTHESIZED LATIN SMALL LETTER C
            '\u249F', // PARENTHESIZED LATIN SMALL LETTER D
            '\u24A0', // PARENTHESIZED LATIN SMALL LETTER E
            '\u24A1', // PARENTHESIZED LATIN SMALL LETTER F
            '\u24A2', // PARENTHESIZED LATIN SMALL LETTER G
            '\u24A3', // PARENTHESIZED LATIN SMALL LETTER H
            '\u24A4', // PARENTHESIZED LATIN SMALL LETTER I
            '\u24A5', // PARENTHESIZED LATIN SMALL LETTER J
            '\u24A6', // PARENTHESIZED LATIN SMALL LETTER K
            '\u24A7', // PARENTHESIZED LATIN SMALL LETTER L
            '\u24A8', // PARENTHESIZED LATIN SMALL LETTER M
            '\u24A9', // PARENTHESIZED LATIN SMALL LETTER N
            '\u24AA', // PARENTHESIZED LATIN SMALL LETTER O
            '\u24AB', // PARENTHESIZED LATIN SMALL LETTER P
            '\u24AC', // PARENTHESIZED LATIN SMALL LETTER Q
            '\u24AD', // PARENTHESIZED LATIN SMALL LETTER R
            '\u24AE', // PARENTHESIZED LATIN SMALL LETTER S
            '\u24AF', // PARENTHESIZED LATIN SMALL LETTER T
            '\u24B0', // PARENTHESIZED LATIN SMALL LETTER U
            '\u24B1', // PARENTHESIZED LATIN SMALL LETTER V
            '\u24B2', // PARENTHESIZED LATIN SMALL LETTER W
            '\u24B3', // PARENTHESIZED LATIN SMALL LETTER X
            '\u24B4', // PARENTHESIZED LATIN SMALL LETTER Y
            '\u24B5', // PARENTHESIZED LATIN SMALL LETTER Z
            '\u3200', // PARENTHESIZED HANGUL KIYEOK
            '\u3201', // PARENTHESIZED HANGUL NIEUN
            '\u3202', // PARENTHESIZED HANGUL TIKEUT
            '\u3203', // PARENTHESIZED HANGUL RIEUL
            '\u3204', // PARENTHESIZED HANGUL MIEUM
            '\u3205', // PARENTHESIZED HANGUL PIEUP
            '\u3206', // PARENTHESIZED HANGUL SIOS
            '\u3207', // PARENTHESIZED HANGUL IEUNG
            '\u3208', // PARENTHESIZED HANGUL CIEUC
            '\u3209', // PARENTHESIZED HANGUL CHIEUCH
            '\u320A', // PARENTHESIZED HANGUL KHIEUKH
            '\u320B', // PARENTHESIZED HANGUL THIEUTH
            '\u320C', // PARENTHESIZED HANGUL PHIEUPH
            '\u320D', // PARENTHESIZED HANGUL HIEUH
            '\u320E', // PARENTHESIZED HANGUL KIYEOK A
            '\u320F', // PARENTHESIZED HANGUL NIEUN A
            '\u3210', // PARENTHESIZED HANGUL TIKEUT A
            '\u3211', // PARENTHESIZED HANGUL RIEUL A
            '\u3212', // PARENTHESIZED HANGUL MIEUM A
            '\u3213', // PARENTHESIZED HANGUL PIEUP A
            '\u3214', // PARENTHESIZED HANGUL SIOS A
            '\u3215', // PARENTHESIZED HANGUL IEUNG A
            '\u3216', // PARENTHESIZED HANGUL CIEUC A
            '\u3217', // PARENTHESIZED HANGUL CHIEUCH A
            '\u3218', // PARENTHESIZED HANGUL KHIEUKH A
            '\u3219', // PARENTHESIZED HANGUL THIEUTH A
            '\u321A', // PARENTHESIZED HANGUL PHIEUPH A
            '\u321B', // PARENTHESIZED HANGUL HIEUH A
            '\u321C', // PARENTHESIZED HANGUL CIEUC U
            '\u3220', // PARENTHESIZED IDEOGRAPH ONE
            '\u3221', // PARENTHESIZED IDEOGRAPH TWO
            '\u3222', // PARENTHESIZED IDEOGRAPH THREE
            '\u3223', // PARENTHESIZED IDEOGRAPH FOUR
            '\u3224', // PARENTHESIZED IDEOGRAPH FIVE
            '\u3225', // PARENTHESIZED IDEOGRAPH SIX
            '\u3226', // PARENTHESIZED IDEOGRAPH SEVEN
            '\u3227', // PARENTHESIZED IDEOGRAPH EIGHT
            '\u3228', // PARENTHESIZED IDEOGRAPH NINE
            '\u3229', // PARENTHESIZED IDEOGRAPH TEN
            '\u322A', // PARENTHESIZED IDEOGRAPH MOON
            '\u322B', // PARENTHESIZED IDEOGRAPH FIRE
            '\u322C', // PARENTHESIZED IDEOGRAPH WATER
            '\u322D', // PARENTHESIZED IDEOGRAPH WOOD
            '\u322E', // PARENTHESIZED IDEOGRAPH METAL
            '\u322F', // PARENTHESIZED IDEOGRAPH EARTH
            '\u3230', // PARENTHESIZED IDEOGRAPH SUN
            '\u3231', // PARENTHESIZED IDEOGRAPH STOCK
            '\u3232', // PARENTHESIZED IDEOGRAPH HAVE
            '\u3233', // PARENTHESIZED IDEOGRAPH SOCIETY
            '\u3234', // PARENTHESIZED IDEOGRAPH NAME
            '\u3235', // PARENTHESIZED IDEOGRAPH SPECIAL
            '\u3236', // PARENTHESIZED IDEOGRAPH FINANCIAL
            '\u3237', // PARENTHESIZED IDEOGRAPH CONGRATULATION
            '\u3238', // PARENTHESIZED IDEOGRAPH LABOR
            '\u3239', // PARENTHESIZED IDEOGRAPH REPRESENT
            '\u323A', // PARENTHESIZED IDEOGRAPH CALL
            '\u323B', // PARENTHESIZED IDEOGRAPH STUDY
            '\u323C', // PARENTHESIZED IDEOGRAPH SUPERVISE
            '\u323D', // PARENTHESIZED IDEOGRAPH ENTERPRISE
            '\u323E', // PARENTHESIZED IDEOGRAPH RESOURCE
            '\u323F', // PARENTHESIZED IDEOGRAPH ALLIANCE
            '\u3240', // PARENTHESIZED IDEOGRAPH FESTIVAL
            '\u3241', // PARENTHESIZED IDEOGRAPH REST
            '\u3242', // PARENTHESIZED IDEOGRAPH SELF
            '\u3243', // PARENTHESIZED IDEOGRAPH REACH
            '\uFE35', // PRESENTATION FORM FOR VERTICAL LEFT PARENTHESIS
            '\uFE59', // SMALL LEFT PARENTHESIS
            '\uFF08', // FULLWIDTH LEFT PARENTHESIS
            // Characters that turn into ')'
            '\u207E', // SUPERSCRIPT RIGHT PARENTHESIS
            '\u208E', // SUBSCRIPT RIGHT PARENTHESIS
            // '\u2474', // PARENTHESIZED DIGIT ONE
            // ... see '('
            // '\u3243', // PARENTHESIZED IDEOGRAPH REACH
            '\uFE36', // PRESENTATION FORM FOR VERTICAL RIGHT PARENTHESIS
            '\uFE5A', // SMALL RIGHT PARENTHESIS
            '\uFF09', // FULLWIDTH RIGHT PARENTHESIS
            // Characters that turn into '/'
            '\u2100', // ACCOUNT OF
            '\u2101', // ADDRESSED TO THE SUBJECT
            '\u2105', // CARE OF
            '\u2106', // CADA UNA
            '\uFF0F', // FULLWIDTH SOLIDUS
            // Characters that turn into '<'
            '\u226E', // NOT LESS-THAN
            '\uFE64', // SMALL LESS-THAN SIGN
            '\uFF1C', // FULLWIDTH LESS-THAN SIGN
            // Characters that turn into '>'
            '\u226F', // NOT GREATER-THAN
            '\uFE65', // SMALL GREATER-THAN SIGN
            '\uFF1E', // FULLWIDTH GREATER-THAN SIGN
            // Characters that turn into ':'
            '\u2A74', // DOUBLE COLON EQUAL
            '\uFE55', // SMALL COLON
            '\uFF1A', // FULLWIDTH COLON
            // Characters that turn into '_'
            '\uFE33', // PRESENTATION FORM FOR VERTICAL LOW LINE
            '\uFE34', // PRESENTATION FORM FOR VERTICAL WAVY LOW LINE
            '\uFE4D', // DASHED LOW LINE
            '\uFE4E', // CENTRELINE LOW LINE
            '\uFE4F', // WAVY LOW LINE
            '\uFF3F', // FULLWIDTH LOW LINE
            // Characters that turn into '{'
            '\uFE37', // PRESENTATION FORM FOR VERTICAL LEFT CURLY BRACKET
            '\uFE5B', // SMALL LEFT CURLY BRACKET
            '\uFF5B', // FULLWIDTH LEFT CURLY BRACKET
            // Characters that turn into '}'
            '\uFE38', // PRESENTATION FORM FOR VERTICAL RIGHT CURLY BRACKET
            '\uFE5C', // SMALL RIGHT CURLY BRACKET
            '\uFF5D', // FULLWIDTH RIGHT CURLY BRACKET
            // Characters that turn into '#'
            '\uFE5F', // SMALL NUMBER SIGN
            '\uFF03', // FULLWIDTH NUMBER SIGN
            // Characters that turn into '\'
            '\uFE68', // SMALL REVERSE SOLIDUS
            '\uFF3C', // FULLWIDTH REVERSE SOLIDUS
            // Characters that turn into '['
            '\uFF3B', // FULLWIDTH LEFT SQUARE BRACKET
            // Characters that turn into ']'
            '\uFF3D', // FULLWIDTH RIGHT SQUARE BRACKET
            // Characters that turn into '^'
            '\uFF3E', // FULLWIDTH CIRCUMFLEX ACCENT
            // Characters that turn into '|'
            '\uFF5C', // FULLWIDTH VERTICAL LINE
    };
    /**
     * Setting for the characters that are not allowed in search strings
     */
    public static final CharArraySetting ILLEGAL_CHARS =
            FACTORY.createCharArraySetting("ILLEGAL_CHARS", BAD_CHARS);
    private static final int MOVE_JUNK_TO_BOTTOM = 1;
    private static final int HIDE_JUNK = 2;
    /**
     * The display mode for junk search results
     */
    private static final IntSetting DISPLAY_JUNK_MODE =
            FACTORY.createIntSetting("DISPLAY_JUNK_MODE", HIDE_JUNK);

    private SearchSettings() {
    }

    public static boolean moveJunkToBottom() {
        return ENABLE_SPAM_FILTER.getValue() && DISPLAY_JUNK_MODE.getValue() == MOVE_JUNK_TO_BOTTOM;
    }

    public static boolean hideJunk() {
        return ENABLE_SPAM_FILTER.getValue() && DISPLAY_JUNK_MODE.getValue() == HIDE_JUNK;
    }
}
