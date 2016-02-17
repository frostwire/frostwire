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

package com.limegroup.gnutella.gui;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import com.frostwire.logging.Logger;
import com.limegroup.gnutella.settings.ApplicationSettings;

/**
 * This class provides utility methods retrieving supported languages and
 * changing language settings.
 */
public class LanguageUtils {

    private static Logger LOG = Logger.getLogger(LanguageUtils.class);

    private static final String BUNDLE_PREFIX = "org/limewire/i18n/Messages_";

    private static final String BUNDLE_POSTFIX = ".class";

    private static final String BUNDLE_MARKER = "org/limewire/i18n/Messages.class";

    /**
     * Applies this language code to be the new language of the program.
     */
    public static void setLocale(Locale locale) {
        ApplicationSettings.LANGUAGE.setValue(locale.getLanguage());
        ApplicationSettings.COUNTRY.setValue(locale.getCountry());
        ApplicationSettings.LOCALE_VARIANT.setValue(locale.getVariant());

        GUIMediator.resetLocale();
    }

    /**
     * Returns an array of supported language as a LanguageInfo[], always having
     * the English language as the first element.
     * 
     * This will only include languages that can be displayed using the given
     * font. If the font is null, all languages are returned.
     */
    public static Locale[] getLocales(Font font) {
        final List<Locale> locales = new LinkedList<Locale>();
        
        File jar = FileUtils.getJarFromClasspath(LanguageUtils.class.getClassLoader(), BUNDLE_MARKER);
        if (jar != null) {
            addLocalesFromJar(locales, jar);
        } else {
            LOG.warn("Could not find bundle jar to determine locales");
        }
        
        Collections.sort(locales, new Comparator<Locale>() {
            public int compare(Locale o1, Locale o2) {
                return o1.getDisplayName(o1).compareToIgnoreCase(
                        o2.getDisplayName(o2));
            }
        });
        
        locales.remove(Locale.ENGLISH);
        locales.add(0, Locale.ENGLISH);

        // remove languages that cannot be displayed using this font
        if (font != null && !OSUtils.isMacOSX()) {
            for (Iterator<Locale> it = locales.iterator(); it.hasNext();) {
                Locale locale = it.next();
                if (!GUIUtils.canDisplay(font, locale.getDisplayName(locale))) {
                    it.remove();
                }
            }
        }

        return locales.toArray(new Locale[0]);
    }

    /**
     * Returns the languages as found from the classpath in messages.jar
     */
    static void addLocalesFromJar(List<Locale> locales, File jar) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(jar);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(BUNDLE_PREFIX) || !name.endsWith(BUNDLE_POSTFIX)
                        || name.indexOf("$") != -1) {
                    continue;
                }

                String iso = name.substring(BUNDLE_PREFIX.length(), name.length()
                        - BUNDLE_POSTFIX.length());
                List<String> tokens = new ArrayList<String>(Arrays.asList(iso.split("_", 3)));
                if (tokens.size() < 1) {
                    continue;
                }
                while (tokens.size() < 3) {
                    tokens.add("");
                }

                Locale locale = new Locale(tokens.get(0), tokens.get(1), tokens.get(2));
                locales.add(locale);
            }
        } catch (IOException e) {
            LOG.warn("Could not determine locales", e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ioe) {
                }
            }
        }
    }

    /**
     * Returns true if the language of <code>locale</code> is English.
     */
    public static boolean isEnglishLocale(Locale locale) {
        return Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
    }

    /**
     * Returns a score between -1 and 3 how well <code>specificLocale</code>
     * matches <code>genericLocale</code>.
     * 
     * @return -1, if locales do not match, 3 if locales are equal
     */
    public static int getMatchScore(Locale specificLocale, Locale genericLocale) {
        int i = 0;
        if (specificLocale.getLanguage().equals(genericLocale.getLanguage())) {
            i += 1;
        } else if (genericLocale.getLanguage().length() > 0) {
            return -1;
        }
        if (specificLocale.getCountry().equals(genericLocale.getCountry())) {
            i += 1;
        } else if (genericLocale.getCountry().length() > 0) {
            return -1;
        }
        if (specificLocale.getVariant().equals(genericLocale.getVariant())) {
            i += 1;
        } else if (genericLocale.getVariant().length() > 0) {
            return -1;
        }
        
        return i;
    }

    /**
     * Returns true, if <code>locale</code> is less specific than the system
     * default locale.
     * 
     * @see Locale#getDefault()
     */
    public static boolean matchesDefaultLocale(Locale locale) {
        Locale systemLocale = Locale.getDefault();
        if (matchesOrIsMoreSpecific(systemLocale.getLanguage(), locale.getLanguage())
                && matchesOrIsMoreSpecific(systemLocale.getCountry(), locale.getCountry())
                && matchesOrIsMoreSpecific(systemLocale.getVariant(), locale.getVariant())) {
            return true;
        }
        return false;
    }
    
    private static boolean matchesOrIsMoreSpecific(String detailed, String generic) {
        return generic.length() == 0 || detailed.equals(generic);
    }
}
