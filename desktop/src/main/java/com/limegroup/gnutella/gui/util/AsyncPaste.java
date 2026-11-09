/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

/**
 * Paste for FrostWire's text fields that reads the clipboard off the EDT.
 *
 * <p>Swing's paste reads the system clipboard on the EDT. On Windows, the app that copied the text
 * may render it only when asked ("delayed rendering"), so the read waits for that app; pressing
 * Ctrl+V in the search box froze FrostWire while it waited (Icebase #4a2769266e9a,
 * frostwire/frostwire#1517). Here the clipboard's text is read on {@link DesktopParallelExecutor}
 * and inserted on the EDT, replacing the field's selection then.
 */
public final class AsyncPaste {

  /** Where the text comes from; replaced in tests. */
  static volatile Supplier<Clipboard> clipboard =
      () -> Toolkit.getDefaultToolkit().getSystemClipboard();

  /** Where the clipboard is read; replaced in tests. */
  static volatile Executor background = DesktopParallelExecutor::execute;

  /** Where the text is inserted; replaced in tests. */
  static volatile Consumer<Runnable> edt = SwingUtilities::invokeLater;

  private AsyncPaste() {}

  /**
   * Pastes the clipboard's text into {@code field}, the way {@link JTextComponent#paste()} does,
   * without reading the clipboard on the calling thread. Text that isn't plain text is ignored, as
   * a text field can't take it.
   */
  public static void paste(JTextComponent field) {
    if (!field.isEditable() || !field.isEnabled()) {
      UIManager.getLookAndFeel().provideErrorFeedback(field);
      return;
    }
    Clipboard source;
    try {
      source = clipboard.get();
    } catch (RuntimeException e) {
      // HeadlessException, SecurityException: there is no clipboard to paste from.
      return;
    }
    background.execute(
        () -> {
          String text = textOf(source);
          if (text == null || text.isEmpty()) {
            return;
          }
          edt.accept(
              () -> {
                // The field may have been disabled or made read-only while the clipboard answered.
                if (field.isEditable() && field.isEnabled()) {
                  field.replaceSelection(text);
                }
              });
        });
  }

  /** The clipboard's plain text, or null when it holds none or can't be read now. */
  static String textOf(Clipboard source) {
    try {
      if (!source.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
        return null;
      }
      return (String) source.getData(DataFlavor.stringFlavor);
    } catch (IllegalStateException | UnsupportedFlavorException | IOException e) {
      // IllegalStateException: another app holds the clipboard open.
      return null;
    }
  }
}
