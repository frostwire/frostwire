/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import com.frostwire.android.R;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Apollo layout widgets: square views, the alpha overlay, and the scroll listener that moves
 * the profile header with a list.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class LayoutWidgetsTest {

  private Activity activity;

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(Activity.class).create().get();
    activity.setTheme(R.style.Theme_FrostWire);
  }

  private static int exactly(int size) {
    return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
  }

  @Test
  public void aLayoutSuppressingImageViewKeepsTheLayoutRequestToItself() {
    FrameLayout parent = new FrameLayout(activity);
    LayoutSuppressingImageView image = new LayoutSuppressingImageView(activity, null);
    parent.addView(image);
    parent.layout(0, 0, 100, 100);
    assertFalse(parent.isLayoutRequested());

    image.requestLayout();

    assertTrue("the view itself is flagged", image.isLayoutRequested());
    assertFalse("the parent is not asked to relayout", parent.isLayoutRequested());
  }

  @Test
  public void aSquareImageViewIsAsBigAsItsShorterSide() {
    SquareImageView image = new SquareImageView(activity, null);
    image.setImageDrawable(new ColorDrawable(Color.RED));

    image.measure(exactly(300), exactly(120));

    assertEquals(120, image.getMeasuredWidth());
    assertEquals(120, image.getMeasuredHeight());

    image.measure(exactly(80), exactly(200));

    assertEquals(80, image.getMeasuredWidth());
    assertEquals(80, image.getMeasuredHeight());
  }

  @Test
  public void aSquareViewIsAsTallAsItIsWide() {
    SquareView square = new SquareView(activity, null);
    View child = new View(activity);
    square.addView(child, new ViewGroup.LayoutParams(10, 10));

    square.measure(exactly(240), exactly(500));
    square.layout(0, 0, square.getMeasuredWidth(), square.getMeasuredHeight());

    assertEquals(240, square.getMeasuredWidth());
    assertEquals(240, square.getMeasuredHeight());
    assertEquals(240, child.getWidth());
    assertEquals(240, child.getHeight());
  }

  @Test
  public void anEmptySquareViewMeasuresNothing() {
    SquareView square = new SquareView(activity, null);

    square.measure(exactly(240), exactly(240));

    assertEquals(0, square.getMeasuredWidth());
    assertEquals(0, square.getMeasuredHeight());
  }

  @Test
  public void aSquareViewKeepsItsLayoutRequestToItself() {
    FrameLayout parent = new FrameLayout(activity);
    SquareView square = new SquareView(activity, null);
    parent.addView(square);
    parent.layout(0, 0, 100, 100);

    square.requestLayout();

    assertFalse(parent.isLayoutRequested());
  }

  @Test
  public void theOverlayDimsWithTheAlphaLayerValueAndClampsIt() {
    AlphaTouchInterceptorOverlay overlay = new AlphaTouchInterceptorOverlay(activity);

    overlay.setAlphaLayerValue(0.5f);
    assertEquals(0x7F000000, ((ColorDrawable) overlay.getBackground()).getColor());

    overlay.setAlphaLayerValue(7f);
    assertEquals(0xFF000000, ((ColorDrawable) overlay.getBackground()).getColor());

    overlay.setAlphaLayerValue(-1f);
    assertEquals(0, ((ColorDrawable) overlay.getBackground()).getColor());
  }

  @Test
  public void clampKeepsTheValueInsideTheBounds() {
    assertEquals(0f, AlphaTouchInterceptorOverlay.clamp(-3f, 0f, 1f), 0f);
    assertEquals(1f, AlphaTouchInterceptorOverlay.clamp(3f, 0f, 1f), 0f);
    assertEquals(0.25f, AlphaTouchInterceptorOverlay.clamp(0.25f, 0f, 1f), 0f);
  }

  @Test
  public void anotherViewCanBeTheAlphaLayerAndTheOverlayThenClears() {
    AlphaTouchInterceptorOverlay overlay = new AlphaTouchInterceptorOverlay(activity);
    View layer = new View(activity);
    overlay.setAlphaLayerValue(1f);
    assertEquals(0xFF000000, ((ColorDrawable) overlay.getBackground()).getColor());

    overlay.setAlphaLayer(layer);

    assertEquals(
        "the overlay itself is no longer dimmed",
        0,
        ((ColorDrawable) overlay.getBackground()).getColor());
    assertEquals(
        "the new layer carries the alpha",
        0xFF000000,
        ((ColorDrawable) layer.getBackground()).getColor());

    overlay.setAlphaLayer(layer);
    overlay.setAlphaLayer(null);
    overlay.setAlphaLayerValue(1f);
    assertEquals(0xFF000000, ((ColorDrawable) overlay.getBackground()).getColor());
    AlphaTouchInterceptorOverlay.setAlphaOnViewBackground(null, 1f);
  }

  @Test
  public void theOverlayPassesClicksToItsListenerUnlessItIsMadeUnclickable() {
    AlphaTouchInterceptorOverlay overlay = new AlphaTouchInterceptorOverlay(activity);
    final AtomicInteger clicks = new AtomicInteger();
    overlay.setOverlayOnClickListener(v -> clicks.incrementAndGet());
    View interceptor = overlay.getChildAt(0);

    interceptor.performClick();
    assertEquals(1, clicks.get());

    overlay.setOverlayClickable(false);
    assertFalse(interceptor.isClickable());
    overlay.setOverlayClickable(true);
    assertTrue(interceptor.isClickable());
  }

  @Test
  public void theFrameLayoutWithOverlayKeepsItsOverlayOnTop() {
    FrameLayoutWithOverlay frame = new FrameLayoutWithOverlay(activity, null);
    View overlay = frame.getChildAt(0);

    frame.addView(new View(activity));
    frame.addView(new View(activity));

    assertEquals(3, frame.getChildCount());
    assertSame(overlay, frame.getChildAt(2));
  }

  @Test
  public void theFrameLayoutWithOverlayDelegatesToItsOverlay() {
    FrameLayoutWithOverlay frame = new FrameLayoutWithOverlay(activity, null);
    AlphaTouchInterceptorOverlay overlay = (AlphaTouchInterceptorOverlay) frame.getChildAt(0);
    final AtomicInteger clicks = new AtomicInteger();

    frame.setOverlayOnClickListener(v -> clicks.incrementAndGet());
    overlay.getChildAt(0).performClick();
    frame.setOverlayClickable(false);
    frame.setAlphaLayerValue(1f);
    View layer = new View(activity);
    frame.setAlphaLayer(layer);

    assertEquals(1, clicks.get());
    assertFalse(overlay.getChildAt(0).isClickable());
    assertEquals(0xFF000000, ((ColorDrawable) layer.getBackground()).getColor());
    Drawable none = overlay.getBackground();
    assertEquals(0, ((ColorDrawable) none).getColor());
  }

  // ---------------------------------------------------------------------------------------------

  private AbsListView listWithFirstRowAt(float y) {
    AbsListView list = mock(AbsListView.class);
    View row = mock(View.class);
    when(row.getY()).thenReturn(y);
    when(list.getChildAt(0)).thenReturn(row);
    return list;
  }

  @Test
  public void theHeaderFollowsTheFirstRowUpToTheAllowedScroll() {
    ProfileTabCarousel carousel = mock(ProfileTabCarousel.class);
    when(carousel.getAllowedVerticalScrollLength()).thenReturn(200);
    VerticalScrollListener listener = new VerticalScrollListener(null, carousel, 1);
    AbsListView list = listWithFirstRowAt(-50f);

    listener.onScroll(list, 0, 5, 20);

    verify(list).setVerticalScrollBarEnabled(false);
    verify(carousel).moveToYCoordinate(1, -50f);

    AbsListView farList = listWithFirstRowAt(-900f);
    listener.onScroll(farList, 0, 5, 20);
    verify(carousel).moveToYCoordinate(1, -200f);
  }

  @Test
  public void scrollingPastTheFirstRowPinsTheHeaderAtItsLimit() {
    ProfileTabCarousel carousel = mock(ProfileTabCarousel.class);
    when(carousel.getAllowedVerticalScrollLength()).thenReturn(200);
    VerticalScrollListener listener = new VerticalScrollListener(null, carousel, 0);
    AbsListView list = mock(AbsListView.class);
    when(list.getChildAt(3)).thenReturn(mock(View.class));

    listener.onScroll(list, 3, 5, 20);

    verify(list).setVerticalScrollBarEnabled(true);
    verify(carousel).moveToYCoordinate(0, -200f);
  }

  @Test
  public void theHeaderIsLeftAloneWhileItAnimatesOrWhenTheRowIsGone() {
    ProfileTabCarousel animating = mock(ProfileTabCarousel.class);
    when(animating.isTabCarouselIsAnimating()).thenReturn(true);
    new VerticalScrollListener(null, animating, 0).onScroll(listWithFirstRowAt(-10f), 0, 5, 20);
    verify(animating, never()).moveToYCoordinate(0, -10f);

    ProfileTabCarousel idle = mock(ProfileTabCarousel.class);
    AbsListView noRows = mock(AbsListView.class);
    new VerticalScrollListener(null, idle, 0).onScroll(noRows, 0, 0, 0);
    verify(idle, never()).moveToYCoordinate(0, 0f);

    new VerticalScrollListener(null, null, 0).onScroll(listWithFirstRowAt(-10f), 0, 5, 20);
  }

  @Test
  public void scrollStateChangesAreForwardedToTheHeader() {
    VerticalScrollListener.ScrollableHeader header =
        mock(VerticalScrollListener.ScrollableHeader.class);
    AbsListView list = mock(AbsListView.class);

    new VerticalScrollListener(header, null, 0).onScrollStateChanged(list, 2);
    new VerticalScrollListener(null, null, 0).onScrollStateChanged(list, 2);

    verify(header).onScrollStateChanged(list, 2);
  }

  @Test
  public void aSquareViewWhoseChildCannotBeMeasuredMeasuresNothing() {
    SquareView square = new SquareView(activity, null);
    square.addView(
        new View(activity) {
          @Override
          protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            throw new IllegalStateException("cannot measure");
          }
        });

    square.measure(exactly(240), exactly(240));

    assertEquals(0, square.getMeasuredWidth());
    assertEquals(0, square.getMeasuredHeight());
  }
}
