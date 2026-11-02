/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.frostwire.android.gui.views.AbstractActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * findFragment only looked at the activity's own FragmentManager, so a fragment hosted by another
 * fragment (the music browser's tabs, once they use its child FragmentManager) was never found and
 * the callers silently did nothing, e.g. deleting a playlist (GitHub #1456).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AbstractActivityFindFragmentTest {

  public static final class Target extends Fragment {}

  public static final class Other extends Fragment {}

  /** Hosts its own fragments in a child FragmentManager, like a pager fragment. */
  public static final class Host extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
      FrameLayout view = new FrameLayout(inflater.getContext());
      view.setId(0x7f0f0001);
      return view;
    }
  }

  private static FragmentActivity activity() {
    FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    FrameLayout root = new FrameLayout(activity);
    root.setId(0x7f0f0002);
    activity.setContentView(root);
    return activity;
  }

  @Test
  public void aFragmentInAChildFragmentManagerIsFound() {
    FragmentActivity activity = activity();
    Host host = new Host();
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0002, host, "host")
        .commitNow();
    Target target = new Target();
    host.getChildFragmentManager().beginTransaction().add(0x7f0f0001, target, "tab").commitNow();
    ShadowLooper.idleMainLooper();

    assertSame(
        target, AbstractActivity.findFragment(activity.getSupportFragmentManager(), Target.class));
  }

  @Test
  public void aTopLevelFragmentIsStillFound() {
    FragmentActivity activity = activity();
    Other other = new Other();
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0002, other, "other")
        .commitNow();

    assertSame(
        other, AbstractActivity.findFragment(activity.getSupportFragmentManager(), Other.class));
    assertNull(AbstractActivity.findFragment(activity.getSupportFragmentManager(), Target.class));
  }
}
