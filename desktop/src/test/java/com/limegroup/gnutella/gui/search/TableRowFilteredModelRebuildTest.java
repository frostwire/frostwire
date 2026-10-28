/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.search.FileSearchResult;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Changing the search filters rebuilds the result table in one step (GitHub #1344): one table
 * event, sorted by the current column, every shown line findable by its hash.
 */
class TableRowFilteredModelRebuildTest {

  @Test
  void refilteringFiresOneEventAndKeepsRowsSortedAndIndexed() throws Exception {
    AtomicReference<String> excluded = new AtomicReference<>("none");
    TableRowFilteredModel model =
        new TableRowFilteredModel(line -> !line.getFilename().contains(excluded.get()));
    sortBySeedsDescending(model);
    List<SearchResultDataLine> lines = new ArrayList<>();
    for (int i = 0; i < 300; i++) {
      SearchResultDataLine line = new SearchResultDataLine(model.getColumns());
      // seeds repeat, so ties show whether the order is stable
      line.initialize(result((i % 3 == 0 ? "odd-" : "file-") + i + ".mp3", i % 50));
      lines.add(line);
      model.add(line, model.getRowCount());
    }

    excluded.set("odd-");
    int[] events = {0};
    model.addTableModelListener(e -> events[0]++);
    model.filtersChanged();

    assertEquals(1, events[0], "one table event per rebuild, not one per row");
    List<SearchResultDataLine> expected = new ArrayList<>();
    for (SearchResultDataLine line : lines) {
      if (!line.getFilename().startsWith("odd-")) {
        expected.add(line);
      }
    }
    expected.sort(Comparator.comparingInt(SearchResultDataLine::getSeeds).reversed());
    assertEquals(expected.size(), model.getRowCount());
    for (int row = 0; row < expected.size(); row++) {
      assertEquals(expected.get(row), model.get(row), "row " + row);
      assertEquals(row, model.getRow(expected.get(row)), "index of row " + row);
    }
    assertEquals(lines.size(), model.getTotalResults());

    // the hidden lines come back when the filter lets them through again
    excluded.set("none");
    model.filtersChanged();
    assertEquals(lines.size(), model.getRowCount());
    assertEquals(lines.size(), model.getTotalResults());
  }

  private static void sortBySeedsDescending(TableRowFilteredModel model) throws Exception {
    Class<?> basic = model.getClass().getSuperclass().getSuperclass();
    set(basic, model, "_activeColumn", SearchTableColumns.COUNT_IDX);
    set(basic, model, "_ascending", -1);
    set(basic, model, "_isSorted", true);
  }

  private static void set(Class<?> type, Object target, String name, Object value)
      throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static UISearchResult result(String filename, int seeds) {
    FileSearchResult file =
        (FileSearchResult)
            Proxy.newProxyInstance(
                FileSearchResult.class.getClassLoader(),
                new Class<?>[] {FileSearchResult.class},
                (proxy, method, args) ->
                    answer(method.getName(), method.getReturnType(), filename, seeds));
    return (UISearchResult)
        Proxy.newProxyInstance(
            UISearchResult.class.getClassLoader(),
            new Class<?>[] {UISearchResult.class},
            (proxy, method, args) ->
                method.getName().equals("getSearchResult")
                    ? file
                    : answer(method.getName(), method.getReturnType(), filename, seeds));
  }

  private static Object answer(String method, Class<?> type, String filename, int seeds) {
    switch (method) {
      case "getFilename":
      case "getDisplayName":
      case "getHash":
        return filename;
      case "getExtension":
        return "mp3";
      case "getSeeds":
        return seeds;
      case "getSize":
        return 1_000L;
      case "hashCode":
        return System.identityHashCode(filename);
      case "toString":
        return filename;
      default:
        break;
    }
    if (type == String.class) {
      return "test";
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0d;
    }
    if (type == float.class) {
      return 0f;
    }
    return null;
  }
}
