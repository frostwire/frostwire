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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import com.frostwire.android.R;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ClearableEditTextViewTest {

  private Context context;
  private ClearableEditTextView view;

  @Before
  public void setUp() {
    context = ViewTestSupport.themedContext();
    view = new ClearableEditTextView(context, null);
    view.onFinishInflate();
  }

  @Test
  public void showsTheMagnifierUntilThereIsText() {
    ImageView search = view.findViewById(R.id.view_clearable_edit_text_image_search);
    ImageButton clear = view.findViewById(R.id.view_clearable_edit_text_button_clear);
    assertEquals(View.VISIBLE, search.getVisibility());
    assertEquals(View.GONE, clear.getVisibility());

    view.setText("frost");
    assertEquals("frost", view.getText());
    assertEquals(View.GONE, search.getVisibility());
    assertEquals(View.VISIBLE, clear.getVisibility());

    view.setText("");
    assertEquals(View.VISIBLE, search.getVisibility());
    assertEquals(View.GONE, clear.getVisibility());
  }

  @Test
  public void clearButtonEmptiesTheTextAndNotifiesTheListener() {
    AtomicInteger cleared = new AtomicInteger();
    ClearableEditTextView.OnActionListener listener = v -> cleared.incrementAndGet();
    view.setOnActionListener(listener);
    assertSame(listener, view.getOnActionListener());
    view.setText("something");
    view.findViewById(R.id.view_clearable_edit_text_button_clear).performClick();
    assertEquals("", view.getText());
    assertEquals(1, cleared.get());
  }

  @Test
  public void clearWithoutAListenerStillEmptiesTheText() {
    view.setText("something");
    view.findViewById(R.id.view_clearable_edit_text_button_clear).performClick();
    assertEquals("", view.getText());
  }

  @Test
  public void hintAndKeyboardFlagAreForwardedToTheInput() {
    view.setHint("Search here");
    assertEquals("Search here", view.getHint());
    assertFalse(view.isShowKeyboardOnPaste());
    view.setShowKeyboardOnPaste(true);
    assertTrue(view.isShowKeyboardOnPaste());
    assertTrue(((FWAutoCompleteTextView) view.getAutoCompleteTextView()).isShowKeyboardOnPaste());
    assertNotNull(view.findViewById(R.id.view_clearable_edit_text_input));
    assertSame(
        view.findViewById(R.id.view_clearable_edit_text_input), view.getAutoCompleteTextView());
    view.replaceSearchIconDrawable(R.drawable.add_transfer);
  }

  @Test
  public void listenersAreRegisteredOnTheInputNotTheContainer() {
    View.OnFocusChangeListener focus = (v, hasFocus) -> {};
    view.setOnFocusChangeListener(focus);
    assertSame(focus, view.getOnFocusChangeListener());
    AdapterView.OnItemClickListener click = (parent, v, position, id) -> {};
    view.setOnItemClickListener(click);
    assertSame(click, view.getOnItemClickListener());
    AtomicInteger keys = new AtomicInteger();
    view.setOnKeyListener(
        (v, keyCode, event) -> {
          keys.incrementAndGet();
          return true;
        });
    view.getAutoCompleteTextView()
        .dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A));
    assertEquals(1, keys.get());
  }

  @Test
  public void focusFlagsGoToBothTheContainerAndTheInput() {
    view.setFocusable(false);
    assertFalse(view.isFocusable());
    assertFalse(view.getAutoCompleteTextView().isFocusable());
    view.setFocusableInTouchMode(true);
    assertTrue(view.getAutoCompleteTextView().isFocusableInTouchMode());
  }

  @Test
  public void selectAllSelectsTheWholeText() {
    view.setAdapter(
        new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, new String[] {"a"}));
    view.setText("abc");
    view.selectAll();
    assertEquals(0, view.getAutoCompleteTextView().getSelectionStart());
    assertEquals(3, view.getAutoCompleteTextView().getSelectionEnd());
    view.setListSelection(-1);
    view.dismissDropDown();
  }

  @Test
  public void lineBreaksInPastedTextAreHiddenFromTheSingleLineInput() {
    view.setText("a\nb");
    FWAutoCompleteTextView input = (FWAutoCompleteTextView) view.getAutoCompleteTextView();
    CharSequence shown = input.getTransformationMethod().getTransformation(input.getText(), input);
    assertEquals("a﻿b", shown.toString());
  }
}
