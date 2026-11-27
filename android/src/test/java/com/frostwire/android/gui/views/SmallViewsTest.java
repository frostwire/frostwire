/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import com.frostwire.android.offers.SupportOffer;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SmallViewsTest {

  private Context context;

  @Before
  public void setUp() {
    context = ViewTestSupport.themedContext();
  }

  @Test
  public void checkableLinearLayoutTogglesAndAddsTheCheckedDrawableState() {
    CheckableLinearLayout layout = new CheckableLinearLayout(context, null);
    assertFalse(layout.isChecked());
    assertFalse(hasCheckedState(layout.onCreateDrawableState(0)));
    layout.toggle();
    assertTrue(layout.isChecked());
    assertTrue(hasCheckedState(layout.onCreateDrawableState(0)));
    layout.setChecked(true);
    assertTrue(layout.isChecked());
    layout.setChecked(false);
    assertFalse(layout.isChecked());
  }

  private static boolean hasCheckedState(int[] state) {
    for (int s : state) {
      if (s == android.R.attr.state_checked) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void nonSmoothViewPagerNeverScrollsSmoothly() {
    NonSmoothViewPager pager = new NonSmoothViewPager(context);
    pager.setAdapter(new PagerAdapterOfThree());
    pager.setCurrentItem(2);
    assertEquals(2, pager.getCurrentItem());
    pager.setCurrentItem(1, true);
    assertEquals(1, pager.getCurrentItem());
    assertFalse(pager.isFakeDragging());
    new NonSmoothViewPager(context, null);
  }

  static final class PagerAdapterOfThree extends androidx.viewpager.widget.PagerAdapter {
    @Override
    public int getCount() {
      return 3;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
      return view == object;
    }
  }

  @Test
  public void viewFlipperSurvivesDetachFailure() {
    ViewFlipper flipper = new ViewFlipper(context, null);
    flipper.startFlipping();
    // detaching a flipper that was never attached used to throw IllegalArgumentException
    flipper.onDetachedFromWindow();
    assertFalse(flipper.isFlipping());
    new ViewFlipper(context);
  }

  @Test
  public void safeLinkMovementMethodIsASingletonThatSwallowsBadOffsets() {
    assertSame(SafeLinkMovementMethod.getInstance(), SafeLinkMovementMethod.getInstance());
    SafeLinkMovementMethod method = SafeLinkMovementMethod.getInstance();
    boolean[] armed = {false};
    SpannableString throwing =
        new SpannableString("hi") {
          @Override
          public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind) {
            if (!armed[0]) {
              return super.getSpans(queryStart, queryEnd, kind);
            }
            throw new IndexOutOfBoundsException("-1");
          }
        };
    TextView text = new TextView(context);
    text.setText(throwing);
    text.measure(
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
    text.layout(0, 0, 400, 100);
    armed[0] = true;
    MotionEvent up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 1, 1, 0);
    try {
      assertFalse(method.onTouchEvent(text, throwing, up));
    } finally {
      up.recycle();
    }
    assertTrue(method instanceof LinkMovementMethod);
  }

  @Test
  public void autoCompleteRemembersTheKeyboardOnPasteFlag() {
    FWAutoCompleteTextView view = new FWAutoCompleteTextView(context);
    assertFalse(view.isShowKeyboardOnPaste());
    view.setShowKeyboardOnPaste(true);
    assertTrue(view.isShowKeyboardOnPaste());
    assertTrue(view.onTextContextMenuItem(android.R.id.copy));
    view.onTextContextMenuItem(android.R.id.paste);
    view.setShowKeyboardOnPaste(false);
    view.onTextContextMenuItem(android.R.id.paste);
    view.setShowKeyboardOnPaste(true);
    new FWAutoCompleteTextView(context, null);
    new FWAutoCompleteTextView(context, null, 0);
  }

  @Test
  public void clickAdapterForwardsEventsToTheLiveOwnerOnly() {
    List<String> events = new ArrayList<>();
    Object owner = new Object();
    ClickAdapter<Object> adapter =
        new ClickAdapter<Object>(owner) {
          @Override
          public void onClick(Object o, View v) {
            events.add("click");
          }

          @Override
          public boolean onLongClick(Object o, View v) {
            events.add("long");
            return true;
          }

          @Override
          public boolean onKey(Object o, View v, int keyCode, KeyEvent event) {
            events.add("key" + keyCode);
            return true;
          }

          @Override
          public void onClick(Object o, DialogInterface d, int which) {
            events.add("dialog" + which);
          }

          @Override
          public void onCheckedChanged(Object o, CompoundButton b, boolean checked) {
            events.add("checked" + checked);
          }
        };
    View v = new View(context);
    adapter.onClick(v);
    assertTrue(adapter.onLongClick(v));
    assertTrue(adapter.onKey(v, 7, null));
    adapter.onClick((DialogInterface) null, 3);
    adapter.onCheckedChanged(new CheckBox(context), true);
    assertEquals(
        java.util.Arrays.asList("click", "long", "key7", "dialog3", "checkedtrue"), events);
  }

  @Test
  public void clickAdapterDefaultsDoNothingAndAnUnreachableOwnerIsIgnored() {
    ClickAdapter<Object> adapter = new ClickAdapter<Object>(new Object()) {};
    View v = new View(context);
    adapter.onClick(v);
    assertFalse(adapter.onLongClick(v));
    assertFalse(adapter.onKey(v, 1, null));
    adapter.onClick((DialogInterface) null, 0);
    adapter.onCheckedChanged(new CheckBox(context), false);

    ClickAdapter<Object> gone = new ClickAdapter<Object>(null) {};
    gone.onClick(v);
    assertFalse(gone.onLongClick(v));
    assertFalse(gone.onKey(v, 1, null));
    gone.onClick((DialogInterface) null, 0);
    gone.onCheckedChanged(new CheckBox(context), false);
  }

  @Test
  public void richNotificationActionLinkBuildsAClickableUnderlinedLink() {
    List<String> clicks = new ArrayList<>();
    ClickAdapter<Object> click =
        new ClickAdapter<Object>(this) {
          @Override
          public void onClick(Object o, View v) {
            clicks.add("clicked");
          }
        };
    RichNotificationActionLink link = new RichNotificationActionLink(context, "Learn more", click);
    assertEquals("Learn more", link.getText());
    assertSame(click, link.getClickAdapter());
    TextView view = (TextView) link.getView();
    assertEquals("Learn more", view.getText().toString());
    assertTrue(view.isClickable());
    view.performClick();
    assertEquals(1, clicks.size());
  }

  @Test
  public void paymentOptionsVisibilityKeepsItsThreeFlags() {
    PaymentOptionsVisibility v = new PaymentOptionsVisibility(true, false, true);
    assertTrue(v.oneTimeOption);
    assertFalse(v.subscriptionOption);
    assertTrue(v.rewardOption);
  }

  @Test
  public void suggestionsCursorServesTheSuggestionStringsFromJson() throws Exception {
    JSONArray suggestions =
        new JSONArray("[[\"frostwire\",0],[\"frostwire android\",0],\"not an array\"]");
    SuggestionsCursor cursor = new SuggestionsCursor(suggestions);
    assertEquals(3, cursor.getCount());
    assertArrayEquals(new String[] {"_id", "suggestion"}, cursor.getColumnNames());
    cursor.moveToPosition(1);
    assertEquals("frostwire android", cursor.getString(1));
    assertEquals(1, cursor.getInt(0));
    assertEquals(1L, cursor.getLong(0));
    assertNull(cursor.getString(0));
    cursor.moveToPosition(2);
    // a malformed entry reads as null instead of throwing
    assertNull(cursor.getString(1));
  }

  @Test
  public void suggestionsCursorRejectsTypesItDoesNotHave() throws Exception {
    SuggestionsCursor cursor = new SuggestionsCursor(new JSONArray("[[\"a\"]]"));
    cursor.moveToFirst();
    int unsupported = 0;
    try {
      cursor.getInt(1);
    } catch (UnsupportedOperationException e) {
      unsupported++;
    }
    try {
      cursor.getLong(1);
    } catch (UnsupportedOperationException e) {
      unsupported++;
    }
    try {
      cursor.getDouble(0);
    } catch (UnsupportedOperationException e) {
      unsupported++;
    }
    try {
      cursor.getFloat(0);
    } catch (UnsupportedOperationException e) {
      unsupported++;
    }
    try {
      cursor.getShort(0);
    } catch (UnsupportedOperationException e) {
      unsupported++;
    }
    try {
      cursor.isNull(0);
    } catch (UnsupportedOperationException e) {
      unsupported++;
    }
    assertEquals(6, unsupported);
  }

  @Test
  public void adMenuItemViewShowsTheOfferBadge() {
    AdMenuItemView view = new AdMenuItemView(context, null);
    view.onFinishInflate();
    SupportOffer offer = SupportOffer.forType(SupportOffer.Type.VPN);
    view.bind(offer);
    TextView headline = view.findViewById(com.frostwire.android.R.id.view_ad_menu_item_headline);
    TextView badge = view.findViewById(com.frostwire.android.R.id.view_ad_menu_item_thumbnail);
    View image = view.findViewById(com.frostwire.android.R.id.view_ad_menu_item_thumbnail_image);
    assertEquals(context.getString(offer.titleRes), headline.getText().toString());
    assertEquals(context.getString(offer.badgeTextRes), badge.getText().toString());
    assertEquals(View.VISIBLE, badge.getVisibility());
    assertEquals(View.GONE, image.getVisibility());
  }
}
