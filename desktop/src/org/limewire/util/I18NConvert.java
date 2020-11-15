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

import java.io.IOException;

/**
 * Removes accents, symbols and normalizes strings for internationalization.
 * <code>I18NConvert</code>'s current implementation delegates the
 * internationalization conversion through the ICU4J library.
 * <p>
 * For example, &#195 is normalized to a, &#153 is normalized to tm and LiMEWirE is
 * normalized to limewire. See
 * <a href = "http://en.wikipedia.org/wiki/International_Components_for_Unicode">
 * Unicode</a> for more information about international components.
 */
public class I18NConvert {
    /**
     * instance
     */
    private final static I18NConvert _instance = new I18NConvert();
    /**
     * the class that handles the conversion
     */
    private final AbstractI18NConverter _convertDelegator;

    /**
     * Empty constructor so nothing else can instantiate it.
     */
    private I18NConvert() {
        try {
            //instantiates an implementation 
            //of abstract class AbstractI18NConverter
            _convertDelegator = new I18NConvertICU();
            _convertDelegator.getNorm("touch ICU code");
        } catch (IOException | ClassNotFoundException te) {
            throw new ExceptionInInitializerError(te);
        }
    }

    /**
     * accessor
     */
    public static I18NConvert instance() {
        return _instance;
    }

    /**
     * delegate to AbstractI18NConverter instance
     */
    public String getNorm(String s) {
        return _convertDelegator.getNorm(s);
    }

    /**
     * Simple composition.
     */
    public String compose(String s) {
        return _convertDelegator.compose(s);
    }
}



