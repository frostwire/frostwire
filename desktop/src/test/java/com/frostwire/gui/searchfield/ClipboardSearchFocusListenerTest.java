/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.searchfield;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.FocusEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ClipboardSearchFocusListenerTest {
  @Test
  void searchFieldInstallsTheBackgroundClipboardListener() {
    assertDoesNotThrow(
        () -> {
          // GoogleSearchField initializes ThemeMediator, which requires a real display.
          // Check its wiring here; the listener behavior is exercised with real Swing below.
          // Whitespace-insensitive: the formatter may wrap these calls across lines.
          String source =
              Files.readString(
                      Path.of("src/main/java/com/frostwire/gui/searchfield/GoogleSearchField.java"))
                  .replaceAll("\\s+", "");
          assertTrue(source.contains("addFocusListener(newClipboardSearchFocusListener("));
          assertTrue(source.contains("DesktopParallelExecutor::execute"));
          assertFalse(source.contains("publicvoidfocusGained("));
        });
  }

  @Test
  void blockedClipboardDoesNotBlockFocusOrEdtAndDeliversOnEdt() {
    assertDoesNotThrow(
        () -> {
          var worker = Executors.newSingleThreadExecutor();
          CountDownLatch entered = new CountDownLatch(1);
          CountDownLatch release = new CountDownLatch(1);
          CountDownLatch delivered = new CountDownLatch(1);
          AtomicBoolean readOnEdt = new AtomicBoolean(true);
          AtomicBoolean deliveredOnEdt = new AtomicBoolean();
          List<String> results = new ArrayList<>();
          try {
            SwingUtilities.invokeAndWait(
                () -> {
                  JTextField field = new JTextField();
                  var listener =
                      new ClipboardSearchFocusListener(
                          field,
                          () -> true,
                          () -> {
                            readOnEdt.set(SwingUtilities.isEventDispatchThread());
                            entered.countDown();
                            assertDoesNotThrow(
                                () -> assertTrue(release.await(5, TimeUnit.SECONDS)));
                            return "magnet:?xt=urn:btih:example";
                          },
                          text -> {
                            deliveredOnEdt.set(SwingUtilities.isEventDispatchThread());
                            results.add(text);
                            delivered.countDown();
                          },
                          worker);
                  listener.focusGained(new FocusEvent(field, FocusEvent.FOCUS_GAINED));
                });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {}); // EDT remains responsive while clipboard waits.
            assertFalse(readOnEdt.get());
            release.countDown();
            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            assertTrue(deliveredOnEdt.get());
            assertEquals(List.of("magnet:?xt=urn:btih:example"), results);
          } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
          }
        });
  }

  @Test
  void focusLossOrTypingInvalidatesDelayedClipboardResults() {
    assertDoesNotThrow(
        () -> {
          List<Runnable> work = new ArrayList<>();
          List<String> results = new ArrayList<>();
          SwingUtilities.invokeAndWait(
              () -> {
                JTextField field = new JTextField();
                var listener =
                    new ClipboardSearchFocusListener(
                        field, () -> true, () -> "https://example.org", results::add, work::add);
                listener.focusGained(new FocusEvent(field, FocusEvent.FOCUS_GAINED));
                listener.focusLost(new FocusEvent(field, FocusEvent.FOCUS_LOST));
              });
          work.remove(0).run();
          SwingUtilities.invokeAndWait(() -> assertTrue(results.isEmpty()));
          SwingUtilities.invokeAndWait(
              () -> {
                JTextField field = new JTextField();
                var listener =
                    new ClipboardSearchFocusListener(
                        field, () -> true, () -> "https://example.org", results::add, work::add);
                listener.focusGained(new FocusEvent(field, FocusEvent.FOCUS_GAINED));
                field.setText("user query");
                field.setText(""); // Even edits that restore the original text invalidate the read.
              });
          work.remove(0).run();
          SwingUtilities.invokeAndWait(() -> assertTrue(results.isEmpty()));
        });
  }

  @Test
  void disabledSettingAndBusyWorkerDoNotAccumulateReads() {
    assertDoesNotThrow(
        () -> {
          List<Runnable> work = new ArrayList<>();
          List<String> results = new ArrayList<>();
          AtomicBoolean enabled = new AtomicBoolean(false);
          SwingUtilities.invokeAndWait(
              () -> {
                JTextField field = new JTextField();
                var listener =
                    new ClipboardSearchFocusListener(
                        field, enabled::get, () -> "https://example.org", results::add, work::add);
                FocusEvent gained = new FocusEvent(field, FocusEvent.FOCUS_GAINED);
                listener.focusGained(gained);
                assertTrue(work.isEmpty());
                enabled.set(true);
                listener.focusGained(gained);
                for (int i = 0; i < 100; i++) {
                  listener.focusGained(gained);
                }
                assertEquals(1, work.size());
                enabled.set(false);
              });
          work.remove(0).run();
          SwingUtilities.invokeAndWait(() -> assertTrue(results.isEmpty()));
        });
  }

  @Test
  void rejectedSubmissionDoesNotEscapeFocusHandlerAndCanRetry() {
    assertDoesNotThrow(
        () -> {
          AtomicInteger attempts = new AtomicInteger();
          List<Runnable> work = new ArrayList<>();
          SwingUtilities.invokeAndWait(
              () -> {
                JTextField field = new JTextField();
                var listener =
                    new ClipboardSearchFocusListener(
                        field,
                        () -> true,
                        () -> "",
                        text -> {},
                        task -> {
                          if (attempts.getAndIncrement() == 0) {
                            throw new RejectedExecutionException("full");
                          }
                          work.add(task);
                        });
                FocusEvent gained = new FocusEvent(field, FocusEvent.FOCUS_GAINED);
                listener.focusGained(gained);
                listener.focusGained(gained);
                assertEquals(2, attempts.get());
                assertEquals(1, work.size());
              });
          work.remove(0).run();
          SwingUtilities.invokeAndWait(() -> {});
        });
  }

  @Test
  void clipboardFailureReleasesTheGateForTheNextFocus() {
    assertDoesNotThrow(
        () -> {
          List<Runnable> work = new ArrayList<>();
          List<String> results = new ArrayList<>();
          AtomicInteger reads = new AtomicInteger();
          ClipboardSearchFocusListener[] listener = new ClipboardSearchFocusListener[1];
          JTextField[] field = new JTextField[1];
          SwingUtilities.invokeAndWait(
              () -> {
                field[0] = new JTextField();
                listener[0] =
                    new ClipboardSearchFocusListener(
                        field[0],
                        () -> true,
                        () -> {
                          if (reads.getAndIncrement() == 0) {
                            throw new IllegalStateException("clipboard unavailable");
                          }
                          return "https://example.org";
                        },
                        results::add,
                        work::add);
                listener[0].focusGained(new FocusEvent(field[0], FocusEvent.FOCUS_GAINED));
              });
          work.remove(0).run();
          SwingUtilities.invokeAndWait(
              () -> {
                assertTrue(results.isEmpty());
                listener[0].focusGained(new FocusEvent(field[0], FocusEvent.FOCUS_GAINED));
              });
          work.remove(0).run();
          SwingUtilities.invokeAndWait(() -> assertEquals(List.of("https://example.org"), results));
        });
  }
}
