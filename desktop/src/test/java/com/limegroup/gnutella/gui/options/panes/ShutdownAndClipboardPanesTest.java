/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.settings.SearchSettings;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Shutdown Behavior and Auto-search URLs panes, and the Network Interface change rule. */
class ShutdownAndClipboardPanesTest {
  private boolean minimizeToTray;
  private boolean showExitDialog;
  private boolean autoSearch;
  private boolean magnetListener;

  @BeforeEach
  void save() {
    minimizeToTray = ApplicationSettings.MINIMIZE_TO_TRAY.getValue();
    showExitDialog = ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.getValue();
    autoSearch = SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue();
    magnetListener = ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.getValue();
  }

  @AfterEach
  void restore() {
    ApplicationSettings.MINIMIZE_TO_TRAY.setValue(minimizeToTray);
    ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.setValue(showExitDialog);
    SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.setValue(autoSearch);
    ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.setValue(magnetListener);
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.revertToDefault();
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.revertToDefault();
  }

  @Test
  void shutdownImmediatelyIsPickedWhenTheTrayIsNotUsed() throws Exception {
    ApplicationSettings.MINIMIZE_TO_TRAY.setValue(false);
    ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.setValue(true);
    ShutdownPaneItem pane = PaneSupport.onEdt(ShutdownPaneItem::new);
    pane.initOptions();
    List<JRadioButton> radios = PaneSupport.all(pane.getContainer(), JRadioButton.class);
    assertFalse(radios.isEmpty());
    assertTrue(radios.get(0).isSelected(), "Shutdown Immediately");
    assertTrue(PaneSupport.only(pane.getContainer(), JCheckBox.class).isSelected());
    assertFalse(pane.isDirty());
  }

  @Test
  void theExitDialogBoxIsSavedWhenApplied() throws Exception {
    ApplicationSettings.MINIMIZE_TO_TRAY.setValue(false);
    ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.setValue(true);
    ShutdownPaneItem pane = PaneSupport.onEdt(ShutdownPaneItem::new);
    pane.initOptions();
    JCheckBox ask = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    ask.setSelected(false);
    assertTrue(pane.isDirty());
    assertFalse(pane.applyOptions());
    assertFalse(ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.getValue());
    assertFalse(ApplicationSettings.MINIMIZE_TO_TRAY.getValue());
    assertFalse(pane.isDirty());
  }

  @Test
  void autoSearchShowsAndSavesTheClipboardChoiceToBothSettings() throws Exception {
    SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.setValue(true);
    ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.setValue(false);
    AutoSearchClipboardURLsPaneItem pane = PaneSupport.onEdt(AutoSearchClipboardURLsPaneItem::new);
    pane.initOptions();
    JCheckBox box = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    assertTrue(box.isSelected());

    box.setSelected(false);
    assertFalse(pane.applyOptions());
    assertFalse(SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue());
    assertFalse(ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.getValue());

    box.setSelected(true);
    pane.applyOptions();
    assertTrue(SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue());
    assertTrue(ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.getValue());
  }

  @Test
  void theNetworkInterfaceIsOnlyChangedWhenTheChoiceOrTheAddressDiffers() {
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.setValue(false);
    assertFalse(NetworkInterfacePaneItem.changed(false, null));
    assertFalse(NetworkInterfacePaneItem.changed(false, "10.0.0.2"), "address is moot when off");
    assertTrue(NetworkInterfacePaneItem.changed(true, "10.0.0.2"));

    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.setValue(true);
    ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.setValue("10.0.0.2");
    assertFalse(NetworkInterfacePaneItem.changed(true, "10.0.0.2"));
    assertFalse(NetworkInterfacePaneItem.changed(true, null), "no address picked, none to change");
    assertTrue(NetworkInterfacePaneItem.changed(true, "10.0.0.3"));
    assertTrue(NetworkInterfacePaneItem.changed(false, "10.0.0.2"));
    assertEquals("10.0.0.2", ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue());
  }
}
