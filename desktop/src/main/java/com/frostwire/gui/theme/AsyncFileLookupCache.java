/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.frostwire.util.Logger;
import java.awt.Component;
import java.awt.Graphics;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.SwingUtilities;

/**
 * Per-file values a file chooser shows (icons, display names), looked up off the EDT. The list
 * renderer asks for them per row; asking the Windows shell over COM for each one on the EDT stalled
 * the chooser (Icebase #e51928be4469, #0d182adae29c, frostwire/frostwire#1428). A miss returns a
 * placeholder at once, the lookup runs on the executor, and the result is stored on the EDT before
 * the chooser is asked to repaint. Use from the EDT only.
 *
 * @param <V> the looked-up value
 */
final class AsyncFileLookupCache<V> {
  private static final Logger LOG = Logger.getLogger(AsyncFileLookupCache.class);

  /** A blank 16x16 icon, the size of a system file icon, to show while one loads. */
  static final Icon BLANK_ICON = new BlankIcon(16, 16);

  private final Executor executor;
  private final Function<File, V> lookup;
  private final Function<File, V> placeholder;
  private final Map<String, V> values = new HashMap<>();
  private final Set<String> pending = new HashSet<>();

  /**
   * @param lookup finds a file's value; runs on {@code executor} and may return null
   * @param placeholder the value shown while the lookup runs, and kept when it finds nothing; runs
   *     on the EDT, so it must not ask the shell
   */
  AsyncFileLookupCache(Executor executor, Function<File, V> lookup, Function<File, V> placeholder) {
    this.executor = executor;
    this.lookup = lookup;
    this.placeholder = placeholder;
  }

  /** The cached value, or the placeholder while it loads; {@code onLoaded} runs on the EDT. */
  V get(File file, Runnable onLoaded) {
    String path = file.getPath();
    V value = values.get(path);
    if (value != null) {
      return value;
    }
    V shown = placeholder.apply(file);
    if (pending.add(path)) {
      executor.execute(
          () -> {
            V found = null;
            try {
              found = lookup.apply(file);
            } catch (RuntimeException e) {
              LOG.warn("Lookup failed for " + path + ": " + e.getMessage());
            }
            V result = found != null ? found : shown;
            SwingUtilities.invokeLater(
                () -> {
                  if (pending.remove(path)) {
                    values.put(path, result);
                    onLoaded.run();
                  }
                });
          });
    }
    return shown;
  }

  /** Cached value or null, without starting a lookup. */
  V cached(File file) {
    return values.get(file.getPath());
  }

  void put(File file, V value) {
    values.put(file.getPath(), value);
  }

  void clear() {
    values.clear();
    pending.clear();
  }

  private record BlankIcon(int width, int height) implements Icon {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {}

    @Override
    public int getIconWidth() {
      return width;
    }

    @Override
    public int getIconHeight() {
      return height;
    }
  }
}
