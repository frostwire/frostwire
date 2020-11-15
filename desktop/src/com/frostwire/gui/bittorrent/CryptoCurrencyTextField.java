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

package com.frostwire.gui.bittorrent;

import com.limegroup.gnutella.gui.LimeTextField;

import java.util.HashMap;
import java.util.Set;

class CryptoCurrencyTextField extends LimeTextField {
    private final String prefix;
    private final HashMap<String, String> firstValidCharsOnAddress;

    CryptoCurrencyTextField(CurrencyURIPrefix p) {
        prefix = p.toString();
        firstValidCharsOnAddress = new HashMap<>();
        initFirstValidChars();
    }

    private void initFirstValidChars() {
        switch (prefix) {
            case "bitcoin:":
                firstValidCharsOnAddress.put("1", "1");
                firstValidCharsOnAddress.put("3", "3");
                break;
            case "litecoin:":
                firstValidCharsOnAddress.put("L", "L");
                break;
            case "dogecoin:":
                firstValidCharsOnAddress.put("D", "D");
                break;
        }
    }

    boolean hasValidPrefixOrNoPrefix() {
        boolean hasPrefix;
        boolean hasValidPrefix = false;
        String text = getText();
        if (text.contains(":")) {
            hasPrefix = true;
            hasValidPrefix = text.startsWith(prefix);
        } else {
            hasPrefix = false;
        }
        return !hasPrefix || hasValidPrefix;
    }

    boolean hasValidAddress() {
        boolean result = false;
        String text = getText().trim();
        if (text != null && !text.isEmpty()) {
            text = text.replaceAll(prefix, "");
            result = (26 <= text.length() && text.length() <= 34) && isFirstCharValid();
        }
        return result;
    }

    //To be invoked only after hasValidAddress() has returned true.
    String normalizeValidAddress() {
        String result = getText().trim();
        if (!result.startsWith(prefix)) {
            result = prefix + result;
        }
        return result;
    }

    private boolean isFirstCharValid() {
        boolean foundChar = false;
        String text = getText().trim();
        if (text != null && !text.isEmpty()) {
            text = text.replaceAll(prefix, "");
            if (text.length() > 1) {
                String firstChar = text.substring(0, 1);
                Set<String> firstChars = firstValidCharsOnAddress.keySet();
                for (String key : firstChars) {
                    if (key.equals(firstChar)) {
                        foundChar = true;
                        break;
                    }
                }
            }
        }
        return foundChar;
    }

    public enum CurrencyURIPrefix {
        BITCOIN("bitcoin:");
        private final String prefix;

        CurrencyURIPrefix(String p) {
            prefix = p;
        }

        public String toString() {
            return prefix;
        }
    }
}