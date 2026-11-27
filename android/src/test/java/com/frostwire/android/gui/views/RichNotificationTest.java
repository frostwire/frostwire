/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class RichNotificationTest {

  private Context context;

  @Before
  public void setUp() {
    context = ViewTestSupport.themedContext();
    RichNotification.wasDismissed.clear();
  }

  @After
  public void tearDown() {
    RichNotification.wasDismissed.clear();
  }

  @SuppressWarnings("deprecation")
  private RichNotification notification(String title, String description, int links) {
    AttributeSet attrs =
        Robolectric.buildAttributeSet()
            .addAttribute(R.attr.rich_notification_title, title)
            .addAttribute(R.attr.rich_notification_description, description)
            .addAttribute(R.attr.rich_notification_number_of_action_links, String.valueOf(links))
            .build();
    RichNotification n = new RichNotification(context, attrs);
    n.setId(View.generateViewId());
    n.onFinishInflate();
    return n;
  }

  private TextView title(RichNotification n) {
    return n.findViewById(R.id.view_rich_notification_title);
  }

  private TextView description(RichNotification n) {
    return n.findViewById(R.id.view_rich_notification_description);
  }

  @Test
  public void showsTitleAndDescriptionFromTheXmlAttributes() {
    RichNotification n = notification("Heads up", "Something happened", 0);
    assertEquals("Heads up", title(n).getText().toString());
    assertEquals("Something happened", description(n).getText().toString());
    assertEquals(
        View.GONE, n.findViewById(R.id.view_rich_notification_action_links).getVisibility());
  }

  @Test
  public void anEmptyTextHidesItsTextView() {
    RichNotification n = notification("Only a title", "", 2);
    assertEquals(View.VISIBLE, title(n).getVisibility());
    assertEquals(View.GONE, description(n).getVisibility());
    assertEquals(
        View.VISIBLE, n.findViewById(R.id.view_rich_notification_action_links).getVisibility());
  }

  @Test
  public void setDescriptionReplacesTheText() {
    RichNotification n = notification("t", "old", 0);
    n.setDescription("new");
    assertEquals("new", description(n).getText().toString());
  }

  @Test
  public void clickingTheTextNotifiesTheListener() {
    RichNotification n = notification("t", "d", 0);
    List<View> clicked = new ArrayList<>();
    View.OnClickListener listener = clicked::add;
    n.setOnClickListener(listener);
    assertSame(listener, n.getOnClickListener());
    title(n).performClick();
    description(n).performClick();
    assertEquals(2, clicked.size());
    assertSame(n, clicked.get(0));
  }

  @Test
  public void clickingWithoutAListenerDoesNothing() {
    RichNotification n = notification("t", "d", 0);
    title(n).performClick();
    assertEquals(View.VISIBLE, n.getVisibility());
  }

  @Test
  public void dismissingHidesItAndRemembersItsId() {
    RichNotification n = notification("t", "d", 0);
    assertFalse(n.wasDismissed());
    n.findViewById(R.id.view_rich_notification_close_button).performClick();
    assertEquals(View.GONE, n.getVisibility());
    assertTrue(n.wasDismissed());
    assertTrue(RichNotification.wasDismissed.contains(n.getId()));
  }

  @Test
  public void updateActionLinksReplacesThePreviousLinks() {
    RichNotification n = notification("t", "d", 1);
    LinearLayout container = n.findViewById(R.id.view_rich_notification_action_links);
    ClickAdapter<Object> noop = new ClickAdapter<Object>(this) {};
    n.updateActionLinks(
        new RichNotificationActionLink(context, "one", noop),
        new RichNotificationActionLink(context, "two", noop));
    assertEquals(2, container.getChildCount());
    assertEquals(View.VISIBLE, container.getVisibility());
    assertEquals("one", ((TextView) container.getChildAt(0)).getText().toString());

    n.updateActionLinks(new RichNotificationActionLink(context, "three", noop));
    assertEquals(1, container.getChildCount());
    assertEquals("three", ((TextView) container.getChildAt(0)).getText().toString());
    assertEquals(
        5, ((LinearLayout.LayoutParams) container.getChildAt(0).getLayoutParams()).leftMargin);

    n.updateActionLinks();
    assertEquals(View.GONE, container.getVisibility());
  }
}
