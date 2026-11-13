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

package com.frostwire.gui.library;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A value that is created later on the EDT, and the work that has to wait for it. Confined to the
 * EDT, so it takes no lock. {@link LibrarySearch} creates its search field in an invokeLater to
 * keep a class initialization off the startup path; the header read it right away when the Library
 * view had not been built yet, got null, and added null to a panel (Icebase #c90f930e756a,
 * NullPointerException in ApplicationHeader.initializeSearchFields on build 338).
 */
final class OnceReady<T> {
  private T value;
  private List<Consumer<T>> waiting = new ArrayList<>();

  /** The value, or null when it was not created yet. */
  T get() {
    return value;
  }

  /** Runs the action now when the value exists, otherwise as soon as it is set, in order. */
  void whenReady(Consumer<T> action) {
    if (value != null) {
      action.accept(value);
    } else {
      waiting.add(action);
    }
  }

  /** Stores the value and runs what was waiting for it. */
  void set(T newValue) {
    value = newValue;
    List<Consumer<T>> pending = waiting;
    waiting = new ArrayList<>();
    pending.forEach(action -> action.accept(newValue));
  }
}
