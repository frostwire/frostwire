/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SystemUtilsTest {

  private final Application app = ApplicationProvider.getApplicationContext();

  @Test
  public void versionChecksFollowTheSdkLevel() {
    assertTrue(SystemUtils.hasNougatOrNewer());
    assertTrue(SystemUtils.hasAndroid10OrNewer());
    assertFalse("34 is not exactly 29", SystemUtils.hasAndroid10());
    assertTrue(SystemUtils.hasAndroid11OrNewer());
    assertTrue(SystemUtils.hasAndroid12OrNewer());
    assertTrue(SystemUtils.hasAndroid13OrNewer());
  }

  @Test
  public void cacheDirsLiveUnderTheExternalFilesDirectory() {
    File dir = SystemUtils.getCacheDir(app, "search");
    assertEquals(new File(app.getExternalFilesDir(null), "cache/search"), dir);
  }

  @Test
  public void primaryStorageIsMountedAndPathsAreComparedToIt() {
    ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_MOUNTED);
    assertTrue(SystemUtils.isPrimaryExternalStorageMounted());
    ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_UNMOUNTED);
    assertFalse(SystemUtils.isPrimaryExternalStorageMounted());
    File primary = android.os.Environment.getExternalStorageDirectory();
    assertTrue(SystemUtils.isPrimaryExternalPath(new File(primary, "Download/a.mp3")));
    assertFalse(SystemUtils.isPrimaryExternalPath(new File("/mnt/other/a.mp3")));
    assertFalse(SystemUtils.isPrimaryExternalPath(null));
  }

  @Test
  public void exceptionSafePostIgnoresANullHandler() {
    SystemUtils.exceptionSafePost(null, () -> {});
  }

  @Test
  public void exceptionSafePostRunsRightAwayOnTheHandlersOwnThreadAndSwallowsFailures() {
    Handler main = new Handler(Looper.getMainLooper());
    AtomicInteger ran = new AtomicInteger();
    SystemUtils.exceptionSafePost(main, ran::incrementAndGet);
    assertEquals(1, ran.get());
    SystemUtils.exceptionSafePost(
        main,
        () -> {
          throw new IllegalStateException("boom");
        });
  }

  @Test
  public void exceptionSafePostHopsToTheHandlersThreadFromAnotherThread() throws Exception {
    HandlerThread thread = new HandlerThread("exception-safe-post-test");
    thread.start();
    try {
      Handler handler = new Handler(thread.getLooper());
      CountDownLatch ran = new CountDownLatch(1);
      Thread[] ranOn = new Thread[1];
      SystemUtils.exceptionSafePost(
          handler,
          () -> {
            ranOn[0] = Thread.currentThread();
            ran.countDown();
          });
      assertTrue(ran.await(5, TimeUnit.SECONDS));
      assertSame(thread, ranOn[0]);
    } finally {
      thread.quitSafely();
    }
  }

  @Test
  public void threadAssertionsFollowThePlatformsIdeaOfTheUiThread() {
    Platform platform = mock(Platform.class);
    try (MockedStatic<Platforms> platforms = Mockito.mockStatic(Platforms.class)) {
      platforms.when(Platforms::get).thenReturn(platform);

      when(platform.isUIThread()).thenReturn(true);
      assertTrue(SystemUtils.isUIThread());
      SystemUtils.ensureUIThreadOrCrash("test");
      RuntimeException onMain =
          assertThrows(
              RuntimeException.class, () -> SystemUtils.ensureBackgroundThreadOrCrash("Foo.bar"));
      assertTrue(onMain.getMessage().contains("Foo.bar"));

      when(platform.isUIThread()).thenReturn(false);
      assertFalse(SystemUtils.isUIThread());
      SystemUtils.ensureBackgroundThreadOrCrash("test");
      RuntimeException offMain =
          assertThrows(RuntimeException.class, () -> SystemUtils.ensureUIThreadOrCrash("Foo.baz"));
      assertTrue(offMain.getMessage().contains("Foo.baz"));
      assertTrue(offMain.getMessage().contains(Thread.currentThread().getName()));
    }
  }

  @Test
  public void uiThreadPostsRunOnTheMainLooper() {
    AtomicInteger order = new AtomicInteger();
    int[] seen = new int[3];
    SystemUtils.postToUIThread(() -> seen[0] = order.incrementAndGet());
    SystemUtils.postToUIThreadAtFront(() -> seen[1] = order.incrementAndGet());
    SystemUtils.postToUIThreadDelayed(() -> seen[2] = order.incrementAndGet(), 1000);
    assertEquals("nothing runs before the looper does", 0, order.get());
    ShadowLooper.idleMainLooper();
    assertEquals(2, order.get());
    assertEquals("the at-front post jumps the queue", 1, seen[1]);
    assertEquals(2, seen[0]);
    assertEquals(0, seen[2]);
    ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS);
    assertEquals(3, seen[2]);
  }

  @Test
  public void foregroundIsDecidedByTheProcessImportance() {
    ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
    Shadows.shadowOf(am).setProcesses(List.of());
    assertFalse("no processes reported", SystemUtils.isAppInForeground(app));

    ActivityManager.RunningAppProcessInfo background = process(app.getPackageName(), 400);
    Shadows.shadowOf(am).setProcesses(List.of(background));
    assertFalse(SystemUtils.isAppInForeground(app));

    ActivityManager.RunningAppProcessInfo other = process("some.other.app", 100);
    Shadows.shadowOf(am).setProcesses(List.of(other));
    assertFalse(SystemUtils.isAppInForeground(app));

    ActivityManager.RunningAppProcessInfo foreground = process(app.getPackageName(), 100);
    Shadows.shadowOf(am).setProcesses(List.of(other, foreground));
    assertTrue(SystemUtils.isAppInForeground(app));
  }

  private static ActivityManager.RunningAppProcessInfo process(String name, int importance) {
    ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
    info.processName = name;
    info.importance = importance;
    return info;
  }

  @Test
  public void handlerThreadsRunPostedAndDelayedTasks() throws Exception {
    CountDownLatch posted = new CountDownLatch(1);
    CountDownLatch delayed = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, posted::countDown);
    SystemUtils.postToHandlerDelayed(
        SystemUtils.HandlerThreadName.CONFIG_MANAGER, delayed::countDown, 0);
    assertTrue(posted.await(5, TimeUnit.SECONDS));
    assertTrue(delayed.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void highPriorityTasksRunOnTheirOwnThread() throws Exception {
    CountDownLatch ran = new CountDownLatch(1);
    String[] name = new String[1];
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.HIGH_PRIORITY,
        () -> {
          name[0] = Thread.currentThread().getName();
          ran.countDown();
        });
    assertTrue(ran.await(5, TimeUnit.SECONDS));
    assertTrue(name[0].startsWith("HIGH_PRIORITY-"));
  }

  @Test
  public void stoppingAllHandlerThreadsStillLetsNewTasksRun() throws Exception {
    CountDownLatch first = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, first::countDown);
    assertTrue(first.await(5, TimeUnit.SECONDS));
    SystemUtils.stopAllHandlerThreads();
    CountDownLatch second = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, second::countDown);
    assertTrue(second.await(5, TimeUnit.SECONDS));
  }
}
