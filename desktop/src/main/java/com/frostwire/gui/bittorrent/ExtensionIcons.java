/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.Icon;

/**
 * File-type icons for a table that asks for them while painting.
 *
 * <p>On Windows an uncached icon is a COM round-trip into the shell (Win32ShellFolder2.getIcon)
 * that can block for seconds, so the first request for an extension returns null and loads the icon
 * on {@code background}; {@code onLoaded} then repaints and the next paint gets the icon. Each
 * extension is looked up once, failures included. Thread-safe.
 */
final class ExtensionIcons {
  private final Function<String, Icon> lookup;
  private final Executor background;
  private final Runnable onLoaded;
  private final Map<String, Optional<Icon>> loaded = new ConcurrentHashMap<>();
  private final Set<String> loading = ConcurrentHashMap.newKeySet();

  ExtensionIcons(Function<String, Icon> lookup, Executor background, Runnable onLoaded) {
    this.lookup = lookup;
    this.background = background;
    this.onLoaded = onLoaded;
  }

  /** The icon for {@code extension}, or null while it loads or when there is none. */
  Icon get(String extension) {
    String key = extension == null ? "" : extension;
    Optional<Icon> icon = loaded.get(key);
    if (icon != null) {
      return icon.orElse(null);
    }
    if (loading.add(key)) {
      background.execute(
          () -> {
            Icon result = null;
            try {
              result = lookup.apply(key);
            } catch (RuntimeException ignored) {
              // no icon for this type; don't retry on every paint
            }
            loaded.put(key, Optional.ofNullable(result));
            onLoaded.run();
          });
    }
    return null;
  }
}
