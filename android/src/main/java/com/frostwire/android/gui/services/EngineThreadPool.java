/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.services;

import com.frostwire.android.util.Debug;
import com.frostwire.util.ThreadPool;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This executor helps to keep track of potential context leaks.
 *
 * @author gubatron
 * @author aldenml
 */
public final class EngineThreadPool extends ThreadPool {

  // look at AsyncTask for a more dynamic calculation, but it yields
  // 17 in a medium hardware phone
  private static final int CORE_POOL_SIZE = 1;
  private static final int MAXIMUM_POOL_SIZE = 8;
  private static final int KEEP_ALIVE_TIME_IN_SECS = 2;

  EngineThreadPool() {
    super(
        "Engine",
        CORE_POOL_SIZE,
        MAXIMUM_POOL_SIZE,
        KEEP_ALIVE_TIME_IN_SECS,
        new LinkedBlockingQueue<>(4),
        false);
  }

  @Override
  public void execute(Runnable command) {
    verifyTask(command);
    super.execute(command);
  }

  @Override
  public Future<?> submit(Runnable task) {
    verifyTask(task);
    return super.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    verifyTask(task);
    return super.submit(task, result);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    verifyTask(task);
    return super.submit(task);
  }

  private void verifyTask(Object task) {
    if (Debug.hasContext(task)) {
      throw new RuntimeException("Runnable/task contains context, possible context leak");
    }
  }
}
