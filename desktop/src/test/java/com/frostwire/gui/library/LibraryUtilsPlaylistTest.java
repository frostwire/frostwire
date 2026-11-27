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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.gui.library.tags.TagsReader;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The library's small helpers: durations, refresh keys, M3U playlists, lyrics callbacks. */
class LibraryUtilsPlaylistTest {

  @TempDir Path tmp;

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  @Test
  void durationsAreShownAsMinutesSecondsHoursOrDays() {
    assertEquals("00:00", LibraryUtils.getSecondsInDDHHMMSS(0));
    assertEquals("00:00", LibraryUtils.getSecondsInDDHHMMSS(-5));
    assertEquals("00:09", LibraryUtils.getSecondsInDDHHMMSS(9));
    assertEquals("01:05", LibraryUtils.getSecondsInDDHHMMSS(65));
    assertEquals("59:59", LibraryUtils.getSecondsInDDHHMMSS(3599));
    assertEquals("01:00:00", LibraryUtils.getSecondsInDDHHMMSS(3600));
    assertEquals("23:59:59", LibraryUtils.getSecondsInDDHHMMSS(86399));
    assertEquals("1 day", LibraryUtils.getSecondsInDDHHMMSS(86400));
    assertEquals("1 day", LibraryUtils.getSecondsInDDHHMMSS(86400 + 7200));
    assertEquals("3 days", LibraryUtils.getSecondsInDDHHMMSS(3 * 86400 + 5));
  }

  private static KeyEvent key(int code, int modifiers) {
    Component source = new JPanel();
    return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, modifiers, code, KeyEvent.CHAR_UNDEFINED);
  }

  @Test
  void f5OrCtrlOrCommandROrAltGraphRRefreshes() {
    assertTrue(LibraryUtils.isRefreshKeyEvent(key(KeyEvent.VK_F5, 0)));
    assertTrue(LibraryUtils.isRefreshKeyEvent(key(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK)));
    assertTrue(LibraryUtils.isRefreshKeyEvent(key(KeyEvent.VK_R, KeyEvent.META_DOWN_MASK)));
    assertTrue(LibraryUtils.isRefreshKeyEvent(key(KeyEvent.VK_R, KeyEvent.ALT_GRAPH_DOWN_MASK)));
    assertFalse(LibraryUtils.isRefreshKeyEvent(key(KeyEvent.VK_R, 0)));
    assertFalse(LibraryUtils.isRefreshKeyEvent(key(KeyEvent.VK_F6, KeyEvent.CTRL_DOWN_MASK)));
  }

  @Test
  void aMagnetLinkIsBuiltFromAHash() {
    assertEquals("magnet:?xt=urn:btih:abc123", TorrentUtil.getMagnet("abc123"));
  }

  private File song(String relative) throws IOException {
    File f = tmp.resolve(relative).toFile();
    Files.createDirectories(f.getParentFile().toPath());
    Files.writeString(f.toPath(), "x");
    return f;
  }

  @Test
  void aSavedPlaylistLoadsBackWithTheSameFiles() throws Exception {
    File a = song("a.mp3");
    File b = song("sub/b.mp3");
    File playlist = tmp.resolve("mine.m3u").toFile();
    M3UPlaylist.save(playlist.getPath(), List.of(a, b, new File(tmp.toFile(), "missing.mp3")));
    List<String> lines = Files.readAllLines(playlist.toPath());
    assertEquals("#EXTM3U", lines.get(0));
    assertEquals("#EXTINF:-1,a.mp3", lines.get(1));
    assertEquals(5, lines.size(), "the file that doesn't exist isn't saved");
    List<File> loaded = M3UPlaylist.load(playlist.getPath());
    assertEquals(
        List.of(a.getCanonicalFile(), b.getCanonicalFile()),
        loaded.stream()
            .map(
                f -> {
                  try {
                    return f.getCanonicalFile();
                  } catch (IOException e) {
                    throw new AssertionError(e);
                  }
                })
            .toList());
  }

  @Test
  void savingAnEmptyListWritesNothing() throws Exception {
    File playlist = tmp.resolve("empty.m3u").toFile();
    M3UPlaylist.save(playlist.getPath(), List.of());
    assertFalse(playlist.exists());
  }

  @Test
  void relativeEntriesResolveAgainstThePlaylistFolderButCannotEscapeIt() throws Exception {
    song("music/inside.mp3");
    Path outside = Files.createTempFile("outside", ".mp3");
    try {
      File playlist = tmp.resolve("rel.m3u").toFile();
      Files.writeString(
          playlist.toPath(),
          "#EXTM3U\n#EXTINF:-1,x\nmusic/inside.mp3\n#EXTINF:-1,y\n../"
              + outside.getFileName()
              + "\n#EXTINF:-1,z\nnot-there.mp3\n");
      List<File> loaded = M3UPlaylist.load(playlist.getPath());
      assertEquals(1, loaded.size());
      assertTrue(loaded.get(0).getPath().endsWith("inside.mp3"));
    } finally {
      Files.deleteIfExists(outside);
    }
  }

  @Test
  void aPlaylistMayStartDirectlyWithAnEntry() throws Exception {
    File a = song("a.mp3");
    File playlist = tmp.resolve("noheader.m3u").toFile();
    Files.writeString(playlist.toPath(), "#EXTINF:-1,a\n" + a.getAbsolutePath() + "\n#EXTINF:1");
    assertEquals(List.of(a), M3UPlaylist.load(playlist.getPath()));
  }

  @Test
  void aFileThatIsNotAPlaylistIsRejected() throws Exception {
    File notAPlaylist = tmp.resolve("junk.m3u").toFile();
    Files.writeString(notAPlaylist.toPath(), "hello\nworld\n");
    assertThrows(IOException.class, () -> M3UPlaylist.load(notAPlaylist.getPath()));
    File empty = tmp.resolve("empty2.m3u").toFile();
    Files.writeString(empty.toPath(), "");
    assertThrows(IOException.class, () -> M3UPlaylist.load(empty.getPath()));
    assertThrows(IOException.class, () -> M3UPlaylist.load(tmp.resolve("nope.m3u").toString()));
  }

  @Test
  void lyricsOfAFileThatCannotBeReadAreEmptyAndTheCallbackStillRuns() throws Exception {
    AtomicInteger ran = new AtomicInteger();
    LibraryUtils.OnLyricsParsedUICallback callback =
        new LibraryUtils.OnLyricsParsedUICallback() {
          @Override
          public void run() {
            ran.incrementAndGet();
          }
        };
    LibraryUtils.asyncParseLyrics(new TagsReader(tmp.resolve("gone.mp3").toFile()), callback);
    SwingUtilities.invokeAndWait(() -> {});
    assertEquals(1, ran.get());
    assertEquals("", callback.getLyrics());
    // no callback: nothing to do, and nothing thrown
    LibraryUtils.asyncParseLyrics(new TagsReader(tmp.resolve("gone.mp3").toFile()), null);
    assertNotNull(LibraryUtils.getExecutor());
  }
}
