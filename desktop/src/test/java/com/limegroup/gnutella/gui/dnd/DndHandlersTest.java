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

package com.limegroup.gnutella.gui.dnd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.OSUtils;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drag and drop plumbing: flavors, URI and file extraction, and the chained handlers. */
class DndHandlersTest {

  @TempDir Path tmp;

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  /** A transferable offering the given flavors, each answering with the given data. */
  private static Transferable offering(Object data, DataFlavor... flavors) {
    return new Transferable() {
      @Override
      public DataFlavor[] getTransferDataFlavors() {
        return flavors;
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DNDUtils.contains(flavors, flavor);
      }

      @Override
      public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
          throw new UnsupportedFlavorException(flavor);
        }
        return data;
      }
    };
  }

  private static String lines(String... uris) {
    return String.join(System.getProperty("line.separator"), uris);
  }

  @Test
  void flavorLookupFindsAFlavorInAnArray() {
    DataFlavor[] flavors = {DataFlavor.stringFlavor, DataFlavor.javaFileListFlavor};
    assertTrue(DNDUtils.contains(flavors, DataFlavor.javaFileListFlavor));
    assertFalse(DNDUtils.contains(flavors, FileTransferable.URIFlavor));
    assertFalse(DNDUtils.contains(new DataFlavor[0], DataFlavor.stringFlavor));
  }

  @Test
  void fileFlavorsAreRecognised() {
    assertFalse(DNDUtils.containsFileFlavors(null));
    assertFalse(DNDUtils.containsFileFlavors(new DataFlavor[] {DataFlavor.stringFlavor}));
    assertTrue(DNDUtils.containsFileFlavors(new DataFlavor[] {DataFlavor.javaFileListFlavor}));
    assertTrue(DNDUtils.containsFileFlavors(new DataFlavor[] {FileTransferable.URIFlavor}));
    assertTrue(DNDUtils.containsFileFlavors(new DataFlavor[] {FileTransferable.URIFlavor16}));
  }

  @Test
  void urisAreReadFromALineList() throws Exception {
    Transferable t =
        offering(
            lines(
                "http://a.test/x.torrent", "", "  file:///tmp/a.mp3  ", "file:///tmp/my song.mp3"),
            FileTransferable.URIFlavor);
    URI[] uris = DNDUtils.getURIs(t);
    assertEquals(3, uris.length);
    assertEquals("http://a.test/x.torrent", uris[0].toString());
    assertEquals("file:///tmp/a.mp3", uris[1].toString());
    assertEquals("/tmp/my song.mp3", uris[2].getPath(), "a space is not a plus");
  }

  @Test
  void theSixteenFlavorIsUsedWhenThatIsAllThereIs() throws Exception {
    URI[] uris = DNDUtils.getURIs(offering("file:///tmp/b.mp3", FileTransferable.URIFlavor16));
    assertEquals(1, uris.length);
  }

  @Test
  void filesComeFromAFileListOrFromFileUris() throws Exception {
    File a = tmp.resolve("a.mp3").toFile();
    File[] fromList = DNDUtils.getFiles(offering(List.of(a), DataFlavor.javaFileListFlavor));
    assertArrayEquals(new File[] {a}, fromList);

    Transferable uris =
        offering(
            lines(a.toURI().toString(), "http://a.test/not-local.mp3"), FileTransferable.URIFlavor);
    File[] fromUris = DNDUtils.getFiles(uris);
    assertEquals(1, fromUris.length);
    assertEquals(a.getAbsoluteFile(), fromUris[0].getAbsoluteFile());

    assertEquals(0, DNDUtils.getFiles(offering("text", DataFlavor.stringFlavor)).length);
  }

  private static TransferHandler.TransferSupport paste(Transferable t) {
    return new TransferHandler.TransferSupport(new JPanel(), t);
  }

  @Test
  void aPasteHasNoDropLocationAndHidingItIsHarmless() {
    TransferHandler.TransferSupport support = paste(new StringSelection("x"));
    assertNull(DNDUtils.dropLocationOrNull(support));
    DNDUtils.hideDropLocation(support);
  }

  @Test
  void importIsAcceptedWhenTheHandlersOwnFlavorIsThere() {
    TransferHandler.TransferSupport support = paste(new StringSelection("x"));
    assertTrue(DNDUtils.supportCanImport(DataFlavor.stringFlavor, support, null, false));
  }

  @Test
  void importIsRefusedForFlavorsNoOneHandles() {
    TransferHandler.TransferSupport support =
        paste(offering(new Object(), new DataFlavor(Object.class, "thing")));
    assertFalse(DNDUtils.supportCanImport(DataFlavor.stringFlavor, support, null, false));
  }

  @Test
  void filesAreImportedWhenTheyArePlayableOrAPlaylistOrTheFallbackTakesThem() throws Exception {
    File song = tmp.resolve("song.mp3").toFile();
    File doc = tmp.resolve("notes.txt").toFile();
    File playlist = tmp.resolve("list.m3u").toFile();
    DataFlavor files = DataFlavor.javaFileListFlavor;
    boolean mac = OSUtils.isMacOSX();
    assertTrue(
        DNDUtils.supportCanImport(
            DataFlavor.stringFlavor, paste(offering(List.of(song), files)), null, false));
    assertEquals(
        mac,
        DNDUtils.supportCanImport(
            DataFlavor.stringFlavor, paste(offering(List.of(doc), files)), null, false));
    assertTrue(
        DNDUtils.supportCanImport(
            DataFlavor.stringFlavor, paste(offering(List.of(playlist), files)), null, false));

    TransferHandler acceptAll =
        new TransferHandler() {
          @Override
          public boolean canImport(TransferSupport support) {
            return true;
          }
        };
    assertTrue(
        DNDUtils.supportCanImport(
            DataFlavor.stringFlavor, paste(offering(List.of(doc), files)), acceptAll, true));
    assertEquals(
        mac,
        DNDUtils.supportCanImport(
            DataFlavor.stringFlavor, paste(offering(List.of(doc), files)), acceptAll, false));
  }

  @Test
  void fileTransferableOffersFileListAndUriListAndTheirData() throws Exception {
    File a = tmp.resolve("a.mp3").toFile();
    File b = tmp.resolve("b.mp3").toFile();
    FileTransferable t = new FileTransferable(List.of(a, b));
    DataFlavor[] flavors = t.getTransferDataFlavors();
    assertTrue(DNDUtils.contains(flavors, DataFlavor.javaFileListFlavor));
    assertEquals(!OSUtils.isWindows(), DNDUtils.contains(flavors, FileTransferable.URIFlavor));
    assertFalse(DNDUtils.contains(flavors, FileTransferable.LISTED_ON_DROP_FLAVOR));
    assertEquals(List.of(a, b), t.getTransferData(DataFlavor.javaFileListFlavor));
    String uris = (String) t.getTransferData(FileTransferable.URIFlavor);
    assertTrue(uris.startsWith(a.toURI().toString()));
    assertTrue(uris.endsWith(b.toURI().toString()));
    assertEquals(uris, t.getTransferData(FileTransferable.URIFlavor16));
    assertTrue(t.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
    assertFalse(t.isDataFlavorSupported(DataFlavor.stringFlavor));
    assertFalse(t.isDataFlavorSupported(FileTransferable.LISTED_ON_DROP_FLAVOR));
    assertThrows(
        UnsupportedFlavorException.class, () -> t.getTransferData(DataFlavor.stringFlavor));
    assertThrows(
        UnsupportedFlavorException.class,
        () -> t.getTransferData(FileTransferable.LISTED_ON_DROP_FLAVOR));
  }

  @Test
  void fileTransferableCopiesItsListAndRefusesNull() {
    List<File> files = new ArrayList<>(List.of(new File("x")));
    FileTransferable t = new FileTransferable(files);
    files.add(new File("y"));
    assertEquals(1, t.filesForDragImage().size());
    assertThrows(NullPointerException.class, () -> new FileTransferable((List<File>) null));
    assertThrows(NullPointerException.class, () -> new FileTransferable(List.of(), null));
  }

  @Test
  void filesListedOnDropAreListedOnceAndOnlyWhenAsked() throws Exception {
    int[] listed = {0};
    File a = tmp.resolve("a.mp3").toFile();
    File preview = tmp.resolve("preview.mp3").toFile();
    FileTransferable t =
        FileTransferable.listedOnDrop(
            () -> {
              listed[0]++;
              return List.of(a);
            },
            () -> List.of(preview));
    assertTrue(
        DNDUtils.contains(t.getTransferDataFlavors(), FileTransferable.LISTED_ON_DROP_FLAVOR));
    assertEquals(Boolean.TRUE, t.getTransferData(FileTransferable.LISTED_ON_DROP_FLAVOR));
    assertEquals(List.of(preview), t.filesForDragImage());
    assertEquals(0, listed[0], "drawing the drag image doesn't list the files");
    assertEquals(List.of(a), t.getTransferData(DataFlavor.javaFileListFlavor));
    assertEquals(List.of(a), t.getTransferData(DataFlavor.javaFileListFlavor));
    assertEquals(1, listed[0]);
    assertEquals(List.of(a), t.filesForDragImage());
  }

  @Test
  void lazyFilesAreResolvedWhenTheDataIsRequested() throws Exception {
    File a = tmp.resolve("a.mp3").toFile();
    FileTransfer present = () -> a;
    FileTransfer absent = () -> null;
    FileTransferable t = new FileTransferable(List.of(), List.of(present, absent));
    assertEquals(List.of(a), t.getTransferData(DataFlavor.javaFileListFlavor));
  }

  @Test
  void magnetAndTorrentUriHandlersOnlyTakeTheUriListFlavor() {
    JComponent c = new JPanel();
    DataFlavor[] uri = {FileTransferable.URIFlavor};
    DataFlavor[] other = {DataFlavor.stringFlavor};
    for (LimeTransferHandler h :
        new LimeTransferHandler[] {new MagnetTransferHandler(), new TorrentURITransferHandler()}) {
      assertTrue(h.canImport(c, uri));
      assertTrue(h.canImport(c, uri, null));
      assertFalse(h.canImport(c, other));
      assertFalse(h.importData(c, new StringSelection("x")));
      assertFalse(h.importData(c, new StringSelection("x"), null));
    }
  }

  @Test
  void aMagnetDropWithoutAMagnetIsNotImported() {
    JComponent c = new JPanel();
    assertFalse(
        new MagnetTransferHandler()
            .importData(c, offering("just some text", FileTransferable.URIFlavor)));
  }

  @Test
  void aUriDropThatIsNotAHttpTorrentIsNotImported() {
    JComponent c = new JPanel();
    TorrentURITransferHandler h = new TorrentURITransferHandler();
    for (String dropped :
        new String[] {
          "ftp://a.test/x.torrent",
          "http://a.test/x.mp3",
          "http://a.test/x.torrent\nhttp://a.test/y.mp3".replace("\n", System.lineSeparator()),
          "relative/x.torrent"
        }) {
      assertFalse(h.importData(c, offering(dropped, FileTransferable.URIFlavor)), dropped);
    }
  }

  @Test
  void httpAndHttpsTorrentUrlsAreBothRecognised() throws Exception {
    java.lang.reflect.Method m =
        TorrentURITransferHandler.class.getDeclaredMethod("areAllTorrentURLs", URI[].class);
    m.setAccessible(true);
    TorrentURITransferHandler h = new TorrentURITransferHandler();
    assertTrue((boolean) m.invoke(h, (Object) new URI[] {URI.create("http://a.test/x.torrent")}));
    assertTrue((boolean) m.invoke(h, (Object) new URI[] {URI.create("HTTPS://a.test/x.TORRENT")}));
    assertFalse((boolean) m.invoke(h, (Object) new URI[] {URI.create("https://a.test/x.mp3")}));
    assertFalse((boolean) m.invoke(h, (Object) new URI[] {URI.create("ftp://a.test/x.torrent")}));
  }

  @Test
  void torrentFileHandlerTakesFileListsAndUriListsAndOnlyImportsTorrents() throws Exception {
    JComponent c = new JPanel();
    TorrentFilesTransferHandler h = new TorrentFilesTransferHandler();
    assertTrue(h.canImport(c, new DataFlavor[] {DataFlavor.javaFileListFlavor}));
    assertTrue(h.canImport(c, new DataFlavor[] {FileTransferable.URIFlavor}, null));
    assertFalse(h.canImport(c, new DataFlavor[] {DataFlavor.stringFlavor}));
    File song = tmp.resolve("song.mp3").toFile();
    Files.writeString(song.toPath(), "x");
    File missingTorrent = tmp.resolve("gone.torrent").toFile();
    assertFalse(h.importData(c, offering(List.of(song), DataFlavor.javaFileListFlavor)));
    assertFalse(h.importData(c, offering(List.of(missingTorrent), DataFlavor.javaFileListFlavor)));
    assertFalse(h.importData(c, new StringSelection("x")));
  }

  @Test
  void theMulticastHandlerAsksItsHandlersInOrderUntilOneSaysYes() {
    List<String> asked = new ArrayList<>();
    LimeTransferHandler no = recording("no", asked, false, TransferHandler.NONE);
    LimeTransferHandler yes = recording("yes", asked, true, TransferHandler.COPY);
    LimeTransferHandler late = recording("late", asked, true, TransferHandler.MOVE);
    MulticastTransferHandler multi = new MulticastTransferHandler(yes, List.of(late));
    MulticastTransferHandler multiWithNo = new MulticastTransferHandler(no, List.of(yes, late));
    JComponent c = new JPanel();
    DataFlavor[] flavors = {DataFlavor.stringFlavor};

    assertTrue(multiWithNo.canImport(c, flavors));
    assertEquals(List.of("no.canImport", "yes.canImport"), asked);
    asked.clear();
    assertTrue(multiWithNo.canImport(c, flavors, null));
    assertEquals(List.of("no.canImport3", "yes.canImport3"), asked);
    asked.clear();
    assertTrue(multiWithNo.importData(c, new StringSelection("x")));
    assertEquals(List.of("no.importData", "yes.importData"), asked);
    asked.clear();
    assertTrue(multiWithNo.importData(c, new StringSelection("x"), null));
    assertEquals(List.of("no.importData3", "yes.importData3"), asked);
    assertEquals(TransferHandler.COPY | TransferHandler.MOVE, multiWithNo.getSourceActions(c));
    assertEquals(TransferHandler.COPY | TransferHandler.MOVE, multi.getSourceActions(c));

    MulticastTransferHandler none = new MulticastTransferHandler();
    assertFalse(none.canImport(c, flavors));
    assertFalse(none.canImport(c, flavors, null));
    assertFalse(none.importData(c, new StringSelection("x")));
    assertFalse(none.importData(c, new StringSelection("x"), null));
    assertEquals(TransferHandler.NONE, none.getSourceActions(c));
  }

  @Test
  void handlersCanBeAddedAndRemovedFromAMulticastHandler() {
    List<String> asked = new ArrayList<>();
    LimeTransferHandler yes = recording("yes", asked, true, TransferHandler.COPY);
    MulticastTransferHandler multi = new MulticastTransferHandler();
    JComponent c = new JPanel();
    DataFlavor[] flavors = {DataFlavor.stringFlavor};
    multi.addTransferHandler(yes);
    assertTrue(multi.canImport(c, flavors));
    multi.removeTransferHandler(yes);
    assertFalse(multi.canImport(c, flavors));
    assertTrue(new MulticastTransferHandler(yes).canImport(c, flavors));
  }

  private static LimeTransferHandler recording(
      String name, List<String> asked, boolean accepts, int actions) {
    return new LimeTransferHandler(actions) {
      @Override
      public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        asked.add(name + ".canImport");
        return accepts;
      }

      @Override
      public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
        asked.add(name + ".canImport3");
        return accepts;
      }

      @Override
      public boolean importData(JComponent comp, Transferable t) {
        asked.add(name + ".importData");
        return accepts;
      }

      @Override
      public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
        asked.add(name + ".importData3");
        return accepts;
      }
    };
  }

  @Test
  void theDefaultHandlerChainsTheFourFrostWireHandlers() {
    assertEquals(4, DNDUtils.DEFAULT_TRANSFER_HANDLERS.size());
    assertNotNull(DNDUtils.DEFAULT_TRANSFER_HANDLER);
    assertTrue(
        DNDUtils.DEFAULT_TRANSFER_HANDLER.canImport(
            new JPanel(), new DataFlavor[] {FileTransferable.URIFlavor}));
    assertFalse(
        DNDUtils.DEFAULT_TRANSFER_HANDLER.canImport(
            new JPanel(), new DataFlavor[] {DataFlavor.stringFlavor}));
  }

  @SuppressWarnings("deprecation")
  @Test
  void aLimeHandlerDoesNothingByDefaultAndHasNoVisualRepresentation() {
    LimeTransferHandler h = new LimeTransferHandler() {};
    assertFalse(h.canImport(new JPanel(), new DataFlavor[0]));
    assertFalse(h.importData(new JPanel(), new StringSelection("x")));
    assertEquals(TransferHandler.NONE, h.getSourceActions(new JPanel()));
    assertThrows(IllegalStateException.class, () -> h.getVisualRepresentation(null));
    assertSame(h, h);
  }
}
