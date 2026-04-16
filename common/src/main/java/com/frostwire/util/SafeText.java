/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.util;

import java.text.Normalizer;

public final class SafeText {

    private SafeText() {}

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        int len = normalized.length();
        StringBuilder sb = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            char c1 = normalized.charAt(i);
            if (Character.isHighSurrogate(c1) && i + 1 < len) {
                char c2 = normalized.charAt(i + 1);
                if (Character.isLowSurrogate(c2)) {
                    int cp = Character.toCodePoint(c1, c2);
                    if (isSafeSupplementary(cp)) {
                        sb.append(c1);
                        sb.append(c2);
                    }
                    i += 2;
                    continue;
                }
            }
            if (isSafeBMP(c1)) {
                sb.append(c1);
            }
            i++;
        }
        return sb.toString();
    }

    private static boolean isSafeBMP(char c) {
        if (c < ' ' && c != '\n' && c != '\t' && c != '\r') {
            return false;
        }
        if (Character.getType(c) == Character.FORMAT) {
            return false;
        }
        if (c == '\uFEFF' || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060' || c == '\u180E') {
            return false;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS ||
            block == Character.UnicodeBlock.DINGBATS ||
            block == Character.UnicodeBlock.VARIATION_SELECTORS ||
            block == Character.UnicodeBlock.VARIATION_SELECTORS_SUPPLEMENT ||
            block == Character.UnicodeBlock.TAGS) {
            return false;
        }
        if (c >= '\u2600' && c <= '\u27BF') {
            return false;
        }
        return true;
    }

    private static boolean isSafeSupplementary(int cp) {
        if (Character.getType(cp) == Character.FORMAT) {
            return false;
        }
        if (cp >= 0x1F600 && cp <= 0x1F64F) return false;
        if (cp >= 0x1F300 && cp <= 0x1F5FF) return false;
        if (cp >= 0x1F680 && cp <= 0x1F6FF) return false;
        if (cp >= 0x1F1E0 && cp <= 0x1F1FF) return false;
        if (cp >= 0x1F900 && cp <= 0x1F9FF) return false;
        if (cp >= 0x1FA00 && cp <= 0x1FA6F) return false;
        if (cp >= 0x1FA70 && cp <= 0x1FAFF) return false;
        if (cp >= 0xE0100 && cp <= 0xE01EF) return false;
        if (cp >= 0xE0000 && cp <= 0xE007F) return false;
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        if (block == Character.UnicodeBlock.EMOTICONS ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS ||
            block == Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS ||
            block == Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS ||
            block == Character.UnicodeBlock.VARIATION_SELECTORS_SUPPLEMENT ||
            block == Character.UnicodeBlock.TAGS ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS ||
            block == Character.UnicodeBlock.MAHJONG_TILES ||
            block == Character.UnicodeBlock.DOMINO_TILES ||
            block == Character.UnicodeBlock.PLAYING_CARDS ||
            block == Character.UnicodeBlock.ORNAMENTAL_DINGBATS ||
            block == Character.UnicodeBlock.CHESS_SYMBOLS ||
            block == Character.UnicodeBlock.SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A ||
            block == Character.UnicodeBlock.SYMBOLS_FOR_LEGACY_COMPUTING ||
            block == Character.UnicodeBlock.ALCHEMICAL_SYMBOLS) {
            return false;
        }
        if (block != null) {
            String blockName = block.toString();
            if (blockName.contains("SYMBOL") || blockName.contains("PICTOGRAPH") ||
                blockName.contains("EMOTICON") || blockName.contains("DINGBAT") ||
                blockName.contains("MAHJONG") || blockName.contains("DOMINO") ||
                blockName.contains("CHESS") || blockName.contains("CARD") ||
                blockName.contains("GAME") || blockName.contains("TAG")) {
                return false;
            }
        }
        return true;
    }
}
