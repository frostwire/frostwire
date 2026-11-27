/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AbstractListAdapterTest {

  /** Records what the adapter asks of its subclass. */
  static final class StringAdapter extends AbstractListAdapter<String> {
    final List<String> populated = new ArrayList<>();
    final List<View> clicked = new ArrayList<>();
    boolean longClickResult;

    StringAdapter(Context c, List<String> items) {
      super(c, R.layout.view_selectable_list_item, items);
    }

    @Override
    protected void populateView(View view, String data) {
      populated.add(data);
    }

    @Override
    protected void onItemClicked(View v) {
      clicked.add(v);
    }

    @Override
    protected boolean onItemLongClicked(View v) {
      return longClickResult;
    }
  }

  private Context context;
  private StringAdapter adapter;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    context = ViewTestSupport.themedContext();
    adapter = new StringAdapter(context, Arrays.asList("a", "b", "c"));
  }

  @Test
  public void startsWithTheGivenItemsInBothLists() {
    assertEquals(3, adapter.getCount());
    assertEquals(3, adapter.getTotalCount());
    assertEquals("b", adapter.getItem(1));
    assertNull(adapter.getItem(3));
    assertEquals(2, adapter.getItemPosition("c"));
    assertEquals(-1, adapter.getItemPosition("zzz"));
    assertEquals(1L, adapter.getItemId(1));
    assertFalse(adapter.isEmpty());
    assertTrue(adapter.hasStableIds());
    assertTrue(adapter.areAllItemsEnabled());
    assertTrue(adapter.isEnabled(0));
    assertEquals(0, adapter.getItemViewType(0));
    assertEquals(1, adapter.getViewTypeCount());
    assertSame(context, adapter.getContext());
    assertEquals(R.layout.view_selectable_list_item, adapter.getViewItemId());
    assertNull(adapter.getCheckedSum());
  }

  @Test
  public void emptyConstructorStartsEmpty() {
    AbstractListAdapter<String> empty =
        new AbstractListAdapter<String>(context, R.layout.view_selectable_list_item) {
          @Override
          protected void populateView(View view, String data) {}
        };
    assertTrue(empty.isEmpty());
    empty.addItem("x");
    assertEquals(1, empty.getCount());
    empty.addItem("hidden", false);
    assertEquals(1, empty.getCount());
    assertEquals(2, empty.getTotalCount());
  }

  @Test
  public void addListAndDeleteAndClearKeepListsInSync() {
    adapter.addList(Arrays.asList("d", "e"));
    assertEquals(5, adapter.getCount());
    adapter.addToFullList(Arrays.asList("f"));
    assertEquals(5, adapter.getCount());
    assertEquals(6, adapter.getTotalCount());
    adapter.getChecked().add("a");
    adapter.deleteItem("a");
    assertEquals(4, adapter.getCount());
    assertEquals(5, adapter.getFullList().size());
    assertFalse(adapter.getChecked().contains("a"));
    adapter.clear();
    assertTrue(adapter.isEmpty());
    assertEquals(0, adapter.getTotalCount());
    assertEquals(0, adapter.getCheckedCount());
  }

  @Test
  public void checkAllAndSetCheckedAndClearChecked() {
    adapter.checkAll();
    assertEquals(3, adapter.getCheckedCount());
    adapter.setChecked(new HashSet<>(Arrays.asList("a")));
    assertEquals(1, adapter.getCheckedCount());
    adapter.setChecked(1, true);
    adapter.setChecked(1, true);
    assertEquals(2, adapter.getCheckedCount());
    adapter.setChecked(0, false);
    adapter.setChecked(0, false);
    assertEquals(1, adapter.getCheckedCount());
    assertTrue(adapter.getChecked().contains("b"));
    adapter.setChecked(99, true);
    assertEquals(1, adapter.getCheckedCount());
    adapter.clearChecked();
    ShadowLooper.idleMainLooper();
    assertEquals(0, adapter.getCheckedCount());
    adapter.clearChecked();
  }

  @Test
  public void getViewInflatesWrapsAndPopulates() {
    adapter.setCheckboxesVisibility(true);
    adapter.getChecked().add("b");
    View v = adapter.getView(1, null, null);
    assertEquals(Arrays.asList("b"), adapter.populated);
    assertEquals("b", v.getTag());
    CheckBox box = v.findViewById(R.id.view_selectable_list_item_checkbox);
    assertEquals(View.VISIBLE, box.getVisibility());
    assertTrue(box.isChecked());
    assertTrue(((CheckableLinearLayout) v).isChecked());
    // a recycled view is reused and rebound
    View again = adapter.getView(0, v, null);
    assertSame(v, again);
    assertEquals("a", again.getTag());
    assertFalse(box.isChecked());
    adapter.setCheckboxesVisibility(false);
    adapter.getView(0, v, null);
    assertEquals(View.GONE, box.getVisibility());
    assertFalse(adapter.getCheckboxesVisibility());
  }

  @Test
  public void checkingTheBoxUpdatesTheCheckedSetAndNotifiesTheListener() {
    adapter.setCheckboxesVisibility(true);
    View v = adapter.getView(2, null, null);
    AtomicInteger calls = new AtomicInteger();
    List<Object> seen = new ArrayList<>();
    adapter.setOnItemCheckedListener(
        (view, item, checked) -> {
          calls.incrementAndGet();
          seen.add(item);
          seen.add(checked);
        });
    CheckBox box = v.findViewById(R.id.view_selectable_list_item_checkbox);
    box.setChecked(true);
    assertTrue(adapter.getChecked().contains("c"));
    box.setChecked(false);
    assertFalse(adapter.getChecked().contains("c"));
    assertEquals(2, calls.get());
    assertEquals(Arrays.asList("c", true, "c", false), seen);
  }

  @Test
  public void radioButtonsRememberTheLastSelection() {
    LinearLayout row = new LinearLayout(context);
    RadioButton radio = new RadioButton(context);
    radio.setId(R.id.view_selectable_list_item_radiobutton);
    row.addView(radio);
    adapter.setLastSelectedRadioButton(1);
    adapter.initRadioButton(row, "b", 1);
    assertTrue(radio.isChecked());
    assertEquals(1, adapter.getLastSelectedRadioButtonIndex());
    assertEquals("b", adapter.getSelectedItem());

    adapter.initRadioButton(row, "c", 2);
    assertFalse(radio.isChecked());
    radio.setChecked(true);
    assertEquals(2, adapter.getLastSelectedRadioButtonIndex());
    assertEquals("c", adapter.getSelectedItem());
  }

  @Test
  public void radioButtonWithoutTagSelectsPositionZero() {
    RadioButton radio = new RadioButton(context);
    adapter.setLastSelectedRadioButton(2);
    adapter.onItemChecked(radio, true);
    assertEquals(0, adapter.getLastSelectedRadioButtonIndex());
    adapter.setLastSelectedRadioButton(2);
    adapter.onItemChecked(radio, false);
    assertEquals(2, adapter.getLastSelectedRadioButtonIndex());
  }

  @Test
  public void clicksReachTheSubclassUnlessAMenuIsShownOnClick() {
    View v = adapter.getView(0, null, null);
    v.performClick();
    assertEquals(1, adapter.clicked.size());
    assertFalse(adapter.getShowMenuOnClick());
    adapter.setShowMenuOnClick(true);
    assertTrue(adapter.getShowMenuOnClick());
    v.performClick();
    // no menu adapter: the click is swallowed by showMenu, not forwarded
    assertEquals(1, adapter.clicked.size());
  }

  @Test
  public void longClickFallsBackToTheSubclass() {
    View v = adapter.getView(0, null, null);
    // an unhandled long click falls through to the parent's context menu
    new FrameLayout(context).addView(v);
    assertFalse(v.performLongClick());
    adapter.longClickResult = true;
    assertTrue(v.performLongClick());
    adapter.setShowMenuOnLongClick(true);
    assertTrue(v.performLongClick());
    adapter.longClickResult = false;
    assertFalse(v.performLongClick());
    assertFalse(adapter.showMenu(v));
  }

  @Test
  public void keyEventsAreNotHandledByTheMasterClick() {
    View v = adapter.getView(0, null, null);
    assertFalse(v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)));
    assertFalse(v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
    assertFalse(v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)));
  }

  @Test
  public void getViewPositionFindsTheTaggedItem() {
    View v = adapter.getView(2, null, null);
    assertEquals(2, adapter.getViewPosition(v));
    View untagged = new View(context);
    assertEquals(-1, adapter.getViewPosition(untagged));
    untagged.setTag("nope");
    assertEquals(-1, adapter.getViewPosition(untagged));
  }

  @Test
  public void filterNarrowsTheVisibleListOnly() {
    adapter.setAdapterFilter((obj, constraint) -> obj.contains(constraint));
    adapter.getFilter().filter("b");
    ShadowLooper.idleMainLooper();
    assertEquals(1, adapter.getCount());
    assertEquals("b", adapter.getItem(0));
    assertEquals(3, adapter.getTotalCount());
  }

  @Test
  public void filterWithoutDiscriminatorShowsEverything() {
    adapter.getFilter().filter("zzz");
    ShadowLooper.idleMainLooper();
    assertEquals(3, adapter.getCount());
  }

  @Test
  public void touchFeedbackSkipsNullAndCheckBoxesAndTagsChildren() {
    adapter.initTouchFeedback(null, "a");
    CheckBox cb = new CheckBox(context);
    adapter.initTouchFeedback(cb, "a");
    assertNull(cb.getTag());
    FrameLayout group = new FrameLayout(context);
    TextView child = new TextView(context);
    group.addView(child);
    adapter.initTouchFeedback(group, "a");
    assertEquals("a", group.getTag());
    assertEquals("a", child.getTag());
    assertTrue(child.hasOnClickListeners());
  }
}
