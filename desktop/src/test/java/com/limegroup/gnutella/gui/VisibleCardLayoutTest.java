/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The main window's tab holder and the transfer detail holder are card containers. Their size must
 * come from the card on show: measuring hidden tabs made every window layout pass lay out every tab
 * (frostwire/frostwire#1342).
 */
class VisibleCardLayoutTest {

  /** A card that counts how often a layout measured it. */
  private static final class Card extends JPanel {
    final AtomicInteger measured = new AtomicInteger();

    Card(int minimum, int preferred) {
      setMinimumSize(new Dimension(minimum, minimum));
      setPreferredSize(new Dimension(preferred, preferred));
    }

    @Override
    public Dimension getMinimumSize() {
      measured.incrementAndGet();
      return super.getMinimumSize();
    }

    @Override
    public Dimension getPreferredSize() {
      measured.incrementAndGet();
      return super.getPreferredSize();
    }
  }

  @Test
  void sizesComeFromTheCardOnShowOnly() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          VisibleCardLayout layout = new VisibleCardLayout();
          JPanel holder = new JPanel(layout);
          holder.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
          Card small = new Card(10, 20);
          Card huge = new Card(500, 900);
          holder.add(small, "small");
          holder.add(huge, "huge");

          assertEquals(new Dimension(10, 11), holder.getMinimumSize());
          assertEquals(new Dimension(20, 21), holder.getPreferredSize());
          assertEquals(0, huge.measured.get(), "the hidden card must not be measured");

          layout.show(holder, "huge");
          small.measured.set(0);
          assertEquals(new Dimension(500, 501), holder.getMinimumSize());
          assertEquals(new Dimension(900, 901), holder.getPreferredSize());
          assertEquals(0, small.measured.get(), "the hidden card must not be measured");
        });
  }

  /** The window's smallest size must still fit the largest tab, not just the one on show. */
  @Test
  void largestCardMinimumSizeCoversHiddenCards() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          VisibleCardLayout layout = new VisibleCardLayout();
          JPanel holder = new JPanel(layout);
          holder.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
          holder.add(new Card(10, 20), "small");
          holder.add(new Card(500, 900), "huge");

          assertEquals(new Dimension(500, 501), layout.largestCardMinimumSize(holder));
          assertEquals(new Dimension(10, 11), holder.getMinimumSize());
        });
  }

  @Test
  void windowMinimumSizeMeasuresEveryTab() throws Exception {
    String source = readSource("com/limegroup/gnutella/gui/MainFrame.java").replaceAll("\\s+", "");
    int setMinimalSize = source.indexOf("privatevoidsetMinimalSize(");
    assertTrue(setMinimalSize >= 0, "setMinimalSize not found");
    assertTrue(
        source.substring(setMinimalSize).contains("cards.largestCardMinimumSize(c)"),
        "the window's minimum size must fit every tab");
  }

  /** Both need the running app to be built, so check their sources. */
  @Test
  void tabHolderAndTransferDetailHolderUseIt() throws Exception {
    assertTrue(
        readSource("com/limegroup/gnutella/gui/MainFrame.java")
            .contains("TABBED_PANE = new JPanel(new VisibleCardLayout());"),
        "the main window's tabs must be sized from the tab on show");
    assertTrue(
        readSource("com/frostwire/gui/components/transfers/TransferDetailComponent.java")
            .contains("detailComponentHolder = new JPanel(new VisibleCardLayout());"),
        "the transfer detail panels must be sized from the panel on show");
  }

  private static String readSource(String file) throws Exception {
    Path path = Path.of("src/main/java", file);
    if (!Files.exists(path)) {
      path = Path.of("desktop/src/main/java", file);
    }
    return Files.readString(path);
  }

  @Test
  void emptyHolderMeasuresLikeCardLayout() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel visibleOnly = new JPanel(new VisibleCardLayout(), true);
          JPanel plain = new JPanel(new CardLayout(), true);
          visibleOnly.setBorder(BorderFactory.createEmptyBorder(1, 2, 3, 4));
          plain.setBorder(BorderFactory.createEmptyBorder(1, 2, 3, 4));
          assertEquals(plain.getMinimumSize(), visibleOnly.getMinimumSize());
          assertEquals(plain.getPreferredSize(), visibleOnly.getPreferredSize());
        });
  }

  /**
   * A plain CardLayout measures every card, hidden ones too, on each layout pass of its window.
   * Holders in the main window must measure only the card on show (GitHub #1342, #1347: the search
   * tabs re-measured the hidden search-field card, Icebase #b7135455d885, #470fc855fd81). Plain
   * CardLayout stays only where it is harmless, each with its reason.
   */
  @Test
  void mainWindowCardHoldersMeasureOnlyTheVisibleCard() throws Exception {
    Map<String, String> allowed =
        Map.of(
            "CopyrightLicenseSelectorPanel.java", "dialog",
            "LibraryMediator.java", "a single card",
            "TransferDetailGeneral.java", "two small cards: the recheck button and its label",
            "SetupWindowHolder.java", "setup wizard dialog, sized to fit every page",
            "OptionsPaneManager.java", "Options dialog, sized to fit every pane");
    List<String> offenders;
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      offenders =
          files
              .filter(f -> f.toString().endsWith(".java"))
              .filter(f -> !allowed.containsKey(f.getFileName().toString()))
              .filter(f -> read(f).contains("new CardLayout()"))
              .map(Path::toString)
              .toList();
    }
    assertTrue(offenders.isEmpty(), "plain CardLayout in " + offenders);
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
