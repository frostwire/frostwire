/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.SearchSettings;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Closing Options must notice a changed clipboard checkbox (frostwire/frostwire#1654). */
class AutoSearchClipboardURLsDirtyTest {
  private boolean autoSearch;
  private boolean magnetListener;

  @BeforeEach
  void save() {
    autoSearch = SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue();
    magnetListener = ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.getValue();
  }

  @AfterEach
  void restore() {
    SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.setValue(autoSearch);
    ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.setValue(magnetListener);
  }

  @Test
  void isDirtyOnlyWhileTheCheckboxDiffersFromTheSetting() throws Exception {
    SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.setValue(true);
    AutoSearchClipboardURLsPaneItem pane = PaneSupport.onEdt(AutoSearchClipboardURLsPaneItem::new);
    pane.initOptions();
    assertFalse(pane.isDirty());

    JCheckBox box = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    box.setSelected(false);
    assertTrue(pane.isDirty());

    pane.applyOptions();
    assertFalse(pane.isDirty());
  }
}
