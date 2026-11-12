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

package com.frostwire.util;

import java.util.concurrent.Callable;

/**
 * Runs CPU-bound work on the calling thread at a priority below the EDT's and the worker threads',
 * so that on a computer with one or two CPUs it runs when they have nothing to do instead of
 * competing with them.
 */
public final class BackgroundPriority {
  /** Below normal: Windows schedules it under every normal-priority thread of the process. */
  static final int PRIORITY = Thread.NORM_PRIORITY - 2;

  private BackgroundPriority() {}

  /**
   * Calls {@code work} with the current thread at {@link #PRIORITY} and restores the thread's
   * priority afterwards, also when {@code work} throws. A thread already at or below that priority
   * keeps its own.
   */
  public static <T> T call(Callable<T> work) throws Exception {
    Thread thread = Thread.currentThread();
    int before = thread.getPriority();
    if (before > PRIORITY) {
      thread.setPriority(PRIORITY);
    }
    try {
      return work.call();
    } finally {
      thread.setPriority(before);
    }
  }
}
