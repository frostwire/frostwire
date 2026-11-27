/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.IcebaseReports;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The setup wizard's pages and its Next/Back/Finish bookkeeping, built for real, headless: which
 * buttons each position enables, how the page and disk errors reach the user, and what the pages
 * write when the user leaves them.
 */
class SetupWizardPagesTest {
  private boolean icebridge;
  private boolean catalog;
  private boolean reports;

  @BeforeEach
  void save() {
    icebridge = SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue();
    catalog = SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue();
    reports = IcebaseReports.enabled();
  }

  @AfterEach
  void restore() {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(icebridge);
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(catalog);
    IcebaseReports.setEnabled(reports);
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

  private static void flushTheEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }

  /** A page that does what the test tells it to. */
  private static final class FakePage extends SetupWindow {
    ApplySettingsException pageError;
    DiskWork disk;
    final List<Boolean> applied = new ArrayList<>();

    FakePage(SetupManager manager) {
      super(manager, "Fake page", "Nothing here");
    }

    @Override
    public void applySettings(boolean loadCoreComponents) throws ApplySettingsException {
      applied.add(loadCoreComponents);
      if (pageError != null) {
        throw pageError;
      }
    }

    @Override
    DiskWork diskWork() {
      return disk;
    }
  }

  @Test
  void theFirstMiddleAndLastPagesEnableTheRightButtons() {
    SetupManager manager = manager();
    FakePage first = new FakePage(manager);
    FakePage middle = new FakePage(manager);
    FakePage last = new FakePage(manager);
    SetupManager.linkPages(new SetupWindowHolder(), List.of(first, middle, last));

    assertEquals(
        SetupManager.ACTION_CANCEL | SetupManager.ACTION_NEXT, first.getAppropriateActions());
    assertEquals(
        SetupManager.ACTION_CANCEL | SetupManager.ACTION_NEXT | SetupManager.ACTION_PREVIOUS,
        middle.getAppropriateActions());
    assertEquals(
        SetupManager.ACTION_CANCEL | SetupManager.ACTION_FINISH | SetupManager.ACTION_PREVIOUS,
        last.getAppropriateActions());

    FakePage only = new FakePage(manager);
    SetupManager.linkPages(new SetupWindowHolder(), List.of(only));
    assertEquals(
        SetupManager.ACTION_CANCEL | SetupManager.ACTION_FINISH, only.getAppropriateActions());
    assertSame(only, only.getNext());
    assertSame(only, only.getPrevious());
  }

  @Test
  void anUnlinkedPageOnlyAllowsCancelAndBack() {
    FakePage page = new FakePage(manager());
    assertEquals(
        SetupManager.ACTION_CANCEL | SetupManager.ACTION_PREVIOUS, page.getAppropriateActions());
    assertEquals("Fake page", page.getKey());
    assertEquals("Fake page", page.getName());
  }

  @Test
  void aPageWithoutErrorsLetsTheUserMoveOnAtOnce() {
    SetupManager manager = manager();
    FakePage page = new FakePage(manager);
    List<String> results = new ArrayList<>();
    manager.applySettings(page, true, results::add);
    assertEquals(1, results.size());
    assertNull(results.get(0));
    assertEquals(List.of(true), page.applied);
  }

  @Test
  void aPageErrorIsHandedToTheCallerAndAMessagelessOneBecomesEmpty() {
    SetupManager manager = manager();
    FakePage page = new FakePage(manager);
    List<String> results = new ArrayList<>();
    page.pageError = new ApplySettingsException("Pick a folder");
    manager.applySettings(page, false, results::add);
    page.pageError = new ApplySettingsException((String) null);
    manager.applySettings(page, false, results::add);
    assertEquals(List.of("Pick a folder", ""), results);
    assertEquals(List.of(false, false), page.applied);
  }

  @Test
  void diskWorkRunsBeforeTheCallerHearsAndItsErrorComesFirst() throws Exception {
    SetupManager manager = manager();
    FakePage page = new FakePage(manager);
    List<String> events = new CopyOnWriteArrayList<>();
    page.disk = () -> events.add("disk");
    manager.applySettings(page, true, error -> events.add("then:" + error));
    flushTheEdt();
    assertEquals(List.of("disk", "then:null"), events);

    events.clear();
    page.disk =
        () -> {
          events.add("disk");
          throw new ApplySettingsException("Disk is full");
        };
    page.pageError = new ApplySettingsException("Nothing chosen");
    manager.applySettings(page, true, error -> events.add("then:" + error));
    flushTheEdt();
    assertEquals(List.of("disk", "then:Disk is full\n\nNothing chosen"), events);
  }

  @Test
  void aCrashingDiskJobNeverTellsTheCallerItWorked() throws Exception {
    SetupManager manager = manager();
    FakePage page = new FakePage(manager);
    List<String> events = new CopyOnWriteArrayList<>();
    page.disk =
        () -> {
          throw new IllegalStateException("boom");
        };
    assertThrows(
        IllegalStateException.class,
        () -> manager.applySettings(page, true, error -> events.add("then:" + error)));
    flushTheEdt();
    assertEquals(List.of(), events, "the user stays on the page");
  }

  @Test
  void aTallPageGrowsTheDialogOnlyAsFarAsTheScreenAllows() {
    assertEquals(600, SetupManager.heightToFit(600, 500, 500, 1000), "page fits");
    assertEquals(600, SetupManager.heightToFit(600, 500, 400, 1000), "page is smaller than needed");
    assertEquals(700, SetupManager.heightToFit(600, 500, 600, 1000), "grows by what is missing");
    assertEquals(900, SetupManager.heightToFit(600, 500, 2000, 900), "never past the screen");
    assertEquals(950, SetupManager.heightToFit(950, 500, 2000, 900), "never shrinks");
  }

  @Test
  void theSocialPageFollowsTheFirstThreePages() {
    assertFalse(SetupManager.Pages.NONE.socialPage());
    assertTrue(new SetupManager.Pages(true, false, false, false, false).socialPage());
    assertTrue(new SetupManager.Pages(false, true, false, false, false).socialPage());
    assertTrue(new SetupManager.Pages(false, false, true, false, false).socialPage());
    assertFalse(new SetupManager.Pages(false, false, false, true, true).socialPage());
  }

  @Test
  void seedingPageShowsTheStoredChoicesAndDisablesTheCatalogWithoutTheNetwork() throws Exception {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(false);
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(true);
    IcebaseReports.setEnabled(true);
    SwingUtilities.invokeAndWait(
        () -> {
          SeedingPublishingWindow page = new SeedingPublishingWindow(manager());
          page.handleWindowOpeningEvent();
          List<JCheckBox> boxes = all(page, JCheckBox.class);
          assertEquals(3, boxes.size(), "network, catalog, crash reports");
          JCheckBox join = boxes.get(0);
          JCheckBox share = boxes.get(1);
          JCheckBox crash = boxes.get(2);
          assertFalse(join.isSelected());
          assertTrue(share.isSelected());
          assertFalse(share.isEnabled(), "a catalog needs the network");
          assertTrue(crash.isSelected());

          join.setSelected(true);
          assertTrue(share.isEnabled());
          join.setSelected(false);
          assertFalse(share.isEnabled());
        });
  }

  @Test
  void seedingPageSavesWhatTheUserLeftChecked() throws Exception {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(false);
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(false);
    IcebaseReports.setEnabled(true);
    SwingUtilities.invokeAndWait(
        () -> {
          SeedingPublishingWindow page = new SeedingPublishingWindow(manager());
          page.handleWindowOpeningEvent();
          List<JCheckBox> boxes = all(page, JCheckBox.class);
          boxes.get(0).setSelected(true);
          boxes.get(1).setSelected(true);
          boxes.get(2).setSelected(false);
          page.applySettings(true);
        });
    assertTrue(SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    assertTrue(SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
    assertFalse(IcebaseReports.enabled());
  }

  @Test
  void seedingPageThatWasNeverOpenedSavesNothing() throws Exception {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(true);
    SwingUtilities.invokeAndWait(() -> new SeedingPublishingWindow(manager()).applySettings(true));
    assertTrue(SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
  }

  @Test
  void theIntentPageOnlyAllowsFinishOnceAnAnswerIsGiven() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          IntentWindow page = new IntentWindow(manager(), Runnable::run);
          SetupManager.linkPages(new SetupWindowHolder(), List.of(new FakePage(manager()), page));
          page.handleWindowOpeningEvent();
          assertEquals(
              SetupManager.ACTION_CANCEL | SetupManager.ACTION_PREVIOUS,
              page.getAppropriateActions(),
              "no answer yet: no Next, no Finish");
          assertFalse(page.isConfirmedWillNot());
        });
  }

  /** Only "I will not" is offered: the "might use" choice is commented out of the panel. */
  @Test
  void answeringOnTheIntentPageEnablesFinishAndRemembersTheAnswer() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          IntentWindow page = new IntentWindow(manager(), Runnable::run);
          SetupManager.linkPages(new SetupWindowHolder(), List.of(new FakePage(manager()), page));
          page.handleWindowOpeningEvent();
          List<JRadioButton> radios = all(page, JRadioButton.class);
          assertEquals(1, radios.size());
          radios.get(0).doClick();
          assertEquals(
              SetupManager.ACTION_CANCEL
                  | SetupManager.ACTION_FINISH
                  | SetupManager.ACTION_PREVIOUS,
              page.getAppropriateActions());
          assertTrue(page.isConfirmedWillNot());
        });
  }

  @Test
  void theIntentPanelKnowsTheAnswerWasPicked() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          IntentPanel panel = new IntentPanel();
          assertFalse(panel.hasSelection());
          assertFalse(panel.isWillNot());
          int[] clicks = {0};
          panel.addButtonListener(e -> clicks[0]++);
          List<JRadioButton> radios = all(panel, JRadioButton.class);
          assertEquals(1, radios.size());
          radios.get(0).doClick();
          assertTrue(panel.hasSelection());
          assertTrue(panel.isWillNot());
          assertEquals(1, clicks[0]);
        });
  }
}
