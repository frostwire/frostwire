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

package com.limegroup.gnutella.gui.init;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LanguageFlagFactory;
import com.limegroup.gnutella.gui.LanguageUtils;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.apache.commons.io.IOUtils;
import org.limewire.util.OSUtils;
import org.limewire.util.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class LanguagePanel extends JPanel {
    private final JLabel languageLabel;
    private final ActionListener actionListener;
    private final JComboBox<Object> languageOptions;

    /**
     * Constructs a LanguagePanel that will notify the given listener when the
     * language changes.
     */
    LanguagePanel(ActionListener actionListener) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.actionListener = actionListener;
        this.languageLabel = new JLabel();
        languageOptions = new JComboBox<>();
        Font font = new Font("Dialog", Font.PLAIN, 11);
        languageOptions.setFont(font);
        Locale[] locales = LanguageUtils.getLocales(font);
        languageOptions.setModel(new DefaultComboBoxModel<>(locales));
        languageOptions.setRenderer(LanguageFlagFactory.getListRenderer());
        Locale locale = guessLocale(locales);
        languageOptions.setSelectedItem(locale);
        applySettings();
        // It is important that the listener is added after the selected item
        // is set. Otherwise the listener will call methods that are not ready
        // to be called at this point.
        languageOptions.addItemListener(new StateListener());
        add(languageLabel);
        add(Box.createHorizontalStrut(5));
        add(languageOptions);
    }

    /**
     * Overrides applySettings in SetupWindow superclass.
     * Applies the settings handled in this window.
     */
    private void applySettings() {
        Locale locale = (Locale) languageOptions.getSelectedItem();
        LanguageUtils.setLocale(locale);
        languageLabel.setText(I18n.tr("Language:"));
    }

    private Locale guessLocale(Locale[] locales) {
        String[] language = guessLanguage();
        Locale result = null;
        try {
            for (Locale l : locales) {
                if (l.getLanguage().equalsIgnoreCase(language[0]) && l.getCountry().equalsIgnoreCase(language[1]) && l.getVariant().equalsIgnoreCase(language[2])) {
                    result = l;
                }
                if (l.getLanguage().equalsIgnoreCase(language[0]) && l.getCountry().equalsIgnoreCase(language[1]) && result == null) {
                    result = l;
                }
                if (l.getLanguage().equalsIgnoreCase(language[0]) && result == null) {
                    result = l;
                }
            }
        } catch (Throwable e) {
            //shhh!
        }
        return (result == null) ? new Locale(language[0], language[1], language[2]) : result;
    }

    private String[] guessLanguage() {
        String ln = ApplicationSettings.LANGUAGE.getValue();
        String cn = ApplicationSettings.COUNTRY.getValue();
        String vn = ApplicationSettings.LOCALE_VARIANT.getValue();
        String[] registryLocale;
        if (OSUtils.isWindows() && (registryLocale = getDisplayLanguageFromWindowsRegistry()) != null) {
            return registryLocale;
        }
        File file = new File("language.prop");
        if (!file.exists())
            return new String[]{ln, cn, vn};
        InputStream in = null;
        BufferedReader reader = null;
        String code = "";
        try {
            in = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(in));
            code = reader.readLine();
        } catch (IOException ignored) {
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(reader);
        }
        String[] mapped = getLCID(code);
        if (mapped != null)
            return mapped;
        else
            return new String[]{ln, cn, vn};
    }

    /**
     * Returns the String[] { languageCode, countryCode, variantCode }
     * for the Windows LCID.
     */
    private String[] getLCID(String code) {
        Map<String, String[]> map = new HashMap<>();
        map.put("1078", new String[]{"af", "", ""});
        map.put("1052", new String[]{"sq", "", ""});
        map.put("5121", new String[]{"ar", "", ""});
        map.put("15361", new String[]{"ar", "", ""});
        map.put("3073", new String[]{"ar", "", ""});
        map.put("2049", new String[]{"ar", "", ""});
        map.put("11265", new String[]{"ar", "", ""});
        map.put("13313", new String[]{"ar", "", ""});
        map.put("12289", new String[]{"ar", "", ""});
        map.put("4097", new String[]{"ar", "", ""});
        map.put("6145", new String[]{"ar", "", ""});
        map.put("8193", new String[]{"ar", "", ""});
        map.put("16385", new String[]{"ar", "", ""});
        map.put("1025", new String[]{"ar", "", ""});
        map.put("10241", new String[]{"ar", "", ""});
        map.put("7169", new String[]{"ar", "", ""});
        map.put("14337", new String[]{"ar", "", ""});
        map.put("9217", new String[]{"ar", "", ""});
        map.put("1069", new String[]{"eu", "", ""});
        map.put("1059", new String[]{"be", "", ""});
        map.put("1093", new String[]{"bn", "", ""});
        map.put("1027", new String[]{"ca", "", ""});
        map.put("3076", new String[]{"zh", "", ""});
        map.put("5124", new String[]{"zh", "", ""});
        map.put("2052", new String[]{"zh", "", ""});
        map.put("4100", new String[]{"zh", "", ""});
        map.put("1028", new String[]{"zh", "TW", ""});
        map.put("1050", new String[]{"hr", "", ""});
        map.put("1029", new String[]{"cs", "", ""});
        map.put("1030", new String[]{"da", "", ""});
        map.put("2067", new String[]{"nl", "", ""});
        map.put("1043", new String[]{"nl", "", ""});
        map.put("3081", new String[]{"en", "", ""});
        map.put("10249", new String[]{"en", "", ""});
        map.put("4105", new String[]{"en", "", ""});
        map.put("9225", new String[]{"en", "", ""});
        map.put("6153", new String[]{"en", "", ""});
        map.put("8201", new String[]{"en", "", ""});
        map.put("5129", new String[]{"en", "", ""});
        map.put("13321", new String[]{"en", "", ""});
        map.put("7177", new String[]{"en", "", ""});
        map.put("11273", new String[]{"en", "", ""});
        map.put("2057", new String[]{"en", "", ""});
        map.put("1033", new String[]{"en", "", ""});
        map.put("12297", new String[]{"en", "", ""});
        map.put("1061", new String[]{"et", "", ""});
        map.put("1035", new String[]{"fi", "", ""});
        map.put("2060", new String[]{"fr", "", ""});
        map.put("11276", new String[]{"fr", "", ""});
        map.put("3084", new String[]{"fr", "", ""});
        map.put("9228", new String[]{"fr", "", ""});
        map.put("12300", new String[]{"fr", "", ""});
        map.put("1036", new String[]{"fr", "", ""});
        map.put("5132", new String[]{"fr", "", ""});
        map.put("13324", new String[]{"fr", "", ""});
        map.put("6156", new String[]{"fr", "", ""});
        map.put("10252", new String[]{"fr", "", ""});
        map.put("4108", new String[]{"fr", "", ""});
        map.put("7180", new String[]{"fr", "", ""});
        map.put("3079", new String[]{"de", "", ""});
        map.put("1031", new String[]{"de", "", ""});
        map.put("5127", new String[]{"de", "", ""});
        map.put("4103", new String[]{"de", "", ""});
        map.put("2055", new String[]{"de", "", ""});
        map.put("1032", new String[]{"el", "", ""});
        map.put("1037", new String[]{"iw", "", ""});
        map.put("1081", new String[]{"hi", "", ""});
        map.put("1038", new String[]{"hu", "", ""});
        map.put("1039", new String[]{"is", "", ""});
        map.put("1057", new String[]{"id", "", ""});
        map.put("1040", new String[]{"it", "", ""});
        map.put("2064", new String[]{"it", "", ""});
        map.put("1041", new String[]{"ja", "", ""});
        map.put("1042", new String[]{"ko", "", ""});
        map.put("1062", new String[]{"lv", "", ""});
        map.put("2110", new String[]{"ms", "", ""});
        map.put("1086", new String[]{"ms", "", ""});
        map.put("1082", new String[]{"mt", "", ""});
        map.put("1044", new String[]{"no", "", ""});
        map.put("2068", new String[]{"nn", "", ""});
        map.put("1045", new String[]{"pl", "", ""});
        map.put("1046", new String[]{"pt", "BR", ""}); // FTA: Portu Brazil
        map.put("2070", new String[]{"pt", "", ""});
        map.put("1048", new String[]{"ro", "", ""});
        map.put("2072", new String[]{"ro", "", ""});
        map.put("1049", new String[]{"ru", "", ""});
        map.put("2073", new String[]{"ru", "", ""});
        map.put("3098", new String[]{"sr", "", ""});
        map.put("2074", new String[]{"sr", "", ""});
        map.put("1051", new String[]{"sk", "", ""});
        map.put("1060", new String[]{"sl", "", ""});
        map.put("11274", new String[]{"es", "", ""});
        map.put("16394", new String[]{"es", "", ""});
        map.put("13322", new String[]{"es", "", ""});
        map.put("9226", new String[]{"es", "", ""});
        map.put("5130", new String[]{"es", "", ""});
        map.put("7178", new String[]{"es", "", ""});
        map.put("12298", new String[]{"es", "", ""});
        map.put("17418", new String[]{"es", "", ""});
        map.put("4106", new String[]{"es", "", ""});
        map.put("18442", new String[]{"es", "", ""});
        map.put("3082", new String[]{"es", "", ""});
        map.put("2058", new String[]{"es", "", ""});
        map.put("19466", new String[]{"es", "", ""});
        map.put("6154", new String[]{"es", "", ""});
        map.put("15370", new String[]{"es", "", ""});
        map.put("10250", new String[]{"es", "", ""});
        map.put("20490", new String[]{"es", "", ""});
        map.put("1034", new String[]{"es", "", ""});
        map.put("14346", new String[]{"es", "", ""});
        map.put("8202", new String[]{"es", "", ""});
        map.put("1053", new String[]{"sv", "", ""});
        map.put("2077", new String[]{"sv", "", ""});
        map.put("1097", new String[]{"ta", "", ""});
        map.put("1054", new String[]{"th", "", ""});
        map.put("1055", new String[]{"tr", "", ""});
        map.put("1058", new String[]{"uk", "", ""});
        map.put("1056", new String[]{"ur", "", ""});
        map.put("2115", new String[]{"uz", "", ""});
        map.put("1091", new String[]{"uz", "", ""});
        map.put("1066", new String[]{"vi", "", ""});
        return map.get(code);
    }

    private String[] getDisplayLanguageFromWindowsRegistry() {
        //HKCU/Control Panel/Desktop/PreferredUILanguages
        String[] result = null;
        try {
            String registryReadText = SystemUtils.registryReadText("HKEY_CURRENT_USER", "Control Panel\\Desktop", "PreferredUILanguages");
            String[] splitLocale = registryReadText.split("-");
            switch (splitLocale.length) {
                case 1:
                    result = new String[]{splitLocale[0], "", ""};
                    break;
                case 2:
                    result = new String[]{splitLocale[0], splitLocale[1], ""};
                    break;
                case 3:
                    result = new String[]{splitLocale[0], splitLocale[1], splitLocale[2]};
                    break;
                default:
                    result = null;
                    break;
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private class StateListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                applySettings();
                actionListener.actionPerformed(null);
                languageOptions.requestFocus();
            }
        }
    }
}
