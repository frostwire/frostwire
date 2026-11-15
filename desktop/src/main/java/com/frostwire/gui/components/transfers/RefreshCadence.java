/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.PerformanceMode;

/**
 * Which refresh ticks update the transfer detail panel. The Pieces hive re-renders its whole
 * visible bitmap on each update, so in Lite it updates on every {@link #LITE_PIECES_EVERY_TICKS}th
 * tick only (frostwire/frostwire#1555); selecting a transfer or changing a piece's priority still
 * redraws it at once. EDT only.
 */
final class RefreshCadence {
  static final int LITE_PIECES_EVERY_TICKS = 3;

  private long tick;

  /** Counts a refresh tick; whether a panel updated every {@code every} ticks updates on it. */
  boolean due(int every) {
    tick++;
    return every <= 1 || tick % every == 0;
  }

  /** Ticks between Pieces hive updates: every tick in Full. */
  static int piecesEveryTicks() {
    return PerformanceMode.isLite() ? LITE_PIECES_EVERY_TICKS : 1;
  }
}
