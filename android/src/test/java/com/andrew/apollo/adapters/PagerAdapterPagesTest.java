/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.andrew.apollo.ui.fragments.AlbumFragment;
import com.andrew.apollo.ui.fragments.ArtistFragment;
import com.andrew.apollo.ui.fragments.LastAddedFragment;
import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.ui.fragments.RecentFragment;
import com.andrew.apollo.ui.fragments.SongFragment;
import com.frostwire.android.R;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** The music browser's pager: the pages it builds, their arguments and titles. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PagerAdapterPagesTest {

  public static final class Page extends Fragment {}

  private AppCompatActivity activity;
  private FrameLayout pages;

  @Before
  public void setUp() {
    ActivityController<AppCompatActivity> controller =
        Robolectric.buildActivity(AppCompatActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    activity = controller.setup().get();
    pages = new FrameLayout(activity);
    pages.setId(0x7f0f0021);
    activity.setContentView(pages);
  }

  @Test
  public void aPageIsBuiltFromItsClassWithTheArgumentsItWasAddedWith() {
    PagerAdapter adapter = new PagerAdapter(activity);
    Bundle args = new Bundle();
    args.putString("x", "y");
    adapter.add(Page.class, args);
    adapter.add(Page.class, null);

    Fragment withArgs = adapter.getItem(0);
    Fragment withoutArgs = adapter.getItem(1);

    assertEquals(2, adapter.getCount());
    assertTrue(withArgs instanceof Page);
    assertEquals("y", withArgs.getArguments().getString("x"));
    assertNull(withoutArgs.getArguments());
  }

  @Test
  public void aPageClassThatCantBeBuiltIsReportedWithItsName() {
    PagerAdapter adapter = new PagerAdapter(activity);
    adapter.add(NoDefaultConstructor.class, null);

    RuntimeException failure = assertThrows(RuntimeException.class, () -> adapter.getItem(0));

    assertTrue(failure.getMessage().contains(NoDefaultConstructor.class.getName()));
  }

  /** No public no-arg constructor. */
  public static final class NoDefaultConstructor extends Fragment {
    public NoDefaultConstructor(int unused) {}
  }

  @Test
  public void destroyingAPageForgetsIt() {
    PagerAdapter adapter = new PagerAdapter(activity);
    adapter.add(Page.class, null);
    adapter.startUpdate(pages);
    Object page = adapter.instantiateItem(pages, 0);
    adapter.finishUpdate(pages);
    ShadowLooper.idleMainLooper();
    assertSame(page, adapter.getFragment(0));

    adapter.startUpdate(pages);
    adapter.destroyItem(pages, 0, page);
    adapter.finishUpdate(pages);
    ShadowLooper.idleMainLooper();

    assertNull(adapter.getFragment(0));
    assertNull(adapter.getFragment(7));
  }

  @Test
  public void instantiatingAPageAgainReplacesTheRememberedOne() {
    PagerAdapter adapter = new PagerAdapter(activity);
    adapter.add(Page.class, null);
    adapter.startUpdate(pages);
    adapter.instantiateItem(pages, 0);
    Object again = adapter.instantiateItem(pages, 0);
    adapter.finishUpdate(pages);

    assertSame(again, adapter.getFragment(0));
  }

  @Test
  public void tabTitlesAreTheUpperCasedPageTitles() {
    PagerAdapter adapter = new PagerAdapter(activity);
    adapter.add(Page.class, null);
    String expected =
        activity
            .getResources()
            .getStringArray(R.array.page_titles)[0]
            .toUpperCase(Locale.getDefault());

    assertEquals(expected, adapter.getPageTitle(0));
    assertNotNull(adapter.getPageTitle(0));
  }

  @Test
  public void theMusicTabsAreInTheOrderTheBrowserShowsThem() {
    PagerAdapter.MusicFragments[] tabs = PagerAdapter.MusicFragments.values();

    assertEquals(6, tabs.length);
    assertEquals(LastAddedFragment.class, PagerAdapter.MusicFragments.LAST.getFragmentClass());
    assertEquals(PlaylistFragment.class, PagerAdapter.MusicFragments.PLAYLIST.getFragmentClass());
    assertEquals(RecentFragment.class, PagerAdapter.MusicFragments.RECENT.getFragmentClass());
    assertEquals(ArtistFragment.class, PagerAdapter.MusicFragments.ARTIST.getFragmentClass());
    assertEquals(SongFragment.class, PagerAdapter.MusicFragments.SONG.getFragmentClass());
    assertEquals(AlbumFragment.class, PagerAdapter.MusicFragments.ALBUM.getFragmentClass());
  }
}
