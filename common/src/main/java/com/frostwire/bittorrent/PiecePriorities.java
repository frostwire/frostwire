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

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.util.Logger;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Reads and changes the download priority of a single piece from a transfer's Pieces view, on
 * desktop and Android (frostwire/frostwire#1485). Every libtorrent call runs on the {@code
 * background} executor, the results come back through the {@code ui} one: the handle is JNI and the
 * click or tap arrives on the UI thread.
 */
public final class PiecePriorities {
  private static final Logger LOG = Logger.getLogger(PiecePriorities.class);

  /**
   * The priorities offered for a piece. libtorrent downloads 0 never, 1 (jlibtorrent's NORMAL) as
   * its lowest, 4 by default and 7 first.
   */
  private static final Priority[] CHOICES = {
    Priority.IGNORE, Priority.NORMAL, Priority.FOUR, Priority.SEVEN
  };

  /** How a piece that isn't downloaded yet is marked on the hive. */
  public static final int MARK_NONE = 0;

  public static final int MARK_SKIP = 1;
  public static final int MARK_HIGH = 2;

  /** What a priority is called in the UI; each app translates these its own way. */
  public enum Label {
    UNKNOWN,
    DONT_DOWNLOAD,
    LOW,
    NORMAL,
    HIGH
  }

  /** The piece operations the Pieces view needs, so tests can stand in for the JNI handle. */
  public interface PieceHandle {
    boolean isValid();

    boolean havePiece(int piece);

    Priority piecePriority(int piece);

    void piecePriority(int piece, Priority priority);
  }

  /** What the priority choices show for a piece. */
  public record PieceState(int piece, boolean downloaded, Priority priority) {}

  private final Executor background;
  private final Executor ui;

  public PiecePriorities(Executor background, Executor ui) {
    this.background = background;
    this.ui = ui;
  }

  /** The priorities offered for a piece, lowest first, a new array on each call. */
  public static Priority[] choices() {
    return CHOICES.clone();
  }

  public static PieceHandle of(TorrentHandle handle) {
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
   * Reads whether {@code piece} is downloaded and its priority on the background executor, then
   * hands the result to {@code onUi} on the UI one. Nothing is handed over if the handle is gone.
   */
  public void lookup(PieceHandle handle, int piece, Consumer<PieceState> onUi) {
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
          ui.execute(() -> onUi.accept(state));
        });
  }

  /**
   * Sets the priority of {@code piece} on the background executor, unless it got downloaded
   * meanwhile, then runs {@code afterChange} on that same background thread, e.g. to refresh the
   * view.
   */
  public void apply(PieceHandle handle, int piece, Priority priority, Runnable afterChange) {
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

  /** What a priority is called, priorities between the offered ones go to the nearest level. */
  public static Label label(Priority priority) {
    if (priority == null) {
      return Label.UNKNOWN;
    }
    int level = priority.swig();
    if (level <= 0) {
      return Label.DONT_DOWNLOAD;
    }
    if (level < 4) {
      return Label.LOW;
    }
    if (level == 4) {
      return Label.NORMAL;
    }
    return Label.HIGH;
  }

  /**
   * Whether {@code priority} is one of the offered ones. The others are shown with their libtorrent
   * level, e.g. "High (5)".
   */
  public static boolean isChoice(Priority priority) {
    for (Priority choice : CHOICES) {
      if (choice == priority) {
        return true;
      }
    }
    return false;
  }

  /**
   * Index in {@link #choices()} of the offered priority that {@code priority} is shown as, e.g.
   * level 5 is shown as High; -1 for an unknown priority.
   */
  public static int choiceIndex(Priority priority) {
    Label label = label(priority);
    for (int i = 0; i < CHOICES.length; i++) {
      if (label(CHOICES[i]) == label) {
        return i;
      }
    }
    return -1;
  }

  /**
   * How a piece of this libtorrent priority level is marked on the hive. Only Don't Download and
   * High are marked. FrostWire gives the files it downloads level 1 (jlibtorrent's NORMAL) and
   * libtorrent's default is 4, so every level from 1 to 4 is a normal download and keeps the empty
   * cube's color.
   */
  public static int mark(int level) {
    if (level <= 0) {
      return MARK_SKIP;
    }
    return level > 4 ? MARK_HIGH : MARK_NONE;
  }
}
