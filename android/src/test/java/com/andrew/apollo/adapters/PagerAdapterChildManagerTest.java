/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.adapters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The music browser's tabs were added to the activity's FragmentManager from inside the browser
 * fragment, so they weren't its children: they weren't saved and restored with it, and a lookup by
 * fragment found them only by accident (GitHub #1456). The pager now takes the FragmentManager that
 * owns its pages.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PagerAdapterChildManagerTest {

  public static final class Page extends Fragment {}

  /** Hosts a pager, like MusicBrowserPhoneFragment. */
  public static final class Host extends Fragment {
    FrameLayout pages;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
      pages = new FrameLayout(inflater.getContext());
      pages.setId(0x7f0f0011);
      return pages;
    }
  }

  @Test
  public void thePagesBelongToTheHostingFragment() {
    FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    FrameLayout root = new FrameLayout(activity);
    root.setId(0x7f0f0012);
    activity.setContentView(root);
    Host host = new Host();
    activity.getSupportFragmentManager().beginTransaction().add(0x7f0f0012, host).commitNow();

    PagerAdapter adapter = new PagerAdapter(activity, host.getChildFragmentManager());
    adapter.add(Page.class, null);
    adapter.startUpdate(host.pages);
    Object page = adapter.instantiateItem(host.pages, 0);
    adapter.finishUpdate(host.pages);
    ShadowLooper.idleMainLooper();

    assertTrue(host.getChildFragmentManager().getFragments().contains(page));
    assertFalse(activity.getSupportFragmentManager().getFragments().contains(page));
  }

  /** Source check: the browser needs the music library and its loaders. */
  @Test
  public void theMusicBrowserGivesThePagerItsChildFragmentManager() throws Exception {
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve(
                            "apollo/src/com/andrew/apollo/ui/fragments/phone/MusicBrowserPhoneFragment.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(source.contains("newPagerAdapter(requireContext(),getChildFragmentManager())"));
  }
}
