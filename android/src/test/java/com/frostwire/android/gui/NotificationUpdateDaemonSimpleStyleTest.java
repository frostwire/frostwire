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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.app.Notification;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.transfers.TransferManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Android 14 and newer get a plain notification whose text carries the transfer status. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class NotificationUpdateDaemonSimpleStyleTest {

  @Test
  public void newerAndroidUsesASimpleNotificationWhoseTextShowsTheRates() {
    ConfigurationManager config = mock(ConfigurationManager.class);
    TransferManager transfers = mock(TransferManager.class);
    when(config.getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION))
        .thenReturn(true);
    when(transfers.getActiveDownloads()).thenReturn(3);
    when(transfers.getActiveUploads()).thenReturn(1);
    try (MockedStatic<ConfigurationManager> configStatic = mockStatic(ConfigurationManager.class);
        MockedStatic<TransferManager> transfersStatic = mockStatic(TransferManager.class)) {
      configStatic.when(ConfigurationManager::instance).thenReturn(config);
      transfersStatic.when(TransferManager::instance).thenReturn(transfers);
      NotificationUpdateDaemon daemon =
          new NotificationUpdateDaemon(ApplicationProvider.getApplicationContext());
      assertNull(daemon.getNotificationViews());
      Notification first = daemon.getNotificationObject();
      assertNotNull(first);
      assertEquals(
          "FrostWire is running", first.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
      assertEquals(1000, daemon.updateNotification());
      String text =
          daemon.getNotificationObject().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
      assertEquals(true, text.contains("3 @") && text.contains("1 @"));
    }
  }
}
