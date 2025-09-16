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



