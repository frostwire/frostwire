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

package com.frostwire.gui.components.transfers;

import com.frostwire.bittorrent.PiecePriorities;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.gui.theme.SkinRadioButtonMenuItem;
import com.frostwire.jlibtorrent.Priority;
import com.limegroup.gnutella.gui.I18n;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * The Pieces tab's priority menu and names of the priorities (frostwire/frostwire#1485), the logic
 * shared with Android is in {@link PiecePriorities}.
 */
final class PiecePriorityMenu {
  private PiecePriorityMenu() {}

  /**
   * The popup menu for a piece: one item per {@link PiecePriorities#choices()}, the current one
   * selected. A downloaded piece's priority no longer matters, so its items are disabled.
   */
  static JPopupMenu createMenu(PiecePriorities.PieceState state, Consumer<Priority> onChoice) {
    JPopupMenu menu = new SkinPopupMenu();
    JMenuItem title =
        new SkinMenuItem(
            I18n.tr(
                "Piece #{0}: {1}",
                String.format("%,d", state.piece()),
                state.downloaded() ? I18n.tr("downloaded") : describe(state.priority())));
    title.setEnabled(false);
    menu.add(title);
    menu.addSeparator();
    for (Priority choice : PiecePriorities.choices()) {
      JMenuItem item = new SkinRadioButtonMenuItem(label(choice), choice == state.priority());
      item.setEnabled(!state.downloaded());
      item.addActionListener(e -> onChoice.accept(choice));
      menu.add(item);
    }
    return menu;
  }

  /** Menu label of a priority, priorities between the offered ones go to the nearest level. */
  static String label(Priority priority) {
    switch (PiecePriorities.label(priority)) {
      case DONT_DOWNLOAD:
        return I18n.tr("Don't Download");
      case LOW:
        return I18n.tr("Low");
      case NORMAL:
        return I18n.tr("Normal");
      case HIGH:
        return I18n.tr("High");
      default:
        return I18n.tr("Unknown");
    }
  }

  /** Like {@link #label} with the libtorrent level for the in-between ones, e.g. "High (5)". */
  static String describe(Priority priority) {
    if (priority == null || PiecePriorities.isChoice(priority)) {
      return label(priority);
    }
    return label(priority) + " (" + priority.swig() + ")";
  }
}
