/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.LanguageUtils;
import com.limegroup.gnutella.settings.ApplicationSettings;
import java.awt.Component;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The wizard's language picker: which language it starts on and what picking one changes. */
class LanguagePanelTest {
  private String language;
  private String country;
  private String variant;

  @BeforeEach
  void save() {
    language = ApplicationSettings.LANGUAGE.getValue();
    country = ApplicationSettings.COUNTRY.getValue();
    variant = ApplicationSettings.LOCALE_VARIANT.getValue();
  }

  @AfterEach
  void restore() throws Exception {
    SwingUtilities.invokeAndWait(
        () ->
            LanguageUtils.setLocale(
                new Locale.Builder()
                    .setLanguage(language)
                    .setRegion(country)
                    .setVariant(variant)
                    .build()));
  }

  private static void useLanguage(String language, String country, String variant) {
    ApplicationSettings.LANGUAGE.setValue(language);
    ApplicationSettings.COUNTRY.setValue(country);
    ApplicationSettings.LOCALE_VARIANT.setValue(variant);
  }

  @SuppressWarnings("unchecked")
  private static JComboBox<Object> picker(LanguagePanel panel) {
    for (Component c : panel.getComponents()) {
      if (c instanceof JComboBox<?> box) {
        return (JComboBox<Object>) box;
      }
    }
    throw new AssertionError("no language picker");
  }

  private static Locale startsOn(String language, String country, String variant) throws Exception {
    useLanguage(language, country, variant);
    Locale[] selected = new Locale[1];
    SwingUtilities.invokeAndWait(
        () -> selected[0] = (Locale) picker(new LanguagePanel(e -> {})).getSelectedItem());
    return selected[0];
  }

  @Test
  void itStartsOnTheSavedLanguage() throws Exception {
    assertEquals("en", startsOn("en", "", "").toString());
    assertEquals("es", startsOn("es", "", "").toString());
    assertEquals("fr", startsOn("fr", "", "").toString());
  }

  @Test
  void aSavedRegionPicksTheMatchingRegionalVariant() throws Exception {
    assertEquals("pt_BR", startsOn("pt", "BR", "").toString());
    // Latin-script variants only: the list holds the languages the machine's fonts can display, so
    // zh_TW is missing on a runner without CJK fonts (the CI failure that taught us).
    assertEquals("en_CA", startsOn("en", "CA", "").toString());
    assertEquals("en_GB", startsOn("en", "GB", "").toString());
  }

  @Test
  void aRegionWeHaveNoVariantForFallsBackToTheLanguage() throws Exception {
    assertEquals("es", startsOn("es", "VE", "").toString());
    assertEquals("de", startsOn("de", "AT", "").toString());
  }

  @Test
  void aLanguageWeDoNotTranslateStartsOnEnglish() throws Exception {
    assertEquals("en", startsOn("xx", "", "").toString());
    assertEquals("en", ApplicationSettings.LANGUAGE.getValue());
  }

  @Test
  void theStartingLanguageIsAppliedToTheProgram() throws Exception {
    startsOn("es", "", "");
    assertEquals("es", ApplicationSettings.LANGUAGE.getValue());
    assertEquals("", ApplicationSettings.COUNTRY.getValue());
  }

  @Test
  void pickingAnotherLanguageSavesItAndTellsTheWizard() throws Exception {
    useLanguage("en", "", "");
    int[] notified = {0};
    SwingUtilities.invokeAndWait(
        () -> {
          LanguagePanel panel = new LanguagePanel(e -> notified[0]++);
          JComboBox<Object> picker = picker(panel);
          assertEquals(0, notified[0], "starting on a language is not a pick");
          Locale target = null;
          for (int i = 0; i < picker.getItemCount(); i++) {
            Locale candidate = (Locale) picker.getItemAt(i);
            if (candidate.toString().equals("pt_BR")) {
              target = candidate;
            }
          }
          assertTrue(target != null, "Brazilian Portuguese is offered");
          picker.setSelectedItem(target);
          assertEquals(1, notified[0]);
          assertEquals("pt", ApplicationSettings.LANGUAGE.getValue());
          assertEquals("BR", ApplicationSettings.COUNTRY.getValue());
        });
  }
}
