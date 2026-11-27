/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The profile header carousel (two tabs that scroll sideways and slide up with the list): tab
 * selection and dimming, its listener, the stored vertical position of each tab and its measuring.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ProfileTabCarouselTest {

  @org.junit.Rule
  public final com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages cleanAppMessages =
      new com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages();

  private Activity activity;
  private ProfileTabCarousel carousel;
  private final List<String> events = new ArrayList<>();

  @Before
  public void setUp() {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
    activity = Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
    activity.setTheme(R.style.Theme_FrostWire);
    carousel =
        (ProfileTabCarousel)
            LayoutInflater.from(activity).inflate(R.layout.profile_tab_carousel, null);
    carousel.setListener(
        new ProfileTabCarousel.Listener() {
          @Override
          public void onTouchDown() {
            events.add("down");
          }

          @Override
          public void onTouchUp() {
            events.add("up");
          }

          @Override
          public void onScrollChanged(int l, int t, int oldl, int oldt) {
            events.add("scroll " + l + " " + oldl);
          }

          @Override
          public void onTabSelected(int position) {
            events.add("tab " + position);
          }
        });
    activity.setContentView(carousel);
    shadowOf(Looper.getMainLooper()).idle();
  }

  private CarouselTab tab(int id) {
    return carousel.findViewById(id);
  }

  private static int exactly(int size) {
    return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
  }

  private void measureAndLayout(int width) {
    carousel.measure(
        exactly(width), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    carousel.layout(0, 0, carousel.getMeasuredWidth(), carousel.getMeasuredHeight());
  }

  @Test
  public void selectingATabMarksItAndUnmarksTheOther() {
    CarouselTab first = tab(R.id.profile_tab_carousel_tab_one);
    CarouselTab second = tab(R.id.profile_tab_carousel_tab_two);

    carousel.setCurrentTab(1);

    assertTrue(second.isSelected());
    assertFalse(first.isSelected());
    assertEquals(
        "the selected tab's colorstrip shows",
        View.VISIBLE,
        second.findViewById(R.id.profile_tab_colorstrip).getVisibility());
    assertEquals(View.GONE, first.findViewById(R.id.profile_tab_colorstrip).getVisibility());
    assertTrue(second.findViewById(R.id.profile_tab_label).isSelected());
    assertFalse(first.findViewById(R.id.profile_tab_label).isSelected());

    carousel.setCurrentTab(0);

    assertTrue(first.isSelected());
    assertFalse(second.isSelected());
  }

  @Test
  public void theOnlyTabsAreTheFirstAndTheSecond() {
    try {
      carousel.setCurrentTab(2);
      fail("a third tab does not exist");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("2"));
    }
  }

  @Test
  public void tappingTheOtherTabReportsItAndTheSelectedTabIgnoresTaps() {
    carousel.setCurrentTab(0);
    CarouselTab first = tab(R.id.profile_tab_carousel_tab_one);
    CarouselTab second = tab(R.id.profile_tab_carousel_tab_two);

    // the overlay on a tab is its first child; only the unselected tab is clickable
    ((FrameLayoutWithOverlay) second).getChildAt(second.getChildCount() - 1);
    View secondOverlay = overlayOf(second);
    View firstOverlay = overlayOf(first);
    assertTrue(secondOverlay.performClick());
    assertFalse("the selected tab is not clickable", firstOverlay.isClickable());

    assertEquals(List.of("tab 1"), events);
  }

  private static View overlayOf(FrameLayoutWithOverlay tab) {
    return ((AlphaTouchInterceptorOverlay) tab.getChildAt(tab.getChildCount() - 1)).getChildAt(0);
  }

  @Test
  public void aTabKeepsItsOwnVerticalPositionForTheNextVisit() {
    carousel.storeYCoordinate(0, -40f);
    carousel.storeYCoordinate(1, -75f);

    assertEquals(-40f, carousel.getStoredYCoordinateForTab(0), 0f);
    assertEquals(-75f, carousel.getStoredYCoordinateForTab(1), 0f);
  }

  @Test
  public void movingToAYCoordinateStoresItAndSlidesTheCarouselThere() {
    carousel.moveToYCoordinate(0, -60f);
    ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);

    assertEquals(-60f, carousel.getStoredYCoordinateForTab(0), 0f);
    assertEquals(-60f, carousel.getY(), 0.5f);
    assertFalse("a zero length move is done", carousel.isTabCarouselIsAnimating());
  }

  @Test
  public void restoringAYCoordinateAnimatesAndReportsItWhileItRuns() {
    carousel.storeYCoordinate(1, -30f);

    carousel.restoreYCoordinate(200, 1);
    assertTrue("animating", carousel.isTabCarouselIsAnimating());

    ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
    assertFalse(carousel.isTabCarouselIsAnimating());
    assertEquals(-30f, carousel.getY(), 0.5f);
  }

  @Test
  public void resetScrollsBackAndSelectsTheFirstTab() {
    carousel.setCurrentTab(1);
    carousel.storeYCoordinate(0, -10f);

    carousel.reset();
    ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);

    assertTrue(tab(R.id.profile_tab_carousel_tab_one).isSelected());
    assertEquals(0f, carousel.getStoredYCoordinateForTab(0), 0f);
    assertEquals(0, carousel.getScrollX());
  }

  @Test
  public void theCarouselIsSizedFromTheScreenWidth() {
    carousel.setArtistProfileHeader(activity, null);
    measureAndLayout(1000);

    int tabHeight = carousel.getMeasuredHeight();
    assertTrue("a header taller than the labels: " + tabHeight, tabHeight > 0);
    assertEquals(1000, carousel.getMeasuredWidth());
    assertEquals(
        tabHeight
            - activity.getResources().getDimensionPixelSize(R.dimen.profile_carousel_label_height),
        carousel.getAllowedVerticalScrollLength());
    int expectedScroll =
        2
                * Math.round(
                    activity
                            .getResources()
                            .getFraction(R.fraction.tab_width_screen_percentage, 1, 1)
                        * 1000)
            - 1000;
    assertEquals(expectedScroll, carousel.getAllowedHorizontalScrollLength());
  }

  @Test
  public void scrollingSidewaysTellsTheListenerInTheListsOwnScale() {
    carousel.setArtistProfileHeader(activity, null);
    measureAndLayout(1000);
    events.clear();

    carousel.scrollTo(30, 0);

    assertEquals(1, events.size());
    assertTrue(events.get(0), events.get(0).startsWith("scroll "));
    events.clear();
    carousel.scrollTo(30, 0);
    assertTrue("no change, no callback", events.isEmpty());
  }

  @Test
  public void touchingTheHeaderTellsTheListener() {
    long now = SystemClock.uptimeMillis();

    assertTrue(
        carousel.onTouch(
            carousel, MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1f, 1f, 0)));
    assertTrue(
        carousel.onTouch(carousel, MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1f, 1f, 0)));
    carousel.onTouch(carousel, MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1f, 1f, 0));

    assertEquals(List.of("down", "up"), events);
  }

  @Test
  public void aSidewaysDragInterceptsAndTellsTheListener() {
    carousel.setArtistProfileHeader(activity, null);
    measureAndLayout(1000);
    long now = SystemClock.uptimeMillis();
    events.clear();

    carousel.onInterceptTouchEvent(
        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 500f, 50f, 0));
    boolean intercepted =
        carousel.onInterceptTouchEvent(
            MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 100f, 50f, 0));

    assertEquals(intercepted, events.contains("down"));
  }

  @Test
  public void restoringATabSelectsItAndScrollsThereAfterTheNextLayout() {
    carousel.setArtistProfileHeader(activity, null);
    measureAndLayout(1000);

    carousel.restoreCurrentTab(1);
    assertTrue(tab(R.id.profile_tab_carousel_tab_two).isSelected());
    carousel.measure(
        exactly(1000), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    carousel.layout(0, 0, 1001, carousel.getMeasuredHeight());
    carousel.getViewTreeObserver().dispatchOnGlobalLayout();

    assertEquals(carousel.getAllowedHorizontalScrollLength(), carousel.getScrollX());
  }

  @Test
  public void anAlbumOrPlaylistHeaderHidesTheSecondTab() {
    carousel.setPlaylistOrGenreProfileHeader(activity, () -> new long[0]);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(View.GONE, tab(R.id.profile_tab_carousel_tab_two).getVisibility());
    assertEquals(
        activity.getString(R.string.page_songs),
        ((TextView) tab(R.id.profile_tab_carousel_tab_one).findViewById(R.id.profile_tab_label))
            .getText()
            .toString());
    assertNotNull(tab(R.id.profile_tab_carousel_tab_one));
  }

  @Test
  public void aSecondSlideCancelsTheFirstAndTheCarouselKeepsReportingItself() {
    carousel.storeYCoordinate(0, -20f);
    carousel.storeYCoordinate(1, -50f);

    carousel.restoreYCoordinate(300, 0);
    carousel.restoreYCoordinate(300, 1);
    assertTrue(carousel.isTabCarouselIsAnimating());
    ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);

    assertFalse(carousel.isTabCarouselIsAnimating());
    assertEquals(-50f, carousel.getY(), 0.5f);
  }
}
