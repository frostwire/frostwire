/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.settings.SearchSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Search results switched the media type tab by themselves (to Video, typically) even right after
 * the user clicked another tab, and that switch was saved as the user's choice. Only a click may
 * change the tab a search shows and the one the next search opens on; until the first click it is
 * Torrents.
 */
class SearchMediaTypeTabTest {

  private static final NamedMediaType TORRENTS =
      NamedMediaType.getFromMediaType(MediaType.getTorrentMediaType());
  private static final NamedMediaType VIDEO =
      NamedMediaType.getFromMediaType(MediaType.getVideoMediaType());
  private static final NamedMediaType AUDIO =
      NamedMediaType.getFromMediaType(MediaType.getAudioMediaType());

  @AfterEach
  void restoreSetting() {
    SearchSettings.LAST_MEDIA_TYPE_CLICKED.revertToDefault();
  }

  @Test
  void aSearchOpensOnTorrentsUntilATabIsClicked() throws Exception {
    SearchSettings.LAST_MEDIA_TYPE_CLICKED.revertToDefault();
    assertTrue(newBox().buttonFor(TORRENTS).isSelected());
  }

  @Test
  void theNextSearchOpensOnTheLastTabClicked() throws Exception {
    SchemaBox box = newBox();
    SwingUtilities.invokeAndWait(() -> box.buttonFor(AUDIO).doClick());
    assertEquals(
        MediaType.getAudioMediaType().getMimeType(),
        SearchSettings.LAST_MEDIA_TYPE_CLICKED.getValue());
    assertTrue(newBox().buttonFor(AUDIO).isSelected());
  }

  @Test
  void selectingATabFromCodeIsNotRememberedAsTheUsersChoice() throws Exception {
    SchemaBox box = newBox();
    SwingUtilities.invokeAndWait(() -> box.buttonFor(AUDIO).doClick());
    box.selectMediaType(VIDEO); // e.g. a YouTube URL search, which shows only audio and video
    SwingUtilities.invokeAndWait(() -> {}); // runs the deferred selection
    assertTrue(box.buttonFor(VIDEO).isSelected());
    assertEquals(
        MediaType.getAudioMediaType().getMimeType(),
        SearchSettings.LAST_MEDIA_TYPE_CLICKED.getValue());
    assertTrue(newBox().buttonFor(AUDIO).isSelected());
  }

  @Test
  void anUnknownRememberedValueFallsBackToTorrents() throws Exception {
    SearchSettings.LAST_MEDIA_TYPE_CLICKED.setValue("no/such-type");
    assertTrue(newBox().buttonFor(TORRENTS).isSelected());
  }

  @Test
  void incomingResultsDoNotSwitchTheTab() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/limegroup/gnutella/gui/search/SearchMediator.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    int start = source.indexOf("privatevoidonResults(");
    int end = source.indexOf("privatevoidonFinished(");
    assertTrue(start >= 0 && end > start);
    assertFalse(
        source.substring(start, end).contains("selectMediaType("),
        "onResults must not change the media type tab");
  }

  private static SchemaBox newBox() throws Exception {
    AtomicReference<SchemaBox> box = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> box.set(new SchemaBox(null)));
    return box.get();
  }
}
