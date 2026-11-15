/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ApplicationSettings;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

/**
 * Options &gt; Advanced &gt; Performance: Automatic, Full or Lite (frostwire/frostwire#1555). Full
 * and Lite apply right away; Automatic decides when FrostWire starts.
 */
public final class PerformancePaneItem extends AbstractPaneItem {
  public static final String TITLE = I18n.tr("Performance Mode");
  public static final String LABEL =
      I18n.tr(
          "Lite turns off animations, cover art, live sorting and other visual extras to keep FrostWire responsive. Downloads, seeding and searches are never slowed down.");

  final JRadioButton automatic = new JRadioButton(I18n.tr("Automatic (recommended)"));
  final JRadioButton full = new JRadioButton(I18n.tr("Full"));
  final JRadioButton lite = new JRadioButton(I18n.tr("Lite"));
  final JLabel current = new JLabel();

  public PerformancePaneItem() {
    super(TITLE, LABEL);
    ButtonGroup group = new ButtonGroup();
    BoxPanel choices = new BoxPanel();
    for (JRadioButton button : new JRadioButton[] {automatic, full, lite}) {
      group.add(button);
      choices.add(button);
    }
    BoxPanel row = new BoxPanel(BoxPanel.X_AXIS);
    row.add(choices);
    row.add(Box.createHorizontalGlue());
    add(row);
    add(getVerticalSeparator());
    add(
        new JLabel(
            I18n.tr(
                "Automatic picks Lite on slow computers when FrostWire starts. Full and Lite apply right away.")));
    add(getVerticalSeparator());
    add(current);
  }

  @Override
  public void initOptions() {
    switch (savedChoice()) {
      case FULL -> full.setSelected(true);
      case LITE -> lite.setSelected(true);
      default -> automatic.setSelected(true);
    }
    showCurrentMode();
  }

  @Override
  public boolean applyOptions() {
    boolean restart = apply(selectedChoice());
    showCurrentMode();
    return restart;
  }

  /**
   * Saves {@code choice} if it changed. Returns whether it needs a restart: never, since Full and
   * Lite apply now and Automatic decides at the next start.
   */
  static boolean apply(Choice choice) {
    if (choice != savedChoice()) {
      PerformanceMode.choose(choice);
    }
    return false;
  }

  @Override
  public boolean isDirty() {
    return selectedChoice() != savedChoice();
  }

  Choice selectedChoice() {
    if (full.isSelected()) {
      return Choice.FULL;
    }
    return lite.isSelected() ? Choice.LITE : Choice.AUTOMATIC;
  }

  static Choice savedChoice() {
    return Choice.parse(ApplicationSettings.PERFORMANCE_MODE.getValue());
  }

  private void showCurrentMode() {
    current.setText(currentModeText());
  }

  static String currentModeText() {
    return PerformanceMode.isLite() ? I18n.tr("Running now: Lite") : I18n.tr("Running now: Full");
  }
}
