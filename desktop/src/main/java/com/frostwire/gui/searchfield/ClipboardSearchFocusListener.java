/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.searchfield;

import com.frostwire.util.Logger;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

/** EDT-owned focus listener; clipboard access alone runs on the supplied background executor. */
final class ClipboardSearchFocusListener extends FocusAdapter {
  private static final Logger LOG = Logger.getLogger(ClipboardSearchFocusListener.class);
  private final JTextField field;
  private final BooleanSupplier enabled;
  private final Supplier<String> clipboardReader;
  private final Consumer<String> onText;
  private final Executor worker;
  private long generation;
  private boolean reading;

  ClipboardSearchFocusListener(
      JTextField field,
      BooleanSupplier enabled,
      Supplier<String> clipboardReader,
      Consumer<String> onText,
      Executor worker) {
    this.field = field;
    this.enabled = enabled;
    this.clipboardReader = clipboardReader;
    this.onText = onText;
    this.worker = worker;
    field.getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent event) {
                generation++;
              }

              public void removeUpdate(DocumentEvent event) {
                generation++;
              }

              public void changedUpdate(DocumentEvent event) {
                generation++;
              }
            });
  }

  @Override
  public void focusGained(FocusEvent event) {
    long ticket = ++generation;
    // Skip optional polls while one is outstanding; never queue a burst of clipboard waits.
    if (!enabled.getAsBoolean() || reading) {
      return;
    }
    Document document = field.getDocument();
    reading = true;
    try {
      worker.execute(() -> readClipboard(ticket, document));
    } catch (RejectedExecutionException error) {
      reading = false;
      LOG.debug("Clipboard search skipped: background pool is busy");
    }
  }

  @Override
  public void focusLost(FocusEvent event) {
    generation++;
  }

  private void readClipboard(long ticket, Document document) {
    String text = null;
    try {
      text = clipboardReader.get();
    } catch (RuntimeException error) {
      LOG.debug("Unable to read clipboard for automatic search", error);
    } finally {
      String result = text;
      SwingUtilities.invokeLater(
          () -> {
            reading = false;
            if (ticket != generation || document != field.getDocument() || !enabled.getAsBoolean()) {
              return;
            }
            if (result != null && !result.isEmpty()) {
              onText.accept(result);
            }
          });
    }
  }
}
