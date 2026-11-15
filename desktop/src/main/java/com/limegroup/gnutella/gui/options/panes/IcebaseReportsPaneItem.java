/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.IcebaseReports;
import com.limegroup.gnutella.gui.IcebaseReportsText;
import javax.swing.*;

/**
 * Turns anonymous crash and freeze reports to Icebase on or off at any time, with the same
 * explanation as the setup wizard (frostwire/frostwire#1565).
 */
public final class IcebaseReportsPaneItem extends AbstractPaneItem {
  private final JCheckBox checkbox;
  private boolean isDirty = false;

  public IcebaseReportsPaneItem() {
    super(IcebaseReportsText.paneTitle(), IcebaseReportsText.note());
    checkbox = new JCheckBox(IcebaseReportsText.label());
    checkbox.addActionListener(e -> isDirty = true);
    add(checkbox);
  }

  @Override
  public boolean isDirty() {
    return isDirty;
  }

  @Override
  public void initOptions() {
    checkbox.setSelected(IcebaseReports.enabled());
    isDirty = false;
  }

  /** Stores the choice; opting out also deletes the reports waiting on disk, off this thread. */
  @Override
  public boolean applyOptions() {
    IcebaseReports.setEnabled(checkbox.isSelected());
    isDirty = false;
    return false;
  }
}
