/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.transfers.TransferManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The permanent status notification with custom RemoteViews (before Android 14). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class NotificationUpdateDaemonTest {
  private ConfigurationManager config;
  private TransferManager transfers;
  private NotificationManager manager;
  private Context context;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<TransferManager> transfersStatic;

  @Before
  public void setUp() {
    config = mock(ConfigurationManager.class);
    transfers = mock(TransferManager.class);
    manager = mock(NotificationManager.class);
    when(config.getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION))
        .thenReturn(true);
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    transfersStatic = mockStatic(TransferManager.class);
    transfersStatic.when(TransferManager::instance).thenReturn(transfers);
    Application app = ApplicationProvider.getApplicationContext();
    context =
        new ContextWrapper(app) {
          @Override
          public Context getApplicationContext() {
            return this;
          }

          @Override
          public Object getSystemService(String name) {
            return Context.NOTIFICATION_SERVICE.equals(name)
                ? manager
                : super.getSystemService(name);
          }
        };
  }

  @After
  public void tearDown() {
    transfersStatic.close();
    configStatic.close();
  }

  @Test
  public void aDisabledNotificationIsNeverBuiltOrPosted() {
    when(config.getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION))
        .thenReturn(false);
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    assertNull(daemon.getNotificationObject());
    assertNull(daemon.getNotificationViews());
    assertSame(context, daemon.getParentContext());
    daemon.start();
    assertEquals(15000, daemon.updateNotification());
    verify(manager, never()).notify(anyInt(), any(Notification.class));
    // stop() on a never-started daemon does nothing
    daemon.stop();
    verify(manager, never()).cancel(anyInt());
  }

  @Test
  public void theNotificationIsBuiltWithRemoteViewsBeforeAndroid14() {
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    assertNotNull(daemon.getNotificationObject());
    assertNotNull(daemon.getNotificationViews());
  }

  @Test
  public void anIdleUpdatePostsTheStatusAtTheSlowInterval() {
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    assertEquals(15000, daemon.updateNotification());
    verify(manager).notify(Constants.NOTIFICATION_FROSTWIRE_STATUS, daemon.getNotificationObject());
  }

  @Test
  public void activeTransfersUpdateTheStatusAtTheFastInterval() {
    when(transfers.getActiveDownloads()).thenReturn(2);
    when(transfers.getDownloadsBandwidth()).thenReturn(2048L);
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    assertEquals(1000, daemon.updateNotification());
    when(transfers.getActiveDownloads()).thenReturn(0);
    when(transfers.getActiveUploads()).thenReturn(1);
    assertEquals(1000, daemon.updateNotification());
    verify(manager, times(2))
        .notify(Constants.NOTIFICATION_FROSTWIRE_STATUS, daemon.getNotificationObject());
  }

  @Test
  public void aMissingTransferManagerSkipsTheUpdate() {
    transfersStatic.when(TransferManager::instance).thenReturn(null);
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    assertEquals(15000, daemon.updateNotification());
    verify(manager, never()).notify(anyInt(), any(Notification.class));
  }

  @Test
  public void postingFailuresAreSurvived() {
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    doThrow(new SecurityException("no permission"))
        .when(manager)
        .notify(anyInt(), any(Notification.class));
    assertEquals(15000, daemon.updateNotification());
    doThrow(new IllegalStateException("boom"))
        .when(manager)
        .notify(anyInt(), any(Notification.class));
    assertEquals(15000, daemon.updateNotification());
    assertNotNull(
        "the notification is rebuilt after a failed post", daemon.getNotificationObject());
  }

  @Test
  public void aSettingTurnedOffLaterStopsTheUpdates() {
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    when(config.getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION))
        .thenReturn(false);
    assertEquals(15000, daemon.updateNotification());
    verify(manager, never()).notify(anyInt(), any(Notification.class));
  }

  @Test
  public void aSettingTurnedOnLaterBuildsTheNotificationOnDemand() {
    when(config.getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION))
        .thenReturn(false);
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    assertNull(daemon.getNotificationObject());
    when(config.getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION))
        .thenReturn(true);
    daemon.updateNotification();
    assertNotNull(daemon.getNotificationObject());
  }

  @Test
  public void startingAndStoppingCancelsTheStatusNotification() {
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    daemon.start();
    daemon.start(); // already running
    daemon.stop();
    verify(manager).cancel(Constants.NOTIFICATION_FROSTWIRE_STATUS);
    daemon.stop(); // nothing left to stop
    verify(manager, times(1)).cancel(Constants.NOTIFICATION_FROSTWIRE_STATUS);
  }

  @Test
  public void stoppingSurvivesAManagerThatRefusesToCancel() {
    NotificationUpdateDaemon daemon = new NotificationUpdateDaemon(context);
    daemon.start();
    doThrow(new SecurityException("no permission")).when(manager).cancel(anyInt());
    daemon.stop();
    verify(manager).cancel(Constants.NOTIFICATION_FROSTWIRE_STATUS);
  }
}
