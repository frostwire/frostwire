/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.widget.Button;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.SearchMediator;
import com.frostwire.android.gui.fragments.FragmentTestHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class OtherFragmentTest {
  private MockedStatic<ConfigurationRepository> repository;
  private MockedStatic<SearchMediator> mediators;
  private SearchMediator mediator;

  @Before
  public void setUp() {
    repository = mockStatic(ConfigurationRepository.class);
    mediator = mock(SearchMediator.class);
    mediators = mockStatic(SearchMediator.class);
    mediators.when(SearchMediator::instance).thenReturn(mediator);
    when(mediator.getCacheSize()).thenReturn(3L * 1024 * 1024);
  }

  @After
  public void tearDown() {
    mediators.close();
    repository.close();
  }

  @Test
  public void clearingTheCrawlCacheClearsItAndShowsTheNewSize() {
    OtherFragment fragment = new OtherFragment();
    FragmentTestHost.launch(fragment);
    Preference clearIndex = fragment.findPreference("frostwire.prefs.internal.clear_index");
    assertNotNull(clearIndex);
    assertEquals(
        fragment.getString(R.string.crawl_cache_size, 3.0f), clearIndex.getSummary().toString());

    when(mediator.getCacheSize()).thenReturn(0L);
    Button clear = PreferenceTestSupport.button(fragment, fragment.getString(R.string.clear));
    assertNotNull(clear);
    clear.performClick();

    verify(mediator).clearCache();
    assertEquals(
        fragment.getString(R.string.crawl_cache_size, 0.0f), clearIndex.getSummary().toString());
  }

  @Test
  public void turningOffThePermanentNotificationCancelsTheStatusNotification() {
    OtherFragment fragment = new OtherFragment();
    FragmentTestHost.launch(fragment);
    Context app = ApplicationProvider.getApplicationContext();
    NotificationManager manager =
        (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.notify(Constants.NOTIFICATION_FROSTWIRE_STATUS, new Notification());
    assertEquals(1, shadowOf(manager).size());

    CheckBoxPreference permanent =
        fragment.findPreference(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION);
    assertTrue(PreferenceTestSupport.change(permanent, true));
    assertEquals(1, shadowOf(manager).size());
    assertTrue(PreferenceTestSupport.change(permanent, false));
    assertEquals(0, shadowOf(manager).size());
  }
}
