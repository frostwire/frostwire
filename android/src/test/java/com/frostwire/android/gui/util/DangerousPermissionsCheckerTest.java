/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DangerousPermissionsCheckerTest {
  /** An activity that satisfies the callback type the checker needs. */
  public static class PermissionActivity extends Activity
      implements androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback {}

  private final List<Runnable> posted = new ArrayList<>();
  private MockedStatic<SystemUtils> system;
  private PermissionActivity activity;

  @Before
  public void setUp() {
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              posted.add(call.getArgument(1));
              return null;
            });
    activity =
        Robolectric.buildActivity(PermissionActivity.class)
            .create()
            .start()
            .resume()
            .visible()
            .get();
    DangerousPermissionsChecker.AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
    DangerousPermissionsChecker.FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
  }

  @After
  public void tearDown() {
    system.close();
  }

  private DangerousPermissionsChecker<PermissionActivity> storageChecker() {
    return new DangerousPermissionsChecker<>(
        activity, DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void aMissingActivityIsRejected() {
    new DangerousPermissionsChecker<PermissionActivity>(null, 1);
  }

  @Test
  public void theActivityIsHeldWeakly() {
    assertSame(activity, storageChecker().getActivity());
  }

  @Test
  public void oldAndroidAsksForReadAndWriteStorage() {
    storageChecker().requestPermissions();
    ShadowActivity.PermissionsRequest request =
        Shadows.shadowOf(activity).getLastRequestedPermission();
    assertArrayEquals(
        new String[] {
          Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
        },
        request.requestedPermissions);
    assertEquals(
        DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE, request.requestCode);
  }

  @Test
  @Config(sdk = 30)
  public void android11AsksForReadStorageOnly() {
    system.when(SystemUtils::hasAndroid11OrNewer).thenReturn(true);
    storageChecker().requestPermissions();
    assertArrayEquals(
        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
        Shadows.shadowOf(activity).getLastRequestedPermission().requestedPermissions);
  }

  @Test
  @Config(sdk = 33)
  public void android13AsksForTheGranularMediaPermissions() {
    system.when(SystemUtils::hasAndroid13OrNewer).thenReturn(true);
    storageChecker().requestPermissions();
    assertArrayEquals(
        new String[] {
          Manifest.permission.READ_MEDIA_VIDEO,
          Manifest.permission.READ_MEDIA_AUDIO,
          Manifest.permission.READ_MEDIA_IMAGES
        },
        Shadows.shadowOf(activity).getLastRequestedPermission().requestedPermissions);
  }

  @Test
  @Config(sdk = 33)
  public void notificationPermissionIsRequestedWithItsOwnCode() {
    DangerousPermissionsChecker<PermissionActivity> checker =
        new DangerousPermissionsChecker<>(
            activity, DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE);
    checker.requestPermissions();
    assertArrayEquals(
        new String[] {Manifest.permission.POST_NOTIFICATIONS},
        Shadows.shadowOf(activity).getLastRequestedPermission().requestedPermissions);
    assertFalse(
        "only storage access is judged by the storage rule", checker.noExternalStorageAccess());
    assertFalse(checker.shouldShowExternalStorageAccessRationale());
  }

  @Test
  public void unknownRequestCodesRequestNothing() {
    DangerousPermissionsChecker<PermissionActivity> checker =
        new DangerousPermissionsChecker<>(activity, 99);
    checker.requestPermissions();
    assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
  }

  @Test
  public void aFinishingActivityIsNotAskedForAnything() {
    activity.finish();
    storageChecker().requestPermissions();
    assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
  }

  @Test
  public void storageAccessFollowsTheGrantedPermissions() {
    DangerousPermissionsChecker<PermissionActivity> checker = storageChecker();
    assertTrue(checker.noExternalStorageAccess());
    Shadows.shadowOf(ApplicationProvider.<Application>getApplicationContext())
        .grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    assertFalse(checker.noExternalStorageAccess());
  }

  @Test
  public void theRationaleIsShownWhenAnyRequestedPermissionNeedsOne() {
    DangerousPermissionsChecker<PermissionActivity> checker = storageChecker();
    assertFalse(checker.shouldShowExternalStorageAccessRationale());
    Shadows.shadowOf(activity.getPackageManager())
        .setShouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE, true);
    assertTrue(checker.shouldShowExternalStorageAccessRationale());
  }

  @Test
  public void grantedStoragePermissionsFixPlaylistOwnershipInTheBackground() {
    DangerousPermissionsChecker<PermissionActivity> checker = storageChecker();
    checker.onRequestPermissionsResult(
        DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE,
        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
        new int[] {PackageManager.PERMISSION_GRANTED});
    assertEquals(1, posted.size());
    try (MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      main.when(MainApplication::context).thenReturn(activity);
      posted.get(0).run();
      music.verify(() -> MusicUtils.fixPlaylistsOwnership(activity));
    }
  }

  @Test
  public void deniedStoragePermissionsFixNothing() {
    storageChecker()
        .onRequestPermissionsResult(
            DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE,
            new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
            new int[] {PackageManager.PERMISSION_DENIED});
    assertTrue(posted.isEmpty());
    assertNull("no rationale needed, no dialog", ShadowAlertDialog.getLatestAlertDialog());
  }

  @Test
  public void aDeniedPermissionThatNeedsARationaleShowsTheExplanationDialog() {
    Shadows.shadowOf(activity.getPackageManager())
        .setShouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE, true);
    storageChecker()
        .onRequestPermissionsResult(
            DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE,
            new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
            new int[] {PackageManager.PERMISSION_DENIED});
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(dialog);
    // "request again" asks once more
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertNotNull(Shadows.shadowOf(activity).getLastRequestedPermission());
  }

  @Test
  public void exitingFromTheRationaleDialogShutsFrostWireDown() {
    Shadows.shadowOf(activity.getPackageManager())
        .setShouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE, true);
    storageChecker()
        .onRequestPermissionsResult(
            DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE,
            new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
            new int[] {PackageManager.PERMISSION_DENIED});
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    Engine engine = mock(Engine.class);
    try (MockedStatic<Engine> engineStatic = mockStatic(Engine.class);
        MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class)) {
      engineStatic.when(Engine::instance).thenReturn(engine);
      dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      org.mockito.Mockito.verify(engine).shutdown();
      music.verify(() -> MusicUtils.requestMusicPlaybackServiceShutdown(activity));
    }
    assertTrue(activity.isFinishing());
  }

  @Test
  public void grantedNotificationPermissionStartsThePlaybackService() {
    DangerousPermissionsChecker<PermissionActivity> checker =
        new DangerousPermissionsChecker<>(
            activity, DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE);
    try (MockedStatic<MusicPlaybackService> service = mockStatic(MusicPlaybackService.class)) {
      checker.onRequestPermissionsResult(
          DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE,
          new String[] {Manifest.permission.POST_NOTIFICATIONS, "other"},
          new int[] {PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED});
      service.verify(MusicPlaybackService::onCreateSafe);
    }
    try (MockedStatic<MusicPlaybackService> service = mockStatic(MusicPlaybackService.class)) {
      checker.onRequestPermissionsResult(
          DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE,
          new String[] {Manifest.permission.POST_NOTIFICATIONS},
          new int[] {PackageManager.PERMISSION_DENIED});
      service.verifyNoInteractions();
    }
    // an unrelated request code is ignored
    checker.onRequestPermissionsResult(77, new String[0], new int[0]);
  }

  @Test
  public void withWriteSettingsPermissionThePendingRingtoneIsSetInTheBackground() {
    DangerousPermissionsChecker.AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = 5;
    DangerousPermissionsChecker.FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = 2;
    assertTrue(DangerousPermissionsChecker.hasPermissionToWriteSettings(activity));
    assertTrue(DangerousPermissionsChecker.handleOnWriteSettingsActivityResult(activity));
    assertEquals(-1, DangerousPermissionsChecker.AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK);
    assertEquals(-1, DangerousPermissionsChecker.FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK);
    assertEquals(1, posted.size());
    // the task runs after the statics were reset: it must still carry the pending song
    try (MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class)) {
      posted.get(0).run();
      music.verify(() -> MusicUtils.setRingtone(activity, 5L, (byte) 2));
    }
  }

  @Test
  public void theRingtoneRequestIsIgnoredWhenNoneWasPending() {
    assertFalse(DangerousPermissionsChecker.handleOnWriteSettingsActivityResult(activity));
    assertTrue(posted.isEmpty());
  }
}
