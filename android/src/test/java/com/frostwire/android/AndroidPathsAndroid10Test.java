/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.DefaultFileSystem;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Android 10 (Q) is the one release where files live in app-private storage until copied out. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
public class AndroidPathsAndroid10Test {

  private final Application app = ApplicationProvider.getApplicationContext();

  @Test
  public void dataIsTheAppsExternalFilesDirectory() {
    AndroidPaths paths = new AndroidPaths(app);
    assertEquals(app.getExternalFilesDir(null), paths.data());
    assertEquals(new File(app.getExternalFilesDir(null), "Torrents"), paths.torrents());
  }

  @Test
  public void platformUsesTheQFileSystemAndAppPaths() {
    AndroidPlatform platform = new AndroidPlatform(app);
    assertTrue(platform.fileSystem() instanceof Android10QFileSystem);
    assertTrue(platform.systemPaths() instanceof AndroidPaths);
    assertTrue(platform.appSettings() instanceof AndroidSettings);
    assertNull(platform.vpn());
  }

  @Test
  public void isUIThreadIsTrueOnTheMainThreadOnly() throws InterruptedException {
    AndroidPlatform platform = new AndroidPlatform(app);
    assertTrue(platform.isUIThread());
    boolean[] onWorker = {true};
    Thread worker = new Thread(() -> onWorker[0] = platform.isUIThread());
    worker.start();
    worker.join();
    assertFalse(onWorker[0]);
  }

  @Test
  public void safIsOnlyForTheLollipopFileSystem() {
    Platform plain = mock(Platform.class);
    when(plain.fileSystem()).thenReturn(new DefaultFileSystem());
    LollipopFileSystem lollipop = mock(LollipopFileSystem.class);
    Platform saf = mock(Platform.class);
    when(saf.fileSystem()).thenReturn(lollipop);
    File onSdCard = new File("/storage/ABCD-1234/Download/file.mp3");
    File privateFile = new File("/storage/emulated/0/Android/data/com.frostwire.android/files/a");
    File internal = new File("/storage/emulated/0/Download/a.mp3");
    when(lollipop.getExtSdCardFolder(onSdCard)).thenReturn("/storage/ABCD-1234");

    try (MockedStatic<Platforms> platforms = Mockito.mockStatic(Platforms.class)) {
      platforms.when(Platforms::get).thenReturn(plain);
      assertFalse(AndroidPlatform.saf());
      assertFalse(AndroidPlatform.saf(onSdCard));

      platforms.when(Platforms::get).thenReturn(saf);
      assertTrue(AndroidPlatform.saf());
      assertTrue(AndroidPlatform.saf(onSdCard));
      assertFalse("private app files use plain POSIX", AndroidPlatform.saf(privateFile));
      assertFalse(AndroidPlatform.saf(internal));
    }
  }
}
