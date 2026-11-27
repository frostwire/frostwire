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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.filters.TableLineFilter;
import com.frostwire.search.engines.SearchEngine;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The filters that decide which search results the table shows. */
class SearchResultFiltersTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  private static SearchResultDataLine line(String name, String ext, long size, int seeds) {
    return new StubUISearchResult(name, ext, size, seeds, "Archive").line();
  }

  private static final TableLineFilter<SearchResultDataLine> DENY = l -> false;

  @Test
  void allowFilterAllowsEverythingAndAllInstancesAreEqual() {
    assertTrue(AllowFilter.instance().allow(line("a.mp3", "mp3", 1, 1)));
    assertEquals(new AllowFilter(), AllowFilter.instance());
    assertEquals(new AllowFilter().hashCode(), AllowFilter.instance().hashCode());
    assertFalse(AllowFilter.instance().equals("x"));
  }

  @Test
  void compositeAllowsOnlyWhenEveryDelegateAllows() {
    CompositeFilter composite = new CompositeFilter(3);
    SearchResultDataLine l = line("a.mp3", "mp3", 1, 1);
    assertTrue(composite.allow(l));
    assertTrue(composite.setFilter(1, DENY));
    assertFalse(composite.allow(l));
    assertFalse(composite.setFilter(1, DENY), "same filter again changes nothing");
    composite.reset();
    assertTrue(composite.allow(l));
  }

  @Test
  void compositeCannotContainItself() {
    CompositeFilter composite = new CompositeFilter(1);
    assertThrows(IllegalArgumentException.class, () -> composite.setFilter(0, composite));
  }

  @Test
  void settingAnAllowFilterOverAnAllowFilterIsNotAChange() {
    CompositeFilter composite = new CompositeFilter(2);
    assertFalse(composite.setFilter(0, AllowFilter.instance()));
  }

  @Test
  void mediaTypeFilterOnlyAllowsItsType() {
    MediaTypeFilter audio = new MediaTypeFilter(NamedMediaType.getFromExtension("mp3"));
    assertTrue(audio.allow(line("a.mp3", "mp3", 1, 1)));
    assertFalse(audio.allow(line("a.avi", "avi", 1, 1)));
    assertFalse(audio.allow(null));
  }

  @Test
  void engineFilterFollowsTheEnginesCheckbox() {
    Map<SearchEngine, JCheckBox> boxes = new HashMap<>();
    SearchEngineFilter filter = new SearchEngineFilter(boxes);
    StubUISearchResult stub = new StubUISearchResult("a.mp3", "mp3", 1, 1, "Archive");
    SearchEngine engine =
        DesktopSearchEngines.all().stream()
            .filter(e -> e.getId() != null)
            .filter(
                e -> !e.getId().equals(com.frostwire.search.engines.SearchEngineID.TELLURIDE_ID))
            .filter(e -> !e.getId().equals(com.frostwire.search.engines.SearchEngineID.YT_ID))
            .findFirst()
            .orElseThrow();
    stub.engine = engine;
    SearchResultDataLine l = stub.line();
    assertFalse(filter.allow(l), "no checkbox for the engine: hidden");
    JCheckBox box = new JCheckBox();
    boxes.put(engine, box);
    box.setSelected(false);
    assertFalse(filter.allow(l));
    box.setSelected(true);
    assertEquals(engine.isEnabled(), filter.allow(l));
    box.setEnabled(false);
    assertFalse(filter.allow(l));
  }

  @Test
  void aResultWithoutAnEngineHasNoCheckboxSoItIsHiddenNotAnError() {
    SearchEngineFilter filter = new SearchEngineFilter(new HashMap<>());
    StubUISearchResult stub = new StubUISearchResult("a.mp3", "mp3", 1, 1, "Archive");
    stub.engine = null;
    assertFalse(filter.allow(stub.line()));
  }

  // --- GeneralResultFilter: seeds, size and keyword ranges. The mediator is only needed by the
  // setters that report a change, so the tests set the ranges directly.

  private static GeneralResultFilter general() {
    return new GeneralResultFilter(
        null,
        new LabeledRangeSlider("Seeds", null, 0, 1000),
        new LabeledRangeSlider("Size", null, 0, 1000));
  }

  private static void set(Object target, String field, Object value) throws Exception {
    Field f = GeneralResultFilter.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  @Test
  void aSingleResultIsAlwaysInRange() {
    GeneralResultFilter f = general();
    assertTrue(f.allow(line("a.mp3", "mp3", 100, 10)));
  }

  @Test
  void keywordsMustAllBePresentInNameExtensionOrSource() throws Exception {
    GeneralResultFilter f = general();
    SearchResultDataLine l = line("Free Song.mp3", "mp3", 100, 10);
    set(f, "_keywords", "free");
    assertTrue(f.allow(l));
    set(f, "_keywords", "FREE song");
    assertTrue(f.allow(l));
    set(f, "_keywords", "free video");
    assertFalse(f.allow(l));
    set(f, "_keywords", "archive");
    assertTrue(f.allow(l), "the source name counts");
    set(f, "_keywords", "   ");
    assertTrue(f.allow(l));
    set(f, "_keywords", null);
    assertTrue(f.allow(l));
  }

  @Test
  void seedRangeSliderKeepsTheRequestedSliceOfResults() throws Exception {
    GeneralResultFilter f = general();
    SearchResultDataLine low = line("low.mp3", "mp3", 100, 0);
    SearchResultDataLine mid = line("mid.mp3", "mp3", 100, 50);
    SearchResultDataLine high = line("high.mp3", "mp3", 100, 100);
    assertTrue(f.allow(low));
    assertTrue(f.allow(mid));
    assertTrue(f.allow(high));
    // keep the top half: normalised seeds >= 500 of 1000
    set(f, "_minSeeds", 500);
    set(f, "_maxSeeds", 1000);
    assertFalse(f.allow(low));
    assertTrue(f.allow(mid));
    assertTrue(f.allow(high));
    // keep the bottom half
    set(f, "_minSeeds", 0);
    set(f, "_maxSeeds", 400);
    assertTrue(f.allow(low));
    assertFalse(f.allow(mid));
    assertFalse(f.allow(high));
    // a middle band
    set(f, "_minSeeds", 400);
    set(f, "_maxSeeds", 600);
    assertFalse(f.allow(low));
    assertTrue(f.allow(mid));
    assertFalse(f.allow(high));
  }

  @Test
  void sizeRangeSliderKeepsTheRequestedSliceOfResults() throws Exception {
    GeneralResultFilter f = general();
    SearchResultDataLine small = line("s.mp3", "mp3", 0, 5);
    SearchResultDataLine medium = line("m.mp3", "mp3", 500, 5);
    SearchResultDataLine large = line("l.mp3", "mp3", 1000, 5);
    f.allow(small);
    f.allow(medium);
    f.allow(large);
    set(f, "_minSize", 600);
    set(f, "_maxSize", 1000);
    assertFalse(f.allow(small));
    assertFalse(f.allow(medium));
    assertTrue(f.allow(large));
    set(f, "_minSize", 0);
    set(f, "_maxSize", 600);
    assertTrue(f.allow(small));
    assertTrue(f.allow(medium));
    assertFalse(f.allow(large));
    set(f, "_minSize", 400);
    set(f, "_maxSize", 600);
    assertFalse(f.allow(small));
    assertTrue(f.allow(medium));
    assertFalse(f.allow(large));
  }

  @Test
  void theSliderLabelsFollowTheSmallestAndLargestResultSeen() {
    LabeledRangeSlider seeds = new LabeledRangeSlider("Seeds", null, 0, 1000);
    LabeledRangeSlider sizes = new LabeledRangeSlider("Size", null, 0, 1000);
    GeneralResultFilter f = new GeneralResultFilter(null, seeds, sizes);
    f.allow(line("a.mp3", "mp3", 2048, 3));
    f.allow(line("b.mp3", "mp3", 10 * 1024, 40));
    assertEquals("3", seeds.getMinimumValueLabel().getText());
    assertEquals("40", seeds.getMaximumValueLabel().getText());
    assertTrue(sizes.getMinimumValueLabel().getText().endsWith("KB"));
    assertTrue(sizes.getMaximumValueLabel().getText().startsWith("10"));
    assertSame(seeds.getMinimumValueLabel(), seeds.getMinimumValueLabel());
  }
}
