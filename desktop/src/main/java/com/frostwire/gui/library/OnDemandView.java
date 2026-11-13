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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.library;

import java.awt.event.HierarchyEvent;
import javax.swing.JComponent;

/**
 * Builds a container's contents once: the first time the container is shown, or when {@link
 * #ensureBuilt()} is called because code needs the contents before that. EDT only.
 */
final class OnDemandView {
  private final Runnable build;
  private boolean built;

  OnDemandView(JComponent container, Runnable build) {
    this.build = build;
    container.addHierarchyListener(
        e -> {
          if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && container.isShowing()) {
            ensureBuilt();
          }
        });
  }

  void ensureBuilt() {
    if (built) {
      return;
    }
    // Set first: building adds children, which must not start a second build.
    built = true;
    build.run();
  }

  boolean isBuilt() {
    return built;
  }
}
