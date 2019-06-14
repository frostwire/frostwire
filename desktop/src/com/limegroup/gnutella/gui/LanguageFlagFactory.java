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

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Simple factory for retrieving a flag for a given country and/or language.
 */
public class LanguageFlagFactory {
    private final static Map<String, String> lc2cc;

    static {
        //found using google, cia worldbook of facts and wikipedia.
        lc2cc = new HashMap<>();
        lc2cc.put("en", "US");
        lc2cc.put("af", "ZA"); //Aafrican, South Africa
        lc2cc.put("am", "ET"); //Amharic, Ethiopia
        lc2cc.put("ar", "EG"); //arabic, Egypt
        lc2cc.put("be", "BO"); //belarusian, Belarus
        lc2cc.put("bg", "BG"); //Bulgarian, Bulgaria
        lc2cc.put("bn", "BD"); //Bengali, Bangladesh
        lc2cc.put("bo", "CN"); //Tibetan, China
        lc2cc.put("br", "FR"); //Brezhoneg, spoken in france
        lc2cc.put("bs", "BA"); //Bosanski, Bosnia
        lc2cc.put("bu", "MM"); //Burmese, Myanmar
        lc2cc.put("ca", "CAT"); //Catala, Cataluña
        lc2cc.put("cs", "CZ"); //Cestina, Czech Republic
        lc2cc.put("cy", "WE"); //Welsh (Cymraeg) part of GB, part of UK 
        //Wales has a dragon on it we need more flags
        lc2cc.put("da", "DK"); //Dansk, Denmark
        lc2cc.put("de", "DE"); //Deutsch, Germany
        lc2cc.put("dv", "MV"); //Divehi, Maldive
        lc2cc.put("el", "GR"); //Greek, Grece
        lc2cc.put("es", "ES"); //Spanish, Spain
        lc2cc.put("et", "EE"); //Estonian, ET
        lc2cc.put("eu", "ES"); //Euskara, Spain
        lc2cc.put("fa", "IR"); //Persian, Iran
        lc2cc.put("fi", "FI"); //Finnish (Suomi), Finland
        lc2cc.put("fr", "FR"); //French, France
        lc2cc.put("ga", "IE"); //Irish, Ireland
        lc2cc.put("gez", "ET");//Geez, Ethiopia
        lc2cc.put("gu", "IN"); //Gujarati, India
        lc2cc.put("gl", "GL"); //Gallego, Spain
        lc2cc.put("hi", "IN"); //Hindi, India
        lc2cc.put("hr", "HR"); //Hrvatski, Croatia
        lc2cc.put("hu", "HU"); //Magyar, Hungary
        lc2cc.put("hy", "AM"); //Armenian, Armenia
        lc2cc.put("in", "ID"); //Bahasa, Indonesia
        lc2cc.put("is", "IS"); //Islenska, Iceland
        lc2cc.put("it", "IT"); //Italian, Italy
        lc2cc.put("iw", "IL"); //Hebrew, Israel
        lc2cc.put("ja", "JP"); //Japanese, Japan
        lc2cc.put("ka", "GG"); //Georgian, Georgia (added this flag .gif)
        lc2cc.put("kk", "KZ"); //Kazakh, Kazakhstan
        lc2cc.put("kn", "IN"); //Kannada, India
        lc2cc.put("ks", "IN"); //Kashmiri, India
        lc2cc.put("ko", "KR"); //Korean, South Korea
        lc2cc.put("km", "KH"); //Khmer, Cambodia
        lc2cc.put("lb", "LU"); //Luxemburgish, Luxembourg
        lc2cc.put("lo", "LA"); //Lao, Laos
        lc2cc.put("lt", "LT"); //Lietuva, Lituania
        lc2cc.put("lv", "LV"); //Latvija, Latvia
        lc2cc.put("mg", "MG"); //Malagasy, Madagascar
        lc2cc.put("mk", "MK"); //Macedonian, Macedonia
        lc2cc.put("ml", "IN"); //Malayan, India
        lc2cc.put("mn", "MN"); //Mongol, Mongolia
        lc2cc.put("mr", "IN"); //Marathi, India
        lc2cc.put("ms", "MY"); //Bahasa Melayu, Malaysia
        lc2cc.put("mt", "MT"); //Malti, Malta
        lc2cc.put("ne", "NP"); //Nepali, Nepal
        lc2cc.put("nl", "NL"); //Nederlands
        lc2cc.put("nn", "NO"); //Norsk, Norway
        lc2cc.put("no", "NO"); //Norsk Bokmal, Norway //FTA: Currently unused
        lc2cc.put("nb", "NO"); //Norsk Bokmal, Norway (Currently used by FrostWire)
        lc2cc.put("or", "IN"); //Oriya, India
        lc2cc.put("pa", "IN"); //Punjabi, India
        lc2cc.put("pl", "PL"); //Polish, Poland
        lc2cc.put("pt", "PT"); //Portuguese, Portugal
        lc2cc.put("pu", "IN"); //Punjabi, India
        lc2cc.put("ro", "RO"); //Romanian, Romania
        lc2cc.put("ru", "RU"); //Russian, Russia
        //lc2cc.put("sh","YU"); //Serbian, Serbia
        lc2cc.put("si", "IN"); //Singhalese, India
        lc2cc.put("sk", "SK"); //Slovak, Slovakia
        lc2cc.put("sl", "SI"); //Slovenian, Slovenia
        lc2cc.put("sq", "AL"); //Shqipe Albanian, Albania
        lc2cc.put("sr", "YU"); //Serbian, Serbia //FTA: Currently used by FrostWire
        lc2cc.put("LATN", "YU"); //Serbian, Serbia //FTA: Added
        lc2cc.put("sv", "SE"); //Svenska, Sweden
        lc2cc.put("ta", "TA"); //Tamil, Tamil
        lc2cc.put("te", "IN"); //Telugi, India
        lc2cc.put("tig", "ET");//Tigringa, Ethiopia
        lc2cc.put("tg", "TJ"); //Tajik, Tajikistan
        lc2cc.put("th", "TH"); //Thailand
        lc2cc.put("tl", "PH"); //Tagalog, Philippines
        lc2cc.put("tr", "TR"); //Turkish, Turkey
        lc2cc.put("uk", "UA"); //Ukranian, Ukraine
        lc2cc.put("ur", "PK"); //Urdu, Pakistan
        lc2cc.put("uz", "UZ"); //Uzbekistan
        lc2cc.put("vi", "VN"); //Vietnamese, Vietnam
        lc2cc.put("zh", "CN"); //Chinese, China
        //The country codes on this map correspond to the names
        //of the flag .gifs on gui/images/flags
    }

    /**
     * Returns a flag representing the given country, or if the country
     * doesn't have a flag, the most prominent country that the language
     * is spoken in.
     * <p>
     * If no flag exists, defaults to a large (24x24) globe icon.
     *
     * @param countryCode
     * @param languageCode
     * @return
     */
    private static ImageIcon getFlag(String countryCode, String languageCode) {
        return getFlag(countryCode, languageCode, false);
    }

    /**
     * Returns a flag representing the given country, or if the country
     * doesn't have a flag, the most prominent country that the language
     * is spoken in.
     * <p>
     * If no flag exists and little is true, this will return a 16x16 globe icon.
     * If no flag exists and little is false, this will return a 24x24 globe icon.
     *
     * @param countryCode
     * @param languageCode
     * @return
     */
    public static ImageIcon getFlag(String countryCode, String languageCode, boolean little) {
        ImageIcon flag = null;
        if (countryCode != null && countryCode.length() > 0) {
            try {
                flag = GUIMediator.getThemeImage("flags/" + countryCode.toUpperCase());
            } catch (MissingResourceException mse) {
            }
        }
        if (flag == null) {
            //no country code available -> use language code to country code map
            String cc = lc2cc.get(languageCode);
            if (cc != null) {
                try {
                    flag = GUIMediator.getThemeImage("flags/" + cc);
                } catch (MissingResourceException mse) {
                }
            }
        }
        // fallback to a globe.
        if (flag == null)
            flag = GUIMediator.getThemeImage("globe" + (little ? "_small" : "_large"));
        return flag;
    }

    /**
     * Returns a ListCellRenderer that can be used for showing flags in a JList or JComboBox.
     * The Renderer expects the object in the list to be a LanguageInfo object.
     */
    public static ListCellRenderer<Object> getListRenderer() {
        return new LocaleRenderer();
    }

    private static class LocaleRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
            if (value instanceof Locale) {
                Locale locale = (Locale) value;
                setIcon(LanguageFlagFactory.getFlag(locale.getCountry(), locale.getLanguage()));
                setText(locale.getDisplayName(locale).substring(0, 1).toUpperCase() + locale.getDisplayName(locale).substring(1).toLowerCase()); // FTA: Languages list looks better. Each language starts with uppercase. In the past we had a mix of lowercase and uppercase letters.
                //FTA DEBUG System.out.println("Idioma: " + locale.getDisplayName(locale) + " Country: " + locale.getCountry() + "Language code: " + locale.getLanguage());
            } else {
                setIcon(null);
            }
            setIconTextGap(10);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            return this;
        }
    }
}
