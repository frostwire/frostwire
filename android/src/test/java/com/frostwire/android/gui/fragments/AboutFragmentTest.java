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
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    assertTrue(jlibtorrent, jlibtorrent.startsWith("jlibtorrent"));

    assertTrue(((TextView) root.findViewById(R.id.fragment_about_content)).getText().length() > 0);
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
