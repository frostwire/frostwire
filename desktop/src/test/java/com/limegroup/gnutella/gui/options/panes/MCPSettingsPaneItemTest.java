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

import com.limegroup.gnutella.gui.WholeNumberField;
import com.limegroup.gnutella.settings.MCPSettings;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.Setting;

/**
 * Options &gt; MCP Server. The server is never started here: the tests leave it disabled, so the
 * pane only reads and writes its settings.
 */
class MCPSettingsPaneItemTest {
  private static final Setting[] TOUCHED = {
    MCPSettings.MCP_SERVER_ENABLED,
    MCPSettings.MCP_SERVER_HOST,
    MCPSettings.MCP_SERVER_PORT,
    MCPSettings.MCP_SERVER_TLS_ENABLED,
    MCPSettings.MCP_SERVER_AUTO_START,
  };

  @BeforeEach
  void defaults() {
    for (Setting s : TOUCHED) {
      s.revertToDefault();
    }
  }

  @AfterEach
  void restore() {
    for (Setting s : TOUCHED) {
      s.revertToDefault();
    }
  }

  private static MCPSettingsPaneItem open() throws Exception {
    MCPSettingsPaneItem pane = PaneSupport.onEdt(MCPSettingsPaneItem::new);
    pane.initOptions();
    return pane;
  }

  private static JCheckBox box(MCPSettingsPaneItem pane, String text) {
    return PaneSupport.all(pane.getContainer(), JCheckBox.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  private static JButton button(MCPSettingsPaneItem pane, String text) {
    return PaneSupport.all(pane.getContainer(), JButton.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  private static JTextField host(MCPSettingsPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JTextField.class).stream()
        .filter(f -> !(f instanceof WholeNumberField))
        .findFirst()
        .orElseThrow();
  }

  private static WholeNumberField port(MCPSettingsPaneItem pane) {
    return PaneSupport.only(pane.getContainer(), WholeNumberField.class);
  }

  @Test
  void aDisabledServerLeavesItsFieldsAndButtonsOff() throws Exception {
    MCPSettingsPaneItem pane = open();
    assertFalse(box(pane, "Enable MCP Server").isSelected());
    assertFalse(host(pane).isEnabled());
    assertFalse(port(pane).isEnabled());
    assertFalse(box(pane, "Enable HTTPS (TLS)").isEnabled());
    assertFalse(box(pane, "Start MCP server when FrostWire starts").isEnabled());
    assertFalse(button(pane, "Start").isEnabled());
    assertFalse(button(pane, "Stop").isEnabled());
    assertFalse(button(pane, "Restart").isEnabled());
    assertFalse(pane.isDirty());
  }

  @Test
  void enablingTheServerOpensTheFieldsAndOnlyStartIsAvailable() throws Exception {
    MCPSettingsPaneItem pane = open();
    box(pane, "Enable MCP Server").setSelected(true);
    assertTrue(host(pane).isEnabled());
    assertTrue(port(pane).isEnabled());
    assertTrue(box(pane, "Enable HTTPS (TLS)").isEnabled());
    assertTrue(box(pane, "Start MCP server when FrostWire starts").isEnabled());
    // the server is not running, so there is nothing to stop or restart
    assertTrue(pane.isDirty());
  }

  @Test
  void theStoredSettingsAreShownOnOpen() throws Exception {
    MCPSettings.MCP_SERVER_HOST.setValue("0.0.0.0");
    MCPSettings.MCP_SERVER_PORT.setValue(9001);
    MCPSettings.MCP_SERVER_TLS_ENABLED.setValue(true);
    MCPSettings.MCP_SERVER_AUTO_START.setValue(true);
    MCPSettingsPaneItem pane = open();
    assertEquals("0.0.0.0", host(pane).getText());
    assertEquals(9001, port(pane).getValue());
    assertTrue(box(pane, "Enable HTTPS (TLS)").isSelected());
    assertTrue(box(pane, "Start MCP server when FrostWire starts").isSelected());
    assertFalse(pane.isDirty());
  }

  @Test
  void everyFieldMakesThePaneDirtyWhenItChanges() throws Exception {
    MCPSettingsPaneItem pane = open();
    host(pane).setText(" other.example ");
    assertTrue(pane.isDirty());
    host(pane).setText(MCPSettings.MCP_SERVER_HOST.getValue() + "  ");
    assertFalse(pane.isDirty(), "surrounding blanks do not count");
    port(pane).setValue(MCPSettings.MCP_SERVER_PORT.getValue() + 1);
    assertTrue(pane.isDirty());
    port(pane).setValue(MCPSettings.MCP_SERVER_PORT.getValue());
    box(pane, "Enable HTTPS (TLS)").setSelected(true);
    assertTrue(pane.isDirty());
    box(pane, "Enable HTTPS (TLS)").setSelected(false);
    box(pane, "Start MCP server when FrostWire starts").setSelected(true);
    assertTrue(pane.isDirty());
  }

  @Test
  void applyingSavesTheTrimmedHostPortAndFlagsWithoutStartingAnything() throws Exception {
    MCPSettingsPaneItem pane = open();
    host(pane).setText("  127.0.0.2 ");
    port(pane).setValue(9100);
    box(pane, "Enable HTTPS (TLS)").setSelected(true);
    box(pane, "Start MCP server when FrostWire starts").setSelected(true);

    assertFalse(pane.applyOptions());
    assertEquals("127.0.0.2", MCPSettings.MCP_SERVER_HOST.getValue());
    assertEquals(9100, MCPSettings.MCP_SERVER_PORT.getValue());
    assertTrue(MCPSettings.MCP_SERVER_TLS_ENABLED.getValue());
    assertTrue(MCPSettings.MCP_SERVER_AUTO_START.getValue());
    assertFalse(MCPSettings.MCP_SERVER_ENABLED.getValue());
    assertFalse(pane.isDirty());
  }

  @Test
  void aPortOutsideTheValidRangeIsRefusedAndNothingIsSaved() throws Exception {
    for (int bad : new int[] {0, 65536, 99999}) {
      MCPSettingsPaneItem pane = open();
      port(pane).setValue(bad);
      host(pane).setText("changed.example");
      try (PaneSupport.Errors errors = new PaneSupport.Errors()) {
        assertFalse(pane.applyOptions());
        assertEquals(List.of("Port must be between 1 and 65535"), errors.shown);
      }
      assertEquals(
          MCPSettings.MCP_SERVER_HOST.getDefaultValue(), MCPSettings.MCP_SERVER_HOST.getValue());
      assertEquals(
          MCPSettings.MCP_SERVER_PORT.getDefaultValue(), MCPSettings.MCP_SERVER_PORT.getValue());
    }
  }

  @Test
  void anEmptyHostIsRefusedAndNothingIsSaved() throws Exception {
    MCPSettingsPaneItem pane = open();
    host(pane).setText("   ");
    box(pane, "Start MCP server when FrostWire starts").setSelected(true);
    try (PaneSupport.Errors errors = new PaneSupport.Errors()) {
      assertFalse(pane.applyOptions());
      assertEquals(List.of("Host cannot be empty"), errors.shown);
    }
    assertFalse(MCPSettings.MCP_SERVER_AUTO_START.getValue());
  }

  @Test
  void theClientsTableHasItsThreeColumns() throws Exception {
    MCPSettingsPaneItem pane = open();
    JTable table = PaneSupport.only(pane.getContainer(), JTable.class);
    assertEquals(3, table.getColumnCount());
    assertEquals("Client", table.getColumnName(0));
    assertEquals("Status", table.getColumnName(1));
    assertEquals("Actions", table.getColumnName(2));
    assertFalse(table.getModel().isCellEditable(0, 0));
    assertTrue(table.getModel().isCellEditable(0, 2));
  }
}
