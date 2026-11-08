/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.searchfield;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The search field's popup decides whether to offer Paste without asking the system clipboard.
 * Opening the popup runs on the EDT, and on Windows the clipboard's lock can be held for seconds by
 * a background read: the search field reads the clipboard when it gains focus, which the same
 * right-click does (frostwire/frostwire#1508).
 *
 * <p>The field installs ThemeMediator key strokes in its constructor, which needs a display, so
 * this checks the source, as {@code ClipboardSearchFocusListenerTest} does for GoogleSearchField.
 * Whitespace-insensitive: the formatter may wrap these calls across lines.
 */
class PastePopupClipboardTest {
  @Test
  void theSearchFieldPopupDoesNotAskTheClipboard() throws IOException {
    String source = source("src/main/java/com/frostwire/gui/searchfield/SearchField.java");
    assertTrue(
        source.contains("PASTE_ACTION.setEnabled(isEnabled()&&isEditable()&&isPasteAvailable());"));
    assertTrue(
        source.contains(
            "privatebooleanisPasteAvailable(){returnSearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue();}"));
    assertNoClipboardQuery(source);
  }

  private static void assertNoClipboardQuery(String source) {
    assertFalse(source.contains("getSystemClipboard("));
    assertFalse(source.contains("isDataFlavorAvailable("));
    assertFalse(source.contains("getAvailableDataFlavors("));
    assertFalse(source.contains(".getContents("));
  }

  private static String source(String path) throws IOException {
    return Files.readString(Path.of(path)).replaceAll("\\s+", "");
  }
}
