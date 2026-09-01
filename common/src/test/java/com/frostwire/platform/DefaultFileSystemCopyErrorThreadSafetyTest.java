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

package com.frostwire.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultFileSystemCopyErrorThreadSafetyTest {

    @Test
    void copyErrorsRemainBoundToTheCopyingThread() throws Exception {
        TestFileSystem fs = new TestFileSystem();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> firstResult = new AtomicReference<>();
        AtomicReference<Throwable> secondResult = new AtomicReference<>();
        IOException firstError = new IOException("first");
        IOException secondError = new IOException("second");

        Thread first = new Thread(() -> {
            fs.setCopyError(firstError);
            ready.countDown();
            await(release);
            firstResult.set(fs.lastCopyError());
        });
        Thread second = new Thread(() -> {
            fs.setCopyError(secondError);
            ready.countDown();
            await(release);
            secondResult.set(fs.lastCopyError());
        });

        first.start();
        second.start();
        ready.await();
        release.countDown();
        first.join();
        second.join();

        assertSame(firstError, firstResult.get());
        assertSame(secondError, secondResult.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class TestFileSystem extends DefaultFileSystem {
        void setCopyError(Throwable error) {
            lastCopyError.set(error);
        }
    }
}
