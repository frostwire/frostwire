/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@SuppressWarnings("deprecation")
public class PagerAndSwipeTest {

  public static final class Page extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
      return new View(inflater.getContext());
    }
  }

  static final class Pages extends FragmentPagerAdapter {
    final List<Page> created = new ArrayList<>();

    Pages(androidx.fragment.app.FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int position) {
      Page page = new Page();
      created.add(page);
      return page;
    }

    @Override
    public int getCount() {
      return 3;
    }
  }

  private FragmentActivity activity;
  private ViewPager pager;
  private Pages pages;

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    FrameLayout root = new FrameLayout(activity);
    pager = new ViewPager(activity);
    pager.setId(0x7f0f0020);
    root.addView(pager, new FrameLayout.LayoutParams(400, 400));
    activity.setContentView(root);
    pages = new Pages(activity.getSupportFragmentManager());
    pager.setAdapter(pages);
    ShadowLooper.idleMainLooper();
    pager.measure(
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
    pager.layout(0, 0, 400, 400);
    ShadowLooper.idleMainLooper();
  }

  // ---- FragmentPagerAdapter

  @Test
  public void thePagerAddsTheCurrentPageAndTheNextOne() {
    assertEquals(2, pages.created.size());
    assertNotNull(
        activity
            .getSupportFragmentManager()
            .findFragmentByTag("android:switcher:" + 0x7f0f0020 + ":0"));
    assertNotNull(
        activity
            .getSupportFragmentManager()
            .findFragmentByTag("android:switcher:" + 0x7f0f0020 + ":1"));
    assertNull(
        activity
            .getSupportFragmentManager()
            .findFragmentByTag("android:switcher:" + 0x7f0f0020 + ":2"));
  }

  @Test
  public void onlyThePrimaryPageIsMenuVisible() {
    assertTrue(pages.created.get(0).isMenuVisible());
    assertFalse(pages.created.get(1).isMenuVisible());
    pager.setCurrentItem(1);
    ShadowLooper.idleMainLooper();
    assertFalse(pages.created.get(0).isMenuVisible());
    assertTrue(pages.created.get(1).isMenuVisible());
  }

  @Test
  public void aPageThatWasAddedIsReattachedNotRecreatedWhenComingBack() {
    pager.setOffscreenPageLimit(1);
    pager.setCurrentItem(2, false);
    ShadowLooper.idleMainLooper();
    int createdAfterForward = pages.created.size();
    pager.setCurrentItem(0, false);
    ShadowLooper.idleMainLooper();
    assertEquals(createdAfterForward, pages.created.size());
    assertSame(
        pages.created.get(0),
        activity
            .getSupportFragmentManager()
            .findFragmentByTag("android:switcher:" + 0x7f0f0020 + ":0"));
  }

  @Test
  public void itemIdsDefaultToThePositionAndStateIsNotKept() {
    assertEquals(2L, pages.getItemId(2));
    assertNull(pages.saveState());
    pages.restoreState(null, null);
    Page page = pages.created.get(0);
    assertTrue(pages.isViewFromObject(page.getView(), page));
    assertFalse(pages.isViewFromObject(new View(activity), page));
  }

  @Test
  public void aContainerWithoutAnIdIsRejected() {
    ViewGroup noId = new FrameLayout(activity);
    try {
      pages.startUpdate(noId);
      fail("a pager container needs an id");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("requires a view id"));
    }
  }

  // ---- SwipeLayout

  private static MotionEvent event(long time, int action, float x, float y) {
    return MotionEvent.obtain(0, time, action, x, y, 0);
  }

  private final List<String> swipes = new ArrayList<>();

  private SwipeLayout swipeLayout() {
    SwipeLayout layout = new SwipeLayout(ViewTestSupport.themedContext());
    layout.setOnSwipeListener(
        new SwipeLayout.OnSwipeListener() {
          @Override
          public void onSwipeLeft() {
            swipes.add("left");
          }

          @Override
          public void onSwipeRight() {
            swipes.add("right");
          }
        });
    return layout;
  }

  private static void fling(SwipeLayout layout, float fromX, float toX, long durationMs) {
    MotionEvent down = event(0, MotionEvent.ACTION_DOWN, fromX, 50);
    MotionEvent move = event(durationMs / 2, MotionEvent.ACTION_MOVE, (fromX + toX) / 2, 50);
    MotionEvent up = event(durationMs, MotionEvent.ACTION_UP, toX, 50);
    try {
      layout.dispatchTouchEvent(down);
      layout.dispatchTouchEvent(move);
      layout.dispatchTouchEvent(up);
    } finally {
      down.recycle();
      move.recycle();
      up.recycle();
    }
  }

  @Test
  public void aFastLongSwipeLeftIsReported() {
    SwipeLayout layout = swipeLayout();
    assertNotNull(layout.getOnSwipeListener());
    fling(layout, 400, 100, 100);
    assertEquals(List.of("left"), swipes);
  }

  @Test
  public void aFastLongSwipeRightIsReportedUnlessItStartsAtTheLeftEdge() {
    SwipeLayout layout = swipeLayout();
    fling(layout, 150, 450, 100);
    assertEquals(List.of("right"), swipes);
    swipes.clear();
    // starting inside the left margin is the drawer's gesture
    fling(layout, 10, 310, 100);
    assertTrue(swipes.isEmpty());
  }

  @Test
  public void aShortOrVerticalMovementIsNotASwipe() {
    SwipeLayout layout = swipeLayout();
    fling(layout, 200, 150, 50);
    assertTrue(swipes.isEmpty());
  }

  @Test
  public void swipesWithoutAListenerAreIgnored() {
    SwipeLayout layout = new SwipeLayout(ViewTestSupport.themedContext(), null);
    fling(layout, 400, 100, 100);
    assertNull(layout.getOnSwipeListener());
  }

  @Test
  public void theGestureAdapterHandlesMissingEventsAndVerticalFlings() {
    List<String> seen = new ArrayList<>();
    SwipeLayout.SwipeGestureAdapter adapter =
        new SwipeLayout.SwipeGestureAdapter() {
          @Override
          public void onSwipeLeft() {
            seen.add("left");
          }

          @Override
          public void onSwipeRight() {
            seen.add("right");
          }
        };
    MotionEvent down = event(0, MotionEvent.ACTION_DOWN, 400, 50);
    MotionEvent end = event(100, MotionEvent.ACTION_UP, 100, 50);
    MotionEvent vertical = event(100, MotionEvent.ACTION_UP, 400, 300);
    try {
      assertFalse(adapter.onFling(null, end, -500, 0));
      assertTrue(seen.isEmpty());
      adapter.onDown(down);
      // without a first event the last down is used
      adapter.onFling(null, end, -500, 0);
      assertEquals(List.of("left"), seen);
      assertFalse(adapter.onFling(down, null, -500, 0));
      adapter.onFling(down, vertical, 0, 500);
      assertEquals(1, seen.size());
      // too slow
      adapter.onFling(down, end, -10, 0);
      assertEquals(1, seen.size());
    } finally {
      down.recycle();
      end.recycle();
      vertical.recycle();
    }
  }

  // ---- SafeDrawerLayout default reporter

  @Test
  public void theDefaultDrawerReporterRecordsANonFatalWithTheCause() {
    RuntimeException failure = new RuntimeException("did not set the measured dimension");
    FirebaseCrashlytics crashlytics = mock(FirebaseCrashlytics.class);
    try (MockedStatic<FirebaseCrashlytics> firebase = mockStatic(FirebaseCrashlytics.class)) {
      firebase.when(FirebaseCrashlytics::getInstance).thenReturn(crashlytics);

      SafeDrawerLayout.reporterForTesting().accept(failure);

      ArgumentCaptor<Throwable> recorded = ArgumentCaptor.forClass(Throwable.class);
      verify(crashlytics).recordException(recorded.capture());
      assertSame(failure, recorded.getValue().getCause());
      assertEquals("DrawerMeasureFailed", recorded.getValue().getClass().getSimpleName());
    }
  }

  @Test
  public void theDefaultDrawerReporterSurvivesCrashlyticsFailing() {
    try (MockedStatic<FirebaseCrashlytics> firebase = mockStatic(FirebaseCrashlytics.class)) {
      firebase
          .when(FirebaseCrashlytics::getInstance)
          .thenThrow(new IllegalStateException("not initialized"));

      SafeDrawerLayout.reporterForTesting().accept(new RuntimeException("x"));

      firebase.verify(FirebaseCrashlytics::getInstance);
    }
  }
}
