/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1389: FWImageLoader.getInstance built the disk cache inside the class lock,
 * calling Context.getExternalFilesDir (a binder call that may create the folder) and StatFs. On
 * slow storage the main thread, creating the search adapter, waited on that lock until the app was
 * reported as not responding. The disk cache is now built lazily by Coil, off the lock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class FWImageLoaderCreationTest {

  @After
  public void resetSingleton() throws Exception {
    Field instance = FWImageLoader.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(null, null);
  }

  @Test
  public void creatingTheLoaderDoesNoStorageLookups() throws Exception {
    resetSingleton();
    AtomicInteger lookups = new AtomicInteger();
    Context context =
        new ContextWrapper(ApplicationProvider.getApplicationContext()) {
          @Override
          public Context getApplicationContext() {
            return this;
          }

          @Override
          public File getExternalFilesDir(String type) {
            lookups.incrementAndGet();
            return super.getExternalFilesDir(type);
          }
        };

    FWImageLoader loader = FWImageLoader.getInstance(context);
    assertNotNull(loader);
    assertEquals("storage looked up while creating the loader", 0, lookups.get());

    // onLowMemory runs on the main thread and frees memory only.
    loader.clearMemoryCache();
    assertEquals("storage looked up while freeing memory", 0, lookups.get());
  }
}
