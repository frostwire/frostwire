/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.services;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Submitting a task must not rename the submitting thread: it renamed the main thread in every ANR
 * report and took a stack trace on it per task (frostwire/frostwire#1375).
 */
public class EngineThreadPoolTest {

  @Test
  public void submittingATaskLeavesTheCallersNameAlone() throws Exception {
    EngineThreadPool pool = new EngineThreadPool();
    String before = Thread.currentThread().getName();
    CountDownLatch ran = new CountDownLatch(2);
    try {
      pool.execute(ran::countDown);
      pool.submit(ran::countDown);
      assertEquals(before, Thread.currentThread().getName());
      ran.await(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
      Thread.currentThread().setName(before);
    }
  }
}
