/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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