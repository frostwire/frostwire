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

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Showing Transfers right after start selected a Library folder, which cleared the Library search
 * before LibrarySearch had created its search field (Icebase #0ecc9b3a9246,
 * frostwire/frostwire#1572, NullPointerException in LibrarySearch.clear). The SearchField itself
 * can't be built headless (ThemeMediator needs a display), so the test holds its creation back and
 * checks the source for the order the waiting work runs in once it exists.
 */
class LibrarySearchBeforeFieldTest {

  @Test
  void clearAndPromptBeforeTheFieldExistsDoNotThrow() throws Exception {
    List<Runnable> heldFieldCreation = new ArrayList<>();
    Throwable[] thrown = new Throwable[1];
    LibrarySearch[] holder = new LibrarySearch[1];
    SwingUtilities.invokeAndWait(
        () -> {
          LibrarySearch search = new LibrarySearch(heldFieldCreation::add);
          holder[0] = search;
          try {
            search.setStatus("3 search results");
            search.clear();
            search.setSearchPrompt("Search your Audio");
          } catch (Throwable t) {
            thrown[0] = t;
          }
        });

    assertNull(thrown[0], () -> "clear()/setSearchPrompt() threw " + thrown[0]);
    assertEquals(1, heldFieldCreation.size(), "the field's creation is still pending");
    assertNull(holder[0].getSearchField());
  }

  @Test
  void fieldStateIsAppliedAfterTheDefaultsOnceTheFieldExists() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/frostwire/gui/library/LibrarySearch.java"));

    String clear = body(source, "public void clear()");
    assertTrue(clear.contains("whenSearchFieldReady(field -> field.setText(\"\"))"), clear);
    assertFalse(clear.contains("searchField."), clear);

    String prompt = body(source, "void setSearchPrompt(String string)");
    assertTrue(prompt.contains("whenSearchFieldReady(field -> field.setPrompt(string))"), prompt);
    assertFalse(prompt.contains("searchField."), prompt);

    String searchFor = body(source, "public void searchFor(");
    assertTrue(searchFor.contains("whenSearchFieldReady("), searchFor);
    assertFalse(searchFor.contains("searchField."), searchFor);

    // The default "Search in Library" prompt is set before the waiting work runs, so the folder's
    // prompt that was asked for earlier wins.
    String create = body(source, "private void createSearchField()");
    assertTrue(
        create.indexOf("setupUIListeners()") < create.indexOf("searchFieldReady.set(searchField)"),
        create);
  }

  /** The text of the method that starts with the signature, through its closing brace. */
  private static String body(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, "missing " + signature);
    int open = source.indexOf('{', start);
    int depth = 0;
    for (int i = open; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(start, i + 1);
      }
    }
    throw new AssertionError("unbalanced " + signature);
  }
}
