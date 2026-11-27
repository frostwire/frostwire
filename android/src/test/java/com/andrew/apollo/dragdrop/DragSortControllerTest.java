/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.dragdrop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * {@link DragSortController}: how a drag starts (touch, drag or long press), which movements it
 * allows and when a gesture removes the item; and {@link SimpleFloatViewManager}'s float view.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DragSortControllerTest {

  private static final int ITEM_HEIGHT = 100;
  private static final int HANDLE_WIDTH = 60;
  private static final int LIST_WIDTH = 300;
  private static final int LIST_HEIGHT = 500;

  /** Rows of a fixed height with a drag handle on the left. */
  private static final class RowAdapter extends BaseAdapter {
    @Override
    public int getCount() {
      return 10;
    }

    @Override
    public Object getItem(int position) {
      return position;
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      FrameLayout row = convertView instanceof FrameLayout ? (FrameLayout) convertView : null;
      if (row == null) {
        row = new FrameLayout(parent.getContext());
        row.setLayoutParams(new ViewGroup.LayoutParams(LIST_WIDTH, ITEM_HEIGHT));
        View handle = new View(parent.getContext());
        handle.setId(R.id.edit_track_list_item_handle);
        row.addView(handle, new FrameLayout.LayoutParams(HANDLE_WIDTH, ITEM_HEIGHT));
      }
      return row;
    }
  }

  private DragSortListView list;
  private final List<int[]> drops = new ArrayList<>();
  private final List<Integer> removes = new ArrayList<>();

  @Before
  public void setUp() {
    Activity activity =
        Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
    activity.setTheme(R.style.Theme_FrostWire);
    list = new DragSortListView(activity, null);
    list.setAdapter(new RowAdapter());
    list.setDropListener((from, to) -> drops.add(new int[] {from, to}));
    list.setRemoveListener(removes::add);
    activity.setContentView(list, new ViewGroup.LayoutParams(LIST_WIDTH, LIST_HEIGHT));
    layoutAgain();
  }

  private void layoutAgain() {
    list.measure(
        View.MeasureSpec.makeMeasureSpec(LIST_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(LIST_HEIGHT, View.MeasureSpec.EXACTLY));
    list.layout(0, 0, LIST_WIDTH, LIST_HEIGHT);
  }

  /** An event at (x, y) in the list's coordinates whose raw position is on the screen. */
  private MotionEvent event(int action, long time, float x, float y) {
    int[] origin = new int[2];
    list.getLocationOnScreen(origin);
    MotionEvent e = MotionEvent.obtain(time, time, action, x + origin[0], y + origin[1], 0);
    e.offsetLocation(-origin[0], -origin[1]);
    return e;
  }

  private MotionEvent event(int action, float x, float y) {
    return event(action, SystemClock.uptimeMillis(), x, y);
  }

  private boolean touch(int action, float x, float y) {
    return list.dispatchTouchEvent(event(action, x, y));
  }

  /** A gesture whose events are {@code step} ms apart: fast enough to be a fling. */
  private void gesture(long step, float startX, float... nextXs) {
    long t = SystemClock.uptimeMillis();
    int[] origin = new int[2];
    list.getLocationOnScreen(origin);
    list.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, t, startX, 150));
    layoutAgain();
    for (int i = 0; i < nextXs.length; i++) {
      boolean last = i == nextXs.length - 1;
      list.dispatchTouchEvent(
          event(
              last ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
              t + step * (i + 1),
              nextXs[i],
              150));
    }
  }

  private DragSortController controller(int initMode, int removeMode) {
    DragSortController c =
        new DragSortController(list, R.id.edit_track_list_item_handle, initMode, removeMode);
    list.setFloatViewManager(c);
    list.setOnTouchListener(c);
    return c;
  }

  @Test
  public void defaultsAreSortOnlyStartingOnTouch() {
    DragSortController c = new DragSortController(list);
    assertEquals(DragSortController.ON_DOWN, c.getDragInitMode());
    assertEquals(DragSortController.FLING_RIGHT_REMOVE, c.getRemoveMode());
    assertTrue(c.isSortEnabled());
    assertFalse(c.isRemoveEnabled());
  }

  @Test
  public void settersAreReadBack() {
    DragSortController c = new DragSortController(list);
    c.setDragInitMode(DragSortController.ON_LONG_PRESS);
    c.setRemoveMode(DragSortController.SLIDE_LEFT_REMOVE);
    c.setSortEnabled(false);
    c.setRemoveEnabled(true);
    assertEquals(DragSortController.ON_LONG_PRESS, c.getDragInitMode());
    assertEquals(DragSortController.SLIDE_LEFT_REMOVE, c.getRemoveMode());
    assertFalse(c.isSortEnabled());
    assertTrue(c.isRemoveEnabled());
  }

  @Test
  public void inDragModeTheDragStartsOnlyOnceTheFingerMoves() {
    controller(DragSortController.ON_DRAG, DragSortController.FLING_RIGHT_REMOVE);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    assertFalse("nothing is dragged on a plain touch", list.stopDrag(false));

    touch(MotionEvent.ACTION_MOVE, 10, 300);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 300);

    assertEquals(1, drops.size());
    assertEquals(1, drops.get(0)[0]);
  }

  @Test
  public void aRemovalOnlyDragNeedsASidewaysMove() {
    DragSortController c =
        controller(DragSortController.ON_DRAG, DragSortController.FLING_RIGHT_REMOVE);
    c.setSortEnabled(false);
    c.setRemoveEnabled(true);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 400);
    assertFalse("a vertical move does not start it", list.stopDrag(false));
    touch(MotionEvent.ACTION_UP, 10, 400);

    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 200, 150);
    layoutAgain();
    assertTrue("a sideways move starts it", list.stopDrag(false));
  }

  @Test
  public void withSortAndRemoveEnabledAnyMoveStartsTheDrag() {
    DragSortController c =
        controller(DragSortController.ON_DRAG, DragSortController.FLING_RIGHT_REMOVE);
    c.setRemoveEnabled(true);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 40, 200);
    layoutAgain();
    assertTrue(list.stopDrag(false));
  }

  @Test
  public void aSortOnlyDragNeedsAVerticalMove() {
    controller(DragSortController.ON_DRAG, DragSortController.FLING_RIGHT_REMOVE);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 200, 150);
    assertFalse("a sideways move does not start it", list.stopDrag(false));
  }

  @Test
  public void aLongPressOnTheHandleStartsTheDragInLongPressMode() {
    DragSortController c =
        controller(DragSortController.ON_LONG_PRESS, DragSortController.FLING_RIGHT_REMOVE);
    touch(MotionEvent.ACTION_DOWN, 10, 250);
    layoutAgain();
    assertFalse("a touch alone does not start it", list.stopDrag(false));

    c.onLongPress(event(MotionEvent.ACTION_DOWN, 10, 250));
    layoutAgain();

    assertTrue(list.stopDrag(false));
    assertEquals(2, drops.get(0)[0]);
  }

  @Test
  public void aLongPressOutsideTheHandleDoesNothing() {
    DragSortController c =
        controller(DragSortController.ON_LONG_PRESS, DragSortController.FLING_RIGHT_REMOVE);
    touch(MotionEvent.ACTION_DOWN, 200, 250);

    c.onLongPress(event(MotionEvent.ACTION_DOWN, 200, 250));

    assertFalse(list.stopDrag(false));
  }

  @Test
  public void slidingLeftRemovesWhenLiftedInTheFirstThird() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.SLIDE_LEFT_REMOVE);
    c.setRemoveEnabled(true);
    touch(MotionEvent.ACTION_DOWN, 40, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 150, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 20, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 20, 150);

    assertEquals(1, removes.size());
    assertEquals(1, (int) removes.get(0));
  }

  @Test
  public void slidingRightFadesTheFloatViewAsItLeavesTheList() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.SLIDE_RIGHT_REMOVE);
    c.setRemoveEnabled(true);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 150);
    float start = list.getFloatAlpha();
    touch(MotionEvent.ACTION_MOVE, 150, 150);
    float middle = list.getFloatAlpha();
    touch(MotionEvent.ACTION_MOVE, 290, 150);
    float end = list.getFloatAlpha();

    assertEquals(1f, start, 0f);
    assertTrue("half way it is partly faded: " + middle, middle > 0f && middle < 1f);
    assertEquals(0f, end, 0f);
  }

  @Test
  public void slidingLeftFadesTheFloatViewAsItLeavesTheList() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.SLIDE_LEFT_REMOVE);
    c.setRemoveEnabled(true);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 290, 150);
    float start = list.getFloatAlpha();
    touch(MotionEvent.ACTION_MOVE, 150, 150);
    float middle = list.getFloatAlpha();
    touch(MotionEvent.ACTION_MOVE, 5, 150);
    float end = list.getFloatAlpha();

    assertEquals(1f, start, 0f);
    assertTrue("half way it is partly faded: " + middle, middle > 0f && middle < 1f);
    assertEquals(0f, end, 0f);
  }

  @Test
  public void aFastFlingRightRemovesInFlingRightMode() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.FLING_RIGHT_REMOVE);
    c.setRemoveEnabled(true);

    gesture(10, 10, 100, 200, 290, 295);

    assertEquals(1, removes.size());
    assertEquals(1, (int) removes.get(0));
    assertTrue(drops.isEmpty());
  }

  @Test
  public void aFastFlingLeftRemovesInFlingLeftMode() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.FLING_LEFT_REMOVE);
    c.setRemoveEnabled(true);

    gesture(10, 55, 45, 35, 20, 5);

    assertEquals(1, removes.size());
  }

  @Test
  public void aFlingTheOtherWayDoesNotRemove() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.FLING_RIGHT_REMOVE);
    c.setRemoveEnabled(true);

    gesture(10, 55, 45, 35, 20, 5);

    assertTrue(removes.isEmpty());
  }

  @Test
  public void aSlowDragDoesNotFlingRemove() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.FLING_RIGHT_REMOVE);
    c.setRemoveEnabled(true);

    gesture(1000, 10, 150, 160);

    assertTrue(removes.isEmpty());
    assertEquals(1, drops.size());
  }

  @Test
  public void withSortDisabledTheItemCannotLeaveItsRow() {
    DragSortController c =
        controller(DragSortController.ON_DOWN, DragSortController.FLING_RIGHT_REMOVE);
    c.setSortEnabled(false);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 400);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 400);

    assertEquals(1, drops.size());
    assertEquals(1, drops.get(0)[1]);
  }

  @Test
  public void theHandleHitTestReportsMissOutsideTheHandleAndTheList() {
    DragSortController c = controller(DragSortController.ON_DOWN, 0);
    assertEquals(
        DragSortController.MISS, c.dragHandleHitPosition(event(MotionEvent.ACTION_DOWN, 200, 150)));
    assertEquals(
        DragSortController.MISS, c.dragHandleHitPosition(event(MotionEvent.ACTION_DOWN, 10, 900)));
    assertEquals(1, c.dragHandleHitPosition(event(MotionEvent.ACTION_DOWN, 10, 150)));
    assertEquals(1, c.startDragPosition(event(MotionEvent.ACTION_DOWN, 10, 150)));
    c.setDragHandleId(12345);
    assertEquals(
        DragSortController.MISS, c.dragHandleHitPosition(event(MotionEvent.ACTION_DOWN, 10, 150)));
  }

  @Test
  public void unusedGestureCallbacksDoNotConsumeTheTouch() {
    DragSortController c = new DragSortController(list);
    MotionEvent e = event(MotionEvent.ACTION_DOWN, 10, 150);
    assertFalse(c.onFling(e, e, 1000f, 0f));
    assertFalse(c.onSingleTapUp(e));
    assertFalse(c.onScroll(e, e, 0f, 0f));
    c.onShowPress(e);
  }

  @Test
  public void theFloatViewIsABitmapCopyOfTheRow() {
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    SimpleFloatViewManager manager = new SimpleFloatViewManager(list);
    manager.setBackgroundColor(Color.BLUE);

    View floatView = manager.onCreateFloatView(1);

    assertTrue(floatView instanceof ImageView);
    assertNotNull(((ImageView) floatView).getDrawable());
    manager.onDragFloatView(floatView, new Point(0, 0), new Point(0, 0));
    manager.onDestroyFloatView(floatView);
    assertNull(((ImageView) floatView).getDrawable());
  }

  @Test
  public void thereIsNoFloatViewForARowThatIsNotOnScreen() {
    assertNull(new SimpleFloatViewManager(list).onCreateFloatView(40));
  }

  /** Destroying a float view that this manager did not create, or twice, used to crash. */
  @Test
  public void destroyingAForeignOrAlreadyDestroyedFloatViewDoesNotThrow() {
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    SimpleFloatViewManager manager = new SimpleFloatViewManager(list);
    View floatView = manager.onCreateFloatView(1);

    manager.onDestroyFloatView(floatView);
    manager.onDestroyFloatView(floatView);
    manager.onDestroyFloatView(new View(list.getContext()));
    new SimpleFloatViewManager(list).onDestroyFloatView(floatView);
  }
}
