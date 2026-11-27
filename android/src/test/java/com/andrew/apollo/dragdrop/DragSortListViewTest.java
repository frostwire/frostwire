/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.dragdrop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;
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
 * Drives a laid-out {@link DragSortListView} with real touch events: the drag-handle hit test, the
 * float view, the drop and remove callbacks, and the guards that keep a drag from starting where it
 * must not.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DragSortListViewTest {

  private static final int ITEM_HEIGHT = 100;
  private static final int HANDLE_WIDTH = 60;
  private static final int LIST_WIDTH = 300;
  private static final int LIST_HEIGHT = 500;

  /** Items of a fixed height with a drag handle on the left, like the track list's rows. */
  private static final class RowAdapter extends BaseAdapter {
    int count;

    RowAdapter(int count) {
      this.count = count;
    }

    @Override
    public int getCount() {
      return count;
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

  private Activity activity;
  private DragSortListView list;
  private RowAdapter adapter;
  private final List<int[]> drops = new ArrayList<>();
  private final List<Integer> removes = new ArrayList<>();

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
    activity.setTheme(R.style.Theme_FrostWire);
    list = new DragSortListView(activity, null);
    adapter = new RowAdapter(10);
    list.setAdapter(adapter);
    list.setDropListener((from, to) -> drops.add(new int[] {from, to}));
    list.setRemoveListener(removes::add);
    activity.setContentView(list, new ViewGroup.LayoutParams(LIST_WIDTH, LIST_HEIGHT));
    layoutAgain();
  }

  /** A touch at (x, y) in the list's own coordinates, with the raw position on the screen. */
  private boolean touch(int action, float x, float y) {
    int[] origin = new int[2];
    list.getLocationOnScreen(origin);
    long now = SystemClock.uptimeMillis();
    MotionEvent ev = MotionEvent.obtain(now, now, action, x + origin[0], y + origin[1], 0);
    ev.offsetLocation(-origin[0], -origin[1]);
    return list.dispatchTouchEvent(ev);
  }

  private void layoutAgain() {
    list.measure(
        View.MeasureSpec.makeMeasureSpec(LIST_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(LIST_HEIGHT, View.MeasureSpec.EXACTLY));
    list.layout(0, 0, LIST_WIDTH, LIST_HEIGHT);
  }

  @Test
  public void theInputAdapterIsTheOneTheCallerSet() {
    assertSame(adapter, list.getInputAdapter());
    assertNull(new DragSortListView(activity, null).getInputAdapter());
  }

  @Test
  public void floatAlphaFollowsTheSetter() {
    list.setFloatAlpha(0.25f);
    assertEquals(0.25f, list.getFloatAlpha(), 0f);
  }

  @Test
  public void dragCanBeDisabled() {
    assertTrue(list.isDragEnabled());
    list.setDragEnabled(false);
    assertFalse(list.isDragEnabled());
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    touch(MotionEvent.ACTION_UP, 10, 150);
    assertTrue(drops.isEmpty());
  }

  @Test
  public void aDragOutsideATouchGestureDoesNotStart() {
    assertFalse(list.startDrag(1, 0, 0, 0));
    assertFalse(list.stopDrag(false));
  }

  @Test
  public void touchingAHandleStartsADragAndLiftingDropsOnTheSameSlot() {
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 150);

    assertEquals(1, drops.size());
    assertEquals(1, drops.get(0)[0]);
    assertEquals(1, drops.get(0)[1]);
    assertTrue(removes.isEmpty());
  }

  @Test
  public void draggingDownMovesTheItemToTheSlotUnderTheFinger() {
    touch(MotionEvent.ACTION_DOWN, 10, 50);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 260);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 260);

    assertEquals(1, drops.size());
    assertEquals(0, drops.get(0)[0]);
    assertEquals(2, drops.get(0)[1]);
  }

  @Test
  public void draggingUpMovesTheItemToTheSlotUnderTheFinger() {
    touch(MotionEvent.ACTION_DOWN, 10, 350);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 250);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 140);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 140);

    assertEquals(1, drops.size());
    assertEquals(3, drops.get(0)[0]);
    assertEquals(1, drops.get(0)[1]);
  }

  @Test
  public void aTouchOutsideTheHandleDoesNotStartADrag() {
    touch(MotionEvent.ACTION_DOWN, 200, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 200, 300);
    touch(MotionEvent.ACTION_UP, 200, 300);

    assertTrue(drops.isEmpty());
    assertTrue(removes.isEmpty());
  }

  @Test
  public void cancellingTheTouchEndsTheDrag() {
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_CANCEL, 10, 150);

    assertEquals(1, drops.size());
    touch(MotionEvent.ACTION_DOWN, 10, 250);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 250);
    assertEquals(2, drops.size());
    assertEquals(2, drops.get(1)[0]);
  }

  @Test
  public void slidingTheFloatIntoTheLastThirdRemovesTheItem() {
    DragSortController controller =
        new DragSortController(
            list,
            R.id.edit_track_list_item_handle,
            DragSortController.ON_DOWN,
            DragSortController.SLIDE_RIGHT_REMOVE);
    controller.setRemoveEnabled(true);
    list.setFloatViewManager(controller);
    list.setOnTouchListener(controller);

    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 280, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 280, 150);

    assertEquals(1, removes.size());
    assertEquals(1, (int) removes.get(0));
    assertTrue(drops.isEmpty());
  }

  @Test
  public void stoppingADragWithRemoveCallsTheRemoveListener() {
    touch(MotionEvent.ACTION_DOWN, 10, 250);
    layoutAgain();

    assertTrue(list.stopDrag(true));

    assertEquals(1, removes.size());
    assertEquals(2, (int) removes.get(0));
    assertFalse("the drag is over", list.stopDrag(true));
  }

  @Test
  public void aChangeOfTheDataCancelsTheDragInProgress() {
    touch(MotionEvent.ACTION_DOWN, 10, 250);
    layoutAgain();

    adapter.count = 5;
    adapter.notifyDataSetChanged();

    assertFalse("the drag was already stopped", list.stopDrag(false));
    assertEquals("the item lands where it was", 1, drops.size());
    assertEquals(2, drops.get(0)[0]);
    assertEquals(2, drops.get(0)[1]);
  }

  @Test
  public void invalidatingTheDataCancelsTheDragInProgress() {
    touch(MotionEvent.ACTION_DOWN, 10, 250);
    layoutAgain();

    adapter.notifyDataSetInvalidated();

    assertFalse(list.stopDrag(false));
  }

  /** Accepts any float view, unlike the default manager, which expects its own ImageView. */
  private static final DragSortListView.FloatViewManager ANY_VIEW =
      new DragSortListView.FloatViewManager() {
        @Override
        public View onCreateFloatView(int position) {
          return null;
        }

        @Override
        public void onDragFloatView(View floatView, Point location, Point touch) {}

        @Override
        public void onDestroyFloatView(View floatView) {}
      };

  @Test
  public void startingADragWithACustomFloatViewHidesTheSourceItem() {
    list.setFloatViewManager(ANY_VIEW);
    touch(MotionEvent.ACTION_DOWN, 200, 250);
    layoutAgain();

    assertTrue(
        list.startDrag(
            2,
            new TextView(activity),
            DragSortListView.DRAG_POS_Y | DragSortListView.DRAG_NEG_Y,
            0,
            0));

    assertEquals(View.INVISIBLE, list.getChildAt(2).getVisibility());
    assertFalse(
        "a second drag cannot start while one runs",
        list.startDrag(3, new TextView(activity), 0, 0, 0));
    assertTrue(list.stopDrag(false));
    assertEquals(1, drops.size());
  }

  @Test
  public void aNullFloatViewDoesNotStartADrag() {
    touch(MotionEvent.ACTION_DOWN, 200, 250);
    assertFalse(list.startDrag(2, null, 0, 0, 0));
  }

  @Test
  public void aFloatViewManagerReturningNullDoesNotStartADrag() {
    list.setFloatViewManager(ANY_VIEW);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 150);

    assertTrue(drops.isEmpty());
  }

  @Test
  public void aDragSortListenerReceivesDropAndRemove() {
    final List<String> events = new ArrayList<>();
    list.setDragSortListener(
        new DragSortListView.DragSortListener() {
          @Override
          public void drag(int from, int to) {
            events.add("drag");
          }

          @Override
          public void drop(int from, int to) {
            events.add("drop " + from + ">" + to);
          }

          @Override
          public void remove(int which) {
            events.add("remove " + which);
          }
        });

    touch(MotionEvent.ACTION_DOWN, 10, 50);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 50);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();
    list.stopDrag(true);

    assertEquals(List.of("drop 0>0", "remove 1"), events);
  }

  @Test
  public void withoutARemoveListenerStoppingDoesNotRemove() {
    list.setRemoveListener(null);
    touch(MotionEvent.ACTION_DOWN, 10, 150);
    layoutAgain();

    assertTrue(list.stopDrag(true));

    assertTrue(removes.isEmpty());
  }

  @Test
  public void draggingNearTheEdgesScrollsWithoutLosingTheItem() {
    list.setDragScrollStart(0.9f);
    list.setDragScrollStarts(0.1f, 0.2f);
    list.setMaxScrollSpeed(1f);
    list.setDragScrollProfile((w, t) -> 0f);
    list.setDragScrollProfile(null);
    layoutAgain();
    touch(MotionEvent.ACTION_DOWN, 10, 450);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 495);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 5);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 250);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 250);

    assertEquals(1, drops.size());
    assertEquals(4, drops.get(0)[0]);
  }

  @Test
  public void aCustomFloatViewIsRemovedWhenTheDragEnds() {
    list.setFloatViewManager(ANY_VIEW);
    touch(MotionEvent.ACTION_DOWN, 200, 150);
    layoutAgain();

    assertTrue(list.startDrag(1, new TextView(activity), DragSortListView.DRAG_POS_X, 0, 0));
    assertTrue(list.stopDrag(true));

    assertEquals(1, removes.size());
  }

  private void drawIt() {
    android.graphics.Bitmap bitmap =
        android.graphics.Bitmap.createBitmap(
            LIST_WIDTH, LIST_HEIGHT, android.graphics.Bitmap.Config.ARGB_8888);
    list.draw(new android.graphics.Canvas(bitmap));
  }

  @Test
  public void theFloatViewAndTheDropSlotAreDrawnDuringTheDrag() {
    list.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.RED));
    list.setDividerHeight(4);
    touch(MotionEvent.ACTION_DOWN, 10, 50);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 280);
    layoutAgain();
    drawIt();
    touch(MotionEvent.ACTION_MOVE, 10, 120);
    layoutAgain();
    drawIt();
    touch(MotionEvent.ACTION_UP, 10, 120);
    layoutAgain();
    drawIt();

    assertEquals(1, drops.size());
    assertEquals(0, drops.get(0)[0]);
  }

  @Test
  public void dragListenerHearsEverySlotTheItemPassesOver() {
    final List<int[]> hovers = new ArrayList<>();
    list.setDragListener((from, to) -> hovers.add(new int[] {from, to}));
    touch(MotionEvent.ACTION_DOWN, 10, 50);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 160);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 360);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 360);

    assertFalse(hovers.isEmpty());
    assertEquals(0, hovers.get(0)[0]);
    assertEquals(1, hovers.get(0)[1]);
    assertEquals(3, hovers.get(hovers.size() - 1)[1]);
    assertEquals(3, drops.get(0)[1]);
  }

  @Test
  public void headersAndFootersCannotBeDraggedOverOrDropped() {
    TextView header = new TextView(activity);
    header.setText("header");
    header.setLayoutParams(new android.widget.AbsListView.LayoutParams(LIST_WIDTH, 50));
    TextView footer = new TextView(activity);
    footer.setLayoutParams(new android.widget.AbsListView.LayoutParams(LIST_WIDTH, 50));
    DragSortListView withEdges = new DragSortListView(activity, null);
    withEdges.addHeaderView(header, null, false);
    withEdges.addFooterView(footer, null, false);
    withEdges.setAdapter(new RowAdapter(3));
    final List<int[]> localDrops = new ArrayList<>();
    withEdges.setDropListener((from, to) -> localDrops.add(new int[] {from, to}));
    activity.setContentView(withEdges, new ViewGroup.LayoutParams(LIST_WIDTH, LIST_HEIGHT));
    withEdges.measure(
        View.MeasureSpec.makeMeasureSpec(LIST_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(LIST_HEIGHT, View.MeasureSpec.EXACTLY));
    withEdges.layout(0, 0, LIST_WIDTH, LIST_HEIGHT);

    int[] origin = new int[2];
    withEdges.getLocationOnScreen(origin);
    // the first row sits below the 50px header; drag it past the footer
    float[] ys = {75, 120, 400, 20};
    for (int i = 0; i < ys.length; i++) {
      int action = i == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_MOVE;
      long now = SystemClock.uptimeMillis();
      MotionEvent ev = MotionEvent.obtain(now, now, action, 10 + origin[0], ys[i] + origin[1], 0);
      ev.offsetLocation(-origin[0], -origin[1]);
      withEdges.dispatchTouchEvent(ev);
      withEdges.measure(
          View.MeasureSpec.makeMeasureSpec(LIST_WIDTH, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(LIST_HEIGHT, View.MeasureSpec.EXACTLY));
      withEdges.layout(0, 0, LIST_WIDTH, LIST_HEIGHT);
    }
    long now = SystemClock.uptimeMillis();
    MotionEvent up =
        MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 10 + origin[0], 20 + origin[1], 0);
    up.offsetLocation(-origin[0], -origin[1]);
    withEdges.dispatchTouchEvent(up);

    assertEquals(1, localDrops.size());
    assertEquals("the dragged item is the first row, not the header", 0, localDrops.get(0)[0]);
    assertEquals("dropping above the first row keeps it first", 0, localDrops.get(0)[1]);
  }

  @Test
  public void aLongListScrollsUnderADragNearTheBottomEdge() {
    adapter.count = 60;
    adapter.notifyDataSetChanged();
    layoutAgain();
    list.setMaxScrollSpeed(5f);
    touch(MotionEvent.ACTION_DOWN, 10, 50);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 250);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 470);
    layoutAgain();
    org.robolectric.shadows.ShadowLooper.idleMainLooper(
        300, java.util.concurrent.TimeUnit.MILLISECONDS);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 30);
    layoutAgain();
    org.robolectric.shadows.ShadowLooper.idleMainLooper(
        300, java.util.concurrent.TimeUnit.MILLISECONDS);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 250);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 250);

    assertEquals(1, drops.size());
    assertEquals(0, drops.get(0)[0]);
  }

  @Test
  public void aRowViewIsReusedWhenTheListAsksForTheSamePositionAgain() {
    View first = list.getAdapter().getView(0, null, list);
    View again = list.getAdapter().getView(1, first, list);

    assertSame("the wrapper row is recycled", first, again);
  }

  @Test
  public void draggingToTheLastVisibleRowOfALongListMeasuresTheRowsBelow() {
    adapter.count = 20;
    adapter.notifyDataSetChanged();
    layoutAgain();

    touch(MotionEvent.ACTION_DOWN, 10, 350);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 420);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 480);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 495);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, 495);

    assertEquals(1, drops.size());
    assertEquals(3, drops.get(0)[0]);
    assertTrue("it moved down: " + drops.get(0)[1], drops.get(0)[1] >= 4);
  }

  @Test
  public void draggingAboveTheFirstRowKeepsTheItemInTheList() {
    touch(MotionEvent.ACTION_DOWN, 10, 250);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, 120);
    layoutAgain();
    touch(MotionEvent.ACTION_MOVE, 10, -50);
    layoutAgain();
    touch(MotionEvent.ACTION_UP, 10, -50);

    assertEquals(1, drops.size());
    assertEquals(2, drops.get(0)[0]);
    assertEquals(0, drops.get(0)[1]);
  }
}
