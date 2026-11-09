/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pasting into FrostWire's text fields reads the clipboard off the EDT. Swing's paste read it on
 * the EDT, and on Windows that waits for the app that copied the text: Ctrl+V in the search box
 * froze FrostWire (frostwire/frostwire#1517).
 */
class AsyncPasteTest {
  private final Supplier<Clipboard> realClipboard = AsyncPaste.clipboard;
  private final Executor realBackground = AsyncPaste.background;
  private final Consumer<Runnable> realEdt = AsyncPaste.edt;

  @AfterEach
  void restore() {
    AsyncPaste.clipboard = realClipboard;
    AsyncPaste.background = realBackground;
    AsyncPaste.edt = realEdt;
  }

  /** A clipboard whose read blocks until released, like a slow Windows clipboard owner. */
  private static final class SlowClipboard extends Clipboard {
    final CountDownLatch release = new CountDownLatch(1);

    SlowClipboard(String text) {
      super("slow");
      setContents(new StringSelection(text), null);
    }

    @Override
    public Object getData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      try {
        assertTrue(release.await(10, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
      return super.getData(flavor);
    }
  }

  @Test
  void pasteReturnsBeforeTheClipboardAnswersAndInsertsTheTextLater() throws Exception {
    SlowClipboard slow = new SlowClipboard("ubuntu iso");
    CountDownLatch inserted = new CountDownLatch(1);
    AsyncPaste.clipboard = () -> slow;
    AsyncPaste.background = Executors.newSingleThreadExecutor();
    AsyncPaste.edt =
        r -> {
          r.run();
          inserted.countDown();
        };
    JTextField field = new JTextField("find: ");
    field.setCaretPosition(field.getText().length());

    long start = System.nanoTime();
    AsyncPaste.paste(field);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertTrue(elapsedMs < 1000, "paste waited " + elapsedMs + " ms on the clipboard");
    assertEquals("find: ", field.getText(), "nothing inserted before the clipboard answers");

    slow.release.countDown();
    assertTrue(inserted.await(10, TimeUnit.SECONDS));
    assertEquals("find: ubuntu iso", field.getText());
  }

  @Test
  void aReadOnlyFieldGetsNothing() {
    AsyncPaste.clipboard = () -> new SlowClipboard("x");
    AsyncPaste.background =
        r -> {
          throw new AssertionError("a read-only field must not read the clipboard");
        };
    JTextField field = new JTextField("fixed");
    field.setEditable(false);
    AsyncPaste.paste(field);
    assertEquals("fixed", field.getText());
  }

  @Test
  void aClipboardWithoutTextInsertsNothing() {
    Clipboard empty = new Clipboard("empty");
    assertEquals(null, AsyncPaste.textOf(empty));
  }

  @Test
  void frostWireTextFieldsPasteThisWay() throws Exception {
    for (String path :
        new String[] {
          "src/main/java/com/frostwire/gui/searchfield/JXTextField.java",
          "src/main/java/com/limegroup/gnutella/gui/LimeTextField.java"
        }) {
      Path source = Path.of(path);
      if (!Files.isRegularFile(source)) {
        source = Path.of("desktop").resolve(source);
      }
      String code =
          new String(Files.readAllBytes(source), StandardCharsets.UTF_8).replaceAll("\\s+", "");
      assertTrue(
          code.contains(
              "publicvoidpaste(){com.limegroup.gnutella.gui.util.AsyncPaste.paste(this);}"),
          path + " must paste through AsyncPaste");
    }
  }
}
