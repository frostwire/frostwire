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

import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.gui.theme.SkinRadioButtonMenuItem;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.I18n;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Reads and changes the download priority of a single piece from the Pieces tab
 * (frostwire/frostwire#1485). Every libtorrent call runs on the {@code background} executor, the
 * results come back through the {@code edt} one: the handle is JNI and the click arrives on the
 * EDT.
 */
final class PiecePriorities {
  private static final Logger LOG = Logger.getLogger(PiecePriorities.class);

  /**
   * The priorities offered for a piece. libtorrent downloads 0 never, 1 (jlibtorrent's NORMAL) as
   * its lowest, 4 by default and 7 first.
   */
  static final Priority[] CHOICES = {
    Priority.IGNORE, Priority.NORMAL, Priority.FOUR, Priority.SEVEN
  };

  /** How a piece that isn't downloaded yet is marked on the hive. */
  static final int MARK_NONE = 0;

  static final int MARK_SKIP = 1;
  static final int MARK_HIGH = 2;

  /** The piece operations the Pieces tab needs, so tests can stand in for the JNI handle. */
  interface PieceHandle {
    boolean isValid();

    boolean havePiece(int piece);

    Priority piecePriority(int piece);

    void piecePriority(int piece, Priority priority);
  }

  /** What the popup menu shows for a piece. */
  record PieceState(int piece, boolean downloaded, Priority priority) {}

  private final Executor background;
  private final Executor edt;

  PiecePriorities(Executor background, Executor edt) {
    this.background = background;
    this.edt = edt;
  }

  static PieceHandle of(TorrentHandle handle) {
    return new PieceHandle() {
      @Override
      public boolean isValid() {
        return handle.isValid();
      }

      @Override
      public boolean havePiece(int piece) {
        return handle.havePiece(piece);
      }

      @Override
      public Priority piecePriority(int piece) {
        return handle.piecePriority(piece);
      }

      @Override
      public void piecePriority(int piece, Priority priority) {
        handle.piecePriority(piece, priority);
      }
    };
  }

  /**
   * Reads whether {@code piece} is downloaded and its priority off the EDT, then hands the result
   * to {@code onEdt} on the EDT. Nothing is handed over if the handle is gone.
   */
  void lookup(PieceHandle handle, int piece, Consumer<PieceState> onEdt) {
    background.execute(
        () -> {
          PieceState state;
          try {
            if (handle == null || !handle.isValid()) {
              return;
            }
            state = new PieceState(piece, handle.havePiece(piece), handle.piecePriority(piece));
          } catch (Throwable t) {
            LOG.warn("Could not read the state of piece " + piece, t);
            return;
          }
          edt.execute(() -> onEdt.accept(state));
        });
  }

  /**
   * Sets the priority of {@code piece} off the EDT, unless it got downloaded meanwhile, then runs
   * {@code afterChange} on that same background thread, e.g. to refresh the tab.
   */
  void apply(PieceHandle handle, int piece, Priority priority, Runnable afterChange) {
    background.execute(
        () -> {
          try {
            if (handle == null || !handle.isValid() || handle.havePiece(piece)) {
              return;
            }
            handle.piecePriority(piece, priority);
          } catch (Throwable t) {
            LOG.warn("Could not set the priority of piece " + piece, t);
            return;
          }
          if (afterChange != null) {
            afterChange.run();
          }
        });
  }

  /**
   * The popup menu for a piece: one item per {@link #CHOICES}, the current one selected. A
   * downloaded piece's priority no longer matters, so its items are disabled.
   */
  static JPopupMenu createMenu(PieceState state, Consumer<Priority> onChoice) {
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
    for (Priority choice : CHOICES) {
      JMenuItem item = new SkinRadioButtonMenuItem(label(choice), choice == state.priority());
      item.setEnabled(!state.downloaded());
      item.addActionListener(e -> onChoice.accept(choice));
      menu.add(item);
    }
    return menu;
  }

  /** Menu label of a priority, priorities between the offered ones go to the nearest level. */
  static String label(Priority priority) {
    if (priority == null) {
      return I18n.tr("Unknown");
    }
    int level = priority.swig();
    if (level <= 0) {
      return I18n.tr("Don't Download");
    }
    if (level < 4) {
      return I18n.tr("Low");
    }
    if (level == 4) {
      return I18n.tr("Normal");
    }
    return I18n.tr("High");
  }

  /** Like {@link #label} with the libtorrent level for the in-between ones, e.g. "High (5)". */
  static String describe(Priority priority) {
    for (Priority choice : CHOICES) {
      if (choice == priority) {
        return label(priority);
      }
    }
    return priority == null ? label(null) : label(priority) + " (" + priority.swig() + ")";
  }

  /**
   * How a piece of this libtorrent priority level is marked on the hive. Only Don't Download and
   * High are marked. FrostWire gives the files it downloads level 1 (jlibtorrent's NORMAL) and
   * libtorrent's default is 4, so every level from 1 to 4 is a normal download and keeps the empty
   * cube's color.
   */
  static int mark(int level) {
    if (level <= 0) {
      return MARK_SKIP;
    }
    return level > 4 ? MARK_HIGH : MARK_NONE;
  }
}
