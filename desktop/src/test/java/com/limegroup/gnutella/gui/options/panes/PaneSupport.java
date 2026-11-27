/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.jlibtorrent.SettingsPack;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Helpers for tests that build real Options panes headless and drive their components. */
final class PaneSupport {
  private PaneSupport() {}

  /** A body that may throw. */
  interface Body<T> {
    T run() throws Exception;
  }

  /** A void body that may throw. */
  interface VoidBody {
    void run() throws Exception;
  }

  /** Runs on the EDT and returns what the body returned, rethrowing what it threw. */
  static <T> T onEdt(Body<T> body) throws Exception {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            result.set(body.run());
          } catch (Throwable t) {
            failure.set(t);
          }
        });
    if (failure.get() != null) {
      if (failure.get() instanceof Exception e) {
        throw e;
      }
      throw new AssertionError(failure.get());
    }
    return result.get();
  }

  static void onEdt(VoidBody body) throws Exception {
    onEdt(
        () -> {
          body.run();
          return null;
        });
  }

  /** Every component of the given type under the container, depth first. */
  static <T extends Component> List<T> all(Component root, Class<T> type) {
    List<T> found = new ArrayList<>();
    collect(root, type, found);
    return found;
  }

  private static <T extends Component> void collect(Component c, Class<T> type, List<T> found) {
    if (type.isInstance(c)) {
      found.add(type.cast(c));
    }
    if (c instanceof Container container) {
      for (Component child : container.getComponents()) {
        collect(child, type, found);
      }
    }
  }

  /** The only component of the given type under the container. */
  static <T extends Component> T only(Component root, Class<T> type) {
    List<T> found = all(root, type);
    if (found.size() != 1) {
      throw new AssertionError("expected one " + type.getSimpleName() + ", found " + found.size());
    }
    return found.get(0);
  }

  /** Records the validation errors panes would show in a dialog. Close it to restore the dialog. */
  static final class Errors implements AutoCloseable {
    final List<String> shown = new ArrayList<>();
    private final Consumer<String> saved = AbstractPaneItem.errorReporter;

    Errors() {
      AbstractPaneItem.errorReporter = shown::add;
    }

    @Override
    public void close() {
      AbstractPaneItem.errorReporter = saved;
    }
  }

  /**
   * Stands in for libtorrent behind the Options pages: runs their background work on the calling
   * thread and records what they would have told the engine. Close it to restore the real one.
   */
  static final class FakeEngine implements EngineSettings.Engine, AutoCloseable {
    final List<SettingsPack> applied = new ArrayList<>();
    final List<String> listenInterfaces = new ArrayList<>();
    private final Consumer<Runnable> savedBackground = EngineSettings.background;
    private final Supplier<EngineSettings.Engine> savedEngine = EngineSettings.engine;

    FakeEngine() {
      EngineSettings.background = Runnable::run;
      EngineSettings.engine = () -> this;
    }

    @Override
    public void applySettings(SettingsPack settings) {
      applied.add(settings);
    }

    @Override
    public void listenInterfaces(String value) {
      listenInterfaces.add(value);
    }

    @Override
    public void close() {
      EngineSettings.background = savedBackground;
      EngineSettings.engine = savedEngine;
    }
  }
}
