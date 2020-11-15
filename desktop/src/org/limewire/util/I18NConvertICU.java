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

package org.limewire.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Removes accents and symbols, and normalizes strings.
 */
final class I18NConvertICU extends AbstractI18NConverter {
    /**
     * excluded codepoints (like accents)
     */
    private BitSet _excluded;
    private Map<?, ?> _cMap;

    /**
     * initializer:
     * this subclass of AbstractI18NConverter uses the java classes
     * to normalize Strings.
     * _excluded and _replaceWithSpace (BitSet) are read in from
     * files created by UDataFileCreator and are used to
     * remove accents, etc. and replace certain code points with
     * ascii space (\u0020)
     */
    I18NConvertICU()
            throws IOException, ClassNotFoundException {
        BitSet bs = null;
        Map<?, ?> hm = null;
        InputStream fi = CommonUtils.getResourceStream("com/frostwire/util/excluded.dat");
        //read in the explusion bitset
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(fi));
        bs = (java.util.BitSet) ois.readObject();
        ois.close();
        fi = CommonUtils.getResourceStream("com/frostwire/util/caseMap.dat");
        //read in the case map
        ois = new ConverterObjectInputStream(new BufferedInputStream(fi));
        hm = (HashMap<?, ?>) ois.readObject();
        ois.close();
        _excluded = bs;
        _cMap = hm;
    }

    /**
     * Return the converted form of the string s
     * this method will also split the s into the different
     * unicode blocks
     *
     * @param s String to be converted
     * @return the converted string
     */
    public String getNorm(String s) {
        return convert(s);
    }

    /**
     * Simple composition of a String.
     */
    public String compose(String s) {
        return Normalizer.normalize(s, Form.NFC);
    }

    /**
     * convert the string into NFKC + removal of accents, symbols, etc.
     * uses icu4j's Normalizer to first decompose to NFKD form,
     * then removes all codepoints in the exclusion BitSet
     * finally composes to NFC and adds spaces '\u0020' between
     * different unicode blocks
     *
     * @return converted String
     */
    private String convert(String s) {
        //decompose to NFKD
        String nfkd = Normalizer.normalize(s, Form.NFKD);
        StringBuilder buf = new StringBuilder();
        int len = nfkd.length();
        String lower;
        char c;
        //loop through the string and check for excluded chars
        //and lower case if necessary
        for (int i = 0; i < len; i++) {
            c = nfkd.charAt(i);
            if (!_excluded.get(c)) {
                lower = (String) _cMap.get(String.valueOf(c));
                if (lower != null)
                    buf.append(lower);
                else
                    buf.append(c);
            }
        }
        //compose to nfc and split
        return blockSplit(Normalizer.normalize(buf.toString(), Form.NFC));
    }
}





