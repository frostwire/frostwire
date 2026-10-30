/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android;

import android.app.Application;

import com.frostwire.android.util.SystemUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

/**
 * MainApplication.onCreate builds AndroidPaths on the main thread, and AndroidPaths waits, without a
 * timeout, for the app's files directory to be read in the background. It used to read it on the
 * shared MISC handler thread, so a slow MISC task (start-up work waiting up to 30 s for the Python
 * runtime, and retrying) held the main thread behind it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AndroidPathsBusyQueueTest {

    @Test
    public void aBusyMiscQueueDoesNotHoldUpConstruction() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        CountDownLatch release = new CountDownLatch(1);
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<AndroidPaths> paths = caller.submit(() -> new AndroidPaths(app));
            assertNotNull(paths.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }
}
