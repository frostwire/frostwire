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
 * File chooser icons, looked up off the EDT. The list renderer asks for an icon per row; asking the
 * Windows shell over COM for each one on the EDT stalled the chooser (Icebase #e51928be4469). A
 * miss returns a blank placeholder of the same size at once, the lookup runs on the executor, and
 * the result is stored on the EDT before the chooser is asked to repaint. Use from the EDT only.
 */
final class AsyncFileIconCache {
  private static final Logger LOG = Logger.getLogger(AsyncFileIconCache.class);
  private static final Icon PLACEHOLDER = new BlankIcon(16, 16);

  private final Executor executor;
  private final Function<File, Icon> lookup;
  private final Map<String, Icon> icons = new HashMap<>();
  private final Set<String> pending = new HashSet<>();

  /**
   * @param lookup finds a file's system icon; runs on {@code executor} and may return null
   */
  AsyncFileIconCache(Executor executor, Function<File, Icon> lookup) {
    this.executor = executor;
    this.lookup = lookup;
  }

  /** The cached icon, or a placeholder while it loads; {@code onLoaded} runs on the EDT. */
  Icon get(File file, Runnable onLoaded) {
    String path = file.getPath();
    Icon icon = icons.get(path);
    if (icon != null) {
      return icon;
    }
    if (pending.add(path)) {
      executor.execute(
          () -> {
            Icon found = null;
            try {
              found = lookup.apply(file);
            } catch (RuntimeException e) {
              LOG.warn("No icon for " + path + ": " + e.getMessage());
            }
            Icon result = found != null ? found : PLACEHOLDER;
            SwingUtilities.invokeLater(
                () -> {
                  if (pending.remove(path)) {
                    icons.put(path, result);
                    onLoaded.run();
                  }
                });
          });
    }
    return PLACEHOLDER;
  }

  /** Cached icon or null, without starting a lookup. */
  Icon cached(File file) {
    return icons.get(file.getPath());
  }

  void put(File file, Icon icon) {
    icons.put(file.getPath(), icon);
  }

  void clear() {
    icons.clear();
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
