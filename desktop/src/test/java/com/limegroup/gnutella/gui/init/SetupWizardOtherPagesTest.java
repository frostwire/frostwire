/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.StartupSettings;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What the wizard's remaining pages show when the user reaches them. */
class SetupWizardOtherPagesTest {
  private boolean startup;

  @BeforeEach
  void save() {
    startup = StartupSettings.RUN_ON_STARTUP.getValue();
  }

  @AfterEach
  void restore() {
    StartupSettings.RUN_ON_STARTUP.setValue(startup);
  }

  private static SetupManager manager() {
    return new SetupManager(() -> SetupManager.Pages.NONE, status -> {}, Runnable::run);
  }

  private static <T extends Component> List<T> all(Component root, Class<T> type) {
    List<T> found = new ArrayList<>();
    if (type.isInstance(root)) {
      found.add(type.cast(root));
    }
    if (root instanceof Container c) {
      for (Component child : c.getComponents()) {
        found.addAll(all(child, type));
      }
    }
    return found;
  }

  private static String texts(Component root) {
    StringBuilder text = new StringBuilder();
    for (JLabel label : all(root, JLabel.class)) {
      if (label.getText() != null) {
        text.append(label.getText()).append('\n');
      }
    }
    for (JTextArea area : all(root, JTextArea.class)) {
      text.append(area.getText()).append('\n');
    }
    return text.toString();
  }

  @Test
  void everyPageShowsItsTitleAndDescriptionAtTheTop() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SetupManager manager = manager();
          SetupWindow[] pages = {
            new WelcomeWindow(manager, false),
            new WelcomeWindow(manager, true),
            new MiscWindow(manager),
            new AssociationsWindow(manager),
            new SocialRecommendationsWindow(manager),
            new SeedingPublishingWindow(manager),
            new IntentWindow(manager, Runnable::run),
          };
          for (SetupWindow page : pages) {
            page.handleWindowOpeningEvent();
            String shown = texts(page);
            assertTrue(
                shown.contains(page.getName()),
                page.getClass().getSimpleName() + " has no title in: " + shown);
          }
        });
  }

  @Test
  void theWelcomePageSaysWhetherOnlyNewFeaturesNeedConfiguring() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SetupManager manager = manager();
          WelcomeWindow fresh = new WelcomeWindow(manager, false);
          fresh.handleWindowOpeningEvent();
          WelcomeWindow partial = new WelcomeWindow(manager, true);
          partial.handleWindowOpeningEvent();
          assertTrue(texts(fresh).contains("optimum performance"), texts(fresh));
          assertTrue(texts(partial).contains("recently added new features"), texts(partial));
          assertFalse(texts(partial).contains("optimum performance"));
        });
  }

  @Test
  void theMiscPageShowsTheStartupChoiceOnlyWhereTheOsHasOne() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StartupSettings.RUN_ON_STARTUP.setValue(true);
          MiscWindow page = new MiscWindow(manager());
          page.handleWindowOpeningEvent();
          List<JCheckBox> boxes = all(page, JCheckBox.class);
          if (GUIUtils.shouldShowStartOnStartupWindow()) {
            assertEquals(1, boxes.size());
            assertEquals(I18n.tr("Start Automatically"), boxes.get(0).getText());
            assertTrue(boxes.get(0).isSelected(), "shows the stored choice");
          } else {
            assertTrue(boxes.isEmpty());
          }
        });
  }

  @Test
  void theMiscPageStartupBoxShowsAStoredNo() throws Exception {
    Assumptions.assumeTrue(GUIUtils.shouldShowStartOnStartupWindow());
    SwingUtilities.invokeAndWait(
        () -> {
          StartupSettings.RUN_ON_STARTUP.setValue(false);
          MiscWindow page = new MiscWindow(manager());
          page.handleWindowOpeningEvent();
          assertFalse(all(page, JCheckBox.class).get(0).isSelected());
        });
  }

  @Test
  void theAssociationsPageOffersEveryAssociationCheckedAndAlwaysTakeThemPicked() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          AssociationsWindow page = new AssociationsWindow(manager());
          page.handleWindowOpeningEvent();
          List<JCheckBox> boxes = all(page, JCheckBox.class);
          int supported = 0;
          for (Object ignored :
              com.limegroup.gnutella.gui.shell.FrostAssociations.getSupportedAssociations()) {
            supported++;
          }
          assertEquals(supported, boxes.size(), "one box per association this OS supports");
          for (JCheckBox box : boxes) {
            assertTrue(box.isSelected(), box.getText() + " starts checked");
          }
          List<JRadioButton> radios = all(page, JRadioButton.class);
          assertEquals(3, radios.size(), "always, ask, never");
          JRadioButton always =
              radios.stream()
                  .filter(
                      r -> r.getText().equals(I18n.tr("Always take the selected associations.")))
                  .findFirst()
                  .orElseThrow();
          assertTrue(always.isSelected());
          assertEquals(1, radios.stream().filter(JRadioButton::isSelected).count());
        });
  }

  @Test
  void theSocialPagePointsAtTheCommunity() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SocialRecommendationsWindow page = new SocialRecommendationsWindow(manager());
          page.handleWindowOpeningEvent();
          assertTrue(texts(page).contains("Keep in Touch!"), texts(page));
          assertEquals(
              List.of("SOCIAL_MEDIA_CHECK", "SOCIAL_WIZARD_TWITTER", "SOCIAL_WIZARD_FACEBOOK"),
              SocialRecommendationsWindow.ICONS);
        });
  }
}
