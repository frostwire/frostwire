/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Folder picks on Windows use the native folder dialog, shown off the EDT, instead of a
 * JFileChooser, which asks the Windows shell about folders on the EDT (Icebase #45ae6d65f303,
 * #fbbcd419bafa, frostwire/frostwire#1564). The real COM dialog can't run here; a fake picker
 * stands in for it.
 */
class NativeFolderChooserTest {
  private static final File FOLDER = new File("chosen-folder");

  private final ExecutorService worker = Executors.newSingleThreadExecutor();

  @AfterEach
  void stopWorker() {
    worker.shutdownNow();
  }

  /** A picker whose dialog returns {@code result}, counting how often the factory built one. */
  private static Supplier<NativeFolderChooser.Picker> pickers(
      AtomicInteger built, Callable<File> dialog) {
    return () -> {
      built.incrementAndGet();
      return (NativeFolderChooser.Picker)
          Proxy.newProxyInstance(
              NativeFolderChooser.Picker.class.getClassLoader(),
              new Class<?>[] {NativeFolderChooser.Picker.class},
              (proxy, method, args) -> {
                if (method.getName().equals("prepare")) {
                  return dialog;
                }
                return method.invoke(dialog, args);
              });
    };
  }

  private NativeFolderChooser chooser(boolean windows, Supplier<NativeFolderChooser.Picker> p) {
    return new NativeFolderChooser(() -> windows, p, () -> worker, () -> null);
  }

  @Test
  void windowsFolderPicksUseTheNativeDialog() {
    AtomicInteger built = new AtomicInteger();
    NativeFolderChooser chooser = chooser(true, pickers(built, () -> FOLDER));

    NativeFolderChooser.Choice choice = chooser.choose(null, "Select Folder", null);

    assertNotNull(choice);
    assertEquals(FOLDER, choice.folder());
  }

  @Test
  void cancellingTheNativeDialogPicksNothing() {
    NativeFolderChooser chooser = chooser(true, pickers(new AtomicInteger(), () -> null));

    NativeFolderChooser.Choice choice = chooser.choose(null, "Select Folder", null);

    assertNotNull(choice, "a cancelled native dialog must not open a JFileChooser too");
    assertNull(choice.folder());
  }

  @Test
  void otherSystemsKeepTheirChooser() {
    AtomicInteger built = new AtomicInteger();
    NativeFolderChooser chooser = chooser(false, pickers(built, () -> FOLDER));

    assertNull(chooser.choose(null, "Select Folder", null));
    assertEquals(0, built.get());
  }

  @Test
  void missingJnaFallsBackToTheJFileChooserForGood() {
    AtomicInteger attempts = new AtomicInteger();
    NativeFolderChooser chooser =
        chooser(
            true,
            () -> {
              attempts.incrementAndGet();
              throw new UnsatisfiedLinkError("jnidispatch");
            });

    assertNull(chooser.choose(null, "Select Folder", null));
    assertNull(chooser.choose(null, "Select Folder", null));
    assertEquals(1, attempts.get(), "a failed native dialog is not retried on every pick");
  }

  @Test
  void aComErrorFallsBackToTheJFileChooserForGood() {
    AtomicInteger built = new AtomicInteger();
    AtomicInteger shown = new AtomicInteger();
    NativeFolderChooser chooser =
        chooser(
            true,
            pickers(
                built,
                () -> {
                  shown.incrementAndGet();
                  throw new IOException("CoCreateInstance(FileOpenDialog) failed");
                }));

    assertNull(chooser.choose(null, "Select Folder", null));
    assertNull(chooser.choose(null, "Select Folder", null));
    assertEquals(1, shown.get());
  }

  /**
   * The dialog is shown on the picker's thread, and the EDT keeps dispatching events while it is
   * open: the fake dialog stays open until the EDT has run an event posted after it opened.
   */
  @Test
  void theDialogRunsOffTheEdtWhileTheEdtKeepsDispatching() throws Exception {
    AtomicReference<Thread> edt = new AtomicReference<>();
    AtomicReference<Thread> preparedOn = new AtomicReference<>();
    AtomicReference<Thread> shownOn = new AtomicReference<>();
    AtomicBoolean edtDispatchedWhileOpen = new AtomicBoolean();
    Callable<File> dialog =
        () -> {
          shownOn.set(Thread.currentThread());
          CountDownLatch dispatched = new CountDownLatch(1);
          EventQueue.invokeLater(dispatched::countDown);
          edtDispatchedWhileOpen.set(dispatched.await(10, TimeUnit.SECONDS));
          return FOLDER;
        };
    NativeFolderChooser.Picker picker =
        new NativeFolderChooser.Picker() {
          @Override
          public Callable<File> prepare(Window owner, String title, File initialDirectory) {
            preparedOn.set(Thread.currentThread());
            return dialog;
          }
        };
    NativeFolderChooser chooser = chooser(true, () -> picker);
    AtomicReference<NativeFolderChooser.Choice> choice = new AtomicReference<>();

    EventQueue.invokeAndWait(
        () -> {
          edt.set(Thread.currentThread());
          choice.set(chooser.choose(null, "Select Folder", null));
        });

    assertSame(edt.get(), preparedOn.get(), "the owner window is read on the EDT");
    assertNotNull(shownOn.get());
    assertNotSame(edt.get(), shownOn.get(), "the native dialog must not run on the EDT");
    assertTrue(edtDispatchedWhileOpen.get(), "the EDT was blocked while the dialog was open");
    assertEquals(FOLDER, choice.get().folder());
  }

  /** Folder picks go through FileChooserHandler, which asks for the native dialog first. */
  @Test
  void folderPicksGoThroughTheNativeDialogFirst() throws IOException {
    String handler =
        Files.readString(
            Path.of("src/main/java/com/limegroup/gnutella/gui/FileChooserHandler.java"),
            StandardCharsets.UTF_8);
    int getInput = handler.indexOf("private static List<File> getInput(");
    int nativeDialog = handler.indexOf("NativeFolderChooser.instance()", getInput);
    int jFileChooser = handler.indexOf("getDirectoryChooser(titleKey, approveKey", getInput);
    assertTrue(getInput > 0 && nativeDialog > getInput, "getInput doesn't ask for the native one");
    assertTrue(nativeDialog < jFileChooser, "getInput builds a JFileChooser before the native one");
  }

  /**
   * Besides FileChooserHandler, only SendFileProgressDialog shows a JFileChooser: it picks a file
   * or a folder in one dialog, which Windows' dialogs can't.
   */
  @Test
  void choosersAreShownByFileChooserHandler() throws IOException {
    Set<String> direct = new TreeSet<>();
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (source.contains("new NoDropFileChooser(") || source.contains("new JFileChooser(")) {
          direct.add(file.getFileName().toString());
        }
      }
    }
    assertEquals(
        Set.of(
            "FileChooserHandler.java",
            "IceBridgeConsoleWindow.java",
            "SendFileProgressDialog.java"),
        direct);
  }
}
