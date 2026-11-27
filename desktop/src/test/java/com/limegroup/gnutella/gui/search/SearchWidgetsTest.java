/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The range slider, its labels, and the source cell of the results table. */
class SearchWidgetsTest {

  private static LookAndFeel previous;

  @BeforeAll
  static void metal() throws Exception {
    System.setProperty("java.awt.headless", "true");
    previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new MetalLookAndFeel());
  }

  @AfterAll
  static void restore() throws Exception {
    UIManager.setLookAndFeel(previous);
  }

  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }

  @Test
  void theTwoThumbsNeverCrossEachOther() throws Exception {
    onEdt(
        () -> {
          RangeSlider slider = new RangeSlider();
          assertEquals(2, slider.getThumbNum());
          slider.setUpperValue(80);
          slider.setLowerValue(20);
          assertEquals(20, slider.getLowerValue());
          assertEquals(80, slider.getUpperValue());
          slider.setLowerValue(95);
          assertEquals(79, slider.getLowerValue(), "stops one below the upper thumb");
          slider.setLowerValue(10);
          slider.setUpperValue(5);
          assertEquals(11, slider.getUpperValue(), "stops one above the lower thumb");
        });
  }

  @Test
  void theBoundsApplyToBothThumbsAndTheSingleValueIsIgnored() throws Exception {
    onEdt(
        () -> {
          RangeSlider slider = new RangeSlider();
          slider.setMinimum(10);
          slider.setMaximum(500);
          assertEquals(10, slider.getMinimum());
          assertEquals(500, slider.getMaximum());
          assertEquals(10, slider.getModelAt(1).getMinimum());
          assertEquals(500, slider.getModelAt(0).getMaximum());
          slider.setValue(42);
          assertEquals(0, slider.getValue(), "the single value is a dummy");
        });
  }

  @Test
  void aLabeledSliderShowsItsTitleAndRangeLabelsAndTracksTheThumbs() throws Exception {
    onEdt(
        () -> {
          LabeledRangeSlider labeled = new LabeledRangeSlider("Seeds", "Many", 0, 1000);
          assertEquals("0", labeled.getMinimumValueLabel().getText());
          assertEquals("Many", labeled.getMaximumValueLabel().getText());
          assertEquals(0, labeled.getLowerValue());
          assertEquals(1000, labeled.getUpperValue());
          labeled.setLowerValue(250);
          assertEquals(250, labeled.getLowerValue());
          LabeledRangeSlider defaults = new LabeledRangeSlider("Size", null, 0, 100);
          assertEquals("Max", defaults.getMaximumValueLabel().getText());
        });
  }

  private static SourceHolder holderFor(String source) {
    return new SourceHolder(new StubUISearchResult("a.mp3", "mp3", 1, 1, source));
  }

  @Test
  void aSourceHolderShowsItsNameAndUrlAndSortsByName() {
    SourceHolder a = holderFor("Archive.org");
    SourceHolder b = holderFor("TPB");
    assertEquals("Archive.org", a.getSourceName());
    assertEquals("Archive.org", a.getSourceNameHTML());
    assertEquals("Archive.org [https://example.test/a.mp3]", a.toString());
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);
    assertNotNull(a.getUISearchResult());
  }

  @Test
  void theSourceCellShowsTheSourceNameWithAnIcon() throws Exception {
    AtomicReference<Component> shown = new AtomicReference<>();
    onEdt(
        () -> {
          SourceRenderer renderer = new SourceRenderer();
          JTable table = new JTable(1, 1);
          shown.set(
              renderer.getTableCellRendererComponent(
                  table, holderFor("1337x - movies"), false, false, 0, 0));
          assertTrue(shown.get() instanceof JLabel);
          assertEquals("1337x - movies", ((JLabel) shown.get()).getText());
          assertEquals(table.getBackground(), shown.get().getBackground());
          renderer.getTableCellRendererComponent(table, holderFor("TPB"), true, false, 0, 0);
          assertEquals(table.getSelectionBackground(), renderer.getBackground());
          assertTrue(renderer.isOpaque());
          assertFalse(renderer.getCursor().getType() == java.awt.Cursor.DEFAULT_CURSOR);
        });
  }
}
