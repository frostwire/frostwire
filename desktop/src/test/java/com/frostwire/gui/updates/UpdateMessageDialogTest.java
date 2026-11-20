/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The update popup was a fixed-size JOptionPane: not resizable, and its text only broke at the
 * message's own line breaks. It is now a resizable window whose message wraps to its width.
 */
class UpdateMessageDialogTest {
  private static final String LONG =
      "<html><strong>A new version of FrostWire is available.</strong><br>"
          + "&#8226; Security update: the installers bundle OpenJDK 27.0.1.1 with the November 17"
          + " critical Java patch, so update even if you keep your own copy of Java.<br>"
          + "&#8226; New BitTorrent engine: libtorrent 2.1 through jlibtorrent 2.1.2.1 brings"
          + " upstream fixes for stalls and rechecks and smaller saved resume data.<br>";

  @Test
  void theMessageIsInAScrollPaneAboveOneButtonPerOption() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel panel =
              UpdateMessageDialog.content(LONG, new String[] {"Later", "Go to webpage"}, i -> {});
          List<Component> all = flatten(panel);
          assertTrue(all.stream().anyMatch(JScrollPane.class::isInstance));
          JEditorPane text =
              (JEditorPane)
                  all.stream().filter(JEditorPane.class::isInstance).findFirst().orElseThrow();
          assertFalse(text.isEditable());
          assertEquals(
              List.of("Later", "Go to webpage"),
              all.stream()
                  .filter(JButton.class::isInstance)
                  .map(c -> ((JButton) c).getText())
                  .filter(label -> !label.isEmpty())
                  .toList());
        });
  }

  @Test
  void clickingAButtonReportsItsIndex() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          List<Integer> chosen = new ArrayList<>();
          JPanel panel =
              UpdateMessageDialog.content(
                  LONG, new String[] {"Later", "Go to webpage"}, chosen::add);
          List<JButton> buttons =
              flatten(panel).stream()
                  .filter(JButton.class::isInstance)
                  .map(JButton.class::cast)
                  .filter(button -> !button.getText().isEmpty())
                  .toList();
          buttons.get(1).doClick();
          buttons.get(0).doClick();
          assertEquals(List.of(1, 0), chosen);
        });
  }

  @Test
  void theButtonRowWidthCoversEveryButton() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel panel =
              UpdateMessageDialog.content(
                  LONG, new String[] {"Thanks, but not now", "Go to webpage"}, i -> {});
          int needed =
              flatten(panel).stream()
                  .filter(JButton.class::isInstance)
                  .filter(c -> !((JButton) c).getText().isEmpty())
                  .mapToInt(c -> c.getPreferredSize().width)
                  .sum();
          assertTrue(UpdateMessageDialog.buttonRowWidth(panel) >= needed);
        });
  }

  @Test
  void theTextWrapsWhenTheWindowIsNarrower() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JEditorPane text = new JEditorPane("text/html", LONG);
          int wide = UpdateMessageDialog.textHeightForWidth(text, 1400);
          int narrow = UpdateMessageDialog.textHeightForWidth(text, 300);
          assertTrue(narrow > wide, "narrow " + narrow + " should be taller than wide " + wide);
        });
  }

  @Test
  void theWindowIsResizableAndUpdateManagerUsesIt() throws Exception {
    Path root =
        Path.of(System.getProperty("user.dir")).resolve("src/main/java/com/frostwire/gui/updates");
    String dialog =
        Files.readString(root.resolve("UpdateMessageDialog.java"), StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(dialog.contains("dialog.setResizable(true)"));
    String manager =
        Files.readString(root.resolve("UpdateManager.java"), StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(manager.contains("UpdateMessageDialog.show(title,finalUpdateMessage,finalOptions)"));
    assertFalse(manager.contains("JOptionPane.showOptionDialog"));
  }

  private static List<Component> flatten(Component component) {
    List<Component> all = new ArrayList<>();
    all.add(component);
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        all.addAll(flatten(child));
      }
    }
    assertNotNull(all);
    return all;
  }
}
