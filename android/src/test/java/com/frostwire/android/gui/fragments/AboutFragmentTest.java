/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.jlibtorrent.LibTorrent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AboutFragmentTest {

  @Test
  public void showsTheVersionBuildAndAboutText() {
    AboutFragment fragment = new AboutFragment();
    FragmentTestHost.launch(fragment);
    View root = fragment.requireView();

    String title = ((TextView) root.findViewById(R.id.fragment_about_title)).getText().toString();
    assertTrue(title, title.startsWith("FrostWire "));
    assertTrue(title, title.endsWith("v" + Constants.FROSTWIRE_VERSION_STRING));

    String build =
        ((TextView) root.findViewById(R.id.fragment_about_build_number)).getText().toString();
    assertTrue(build, build.contains(String.valueOf(BuildConfig.VERSION_CODE)));
    assertTrue(build, build.contains("sdk level 34"));

    String jlibtorrent =
        ((TextView) root.findViewById(R.id.fragment_about_jlibtorrent_version))
            .getText()
            .toString();
    assertTrue(
        jlibtorrent, jlibtorrent.startsWith("jlibtorrent v" + LibTorrent.jlibtorrentVersion()));

    assertTrue(((TextView) root.findViewById(R.id.fragment_about_content)).getText().length() > 0);
  }

  @Test
  public void aMissingNativeLibraryShowsJlibtorrentAsUnavailableAndIsRecorded() {
    try (MockedStatic<LibTorrent> libtorrent = mockStatic(LibTorrent.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      libtorrent
          .when(LibTorrent::jlibtorrentVersion)
          .thenThrow(new UnsatisfiedLinkError("no jlibtorrent"));
      AboutFragment fragment = new AboutFragment();
      FragmentTestHost.launch(fragment);
      String jlibtorrent =
          ((TextView) fragment.requireView().findViewById(R.id.fragment_about_jlibtorrent_version))
              .getText()
              .toString();
      assertEquals("jlibtorrent unavailable", jlibtorrent);
      main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()));
    }
  }

  @Test
  public void theLinksOpenTheirUrls() throws Exception {
    AboutFragment fragment = new AboutFragment();
    FragmentTestHost.launch(fragment);
    View root = fragment.requireView();
    Application app = ApplicationProvider.getApplicationContext();

    root.findViewById(R.id.fragment_about_changelog).performClick();
    Intent intent = nextStartedActivity(app);
    assertNotNull("no browser intent", intent);
    assertEquals(Intent.ACTION_VIEW, intent.getAction());
    assertEquals(Constants.CHANGELOG_URL, intent.getDataString());

    root.findViewById(R.id.fragment_about_github_button).performClick();
    intent = nextStartedActivity(app);
    assertNotNull("no browser intent", intent);
    assertEquals(Constants.SOCIAL_URL_GITHUB_PAGE + "?ref=android_about", intent.getDataString());
  }

  @Test
  public void donationLinksAreHiddenOnlyOnGooglePlay() {
    AboutFragment fragment = new AboutFragment();
    FragmentTestHost.launch(fragment);
    View root = fragment.requireView();
    int expected = Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? View.GONE : View.VISIBLE;
    assertEquals(expected, root.findViewById(R.id.fragment_about_love_frostwire).getVisibility());
    assertEquals(
        expected, root.findViewById(R.id.fragment_about_support_frostwire).getVisibility());
  }

  @Test
  public void theYtDlpVersionShowsTheCachedOneThenTheLookedUpOne() {
    try (MockedStatic<TellurideCourier> courier = mockStatic(TellurideCourier.class);
        MockedStatic<SystemUtils> utils = mockStatic(SystemUtils.class, CALLS_REAL_METHODS)) {
      courier.when(TellurideCourier::getCachedYtDlpVersion).thenReturn("2025.10.22");
      courier
          .when(() -> TellurideCourier.ytDlpVersion(any()))
          .thenAnswer(
              invocation -> {
                ((TellurideCourier.OnYtDlpVersionCallback) invocation.getArgument(0))
                    .onVersion("2025.11.01");
                return null;
              });
      // the lookup runs where the mocks are
      utils
          .when(() -> SystemUtils.postToHandler(any(), any()))
          .thenAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return null;
              });
      AboutFragment fragment = new AboutFragment();
      FragmentTestHost.launch(fragment);
      shadowOf(Looper.getMainLooper()).idle();
      assertEquals(
          "yt_dlp 2025.11.01",
          ((TextView) fragment.requireView().findViewById(R.id.fragment_about_yt_dlp_version))
              .getText()
              .toString());
    }
  }

  private static Intent nextStartedActivity(Application app) throws InterruptedException {
    // openURL starts the activity from a handler thread
    for (int i = 0; i < 250; i++) {
      Intent intent = shadowOf(app).getNextStartedActivity();
      if (intent != null) {
        return intent;
      }
      Thread.sleep(20);
    }
    return null;
  }
}
