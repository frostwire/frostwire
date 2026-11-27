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

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MenuTest {

  static final class RecordingAction extends MenuAction {
    final List<Context> clicks = new ArrayList<>();

    RecordingAction(Context c, String text) {
      super(c, R.drawable.add_transfer, text, Color.RED);
    }

    @Override
    public void onClick(Context context) {
      clicks.add(context);
    }
  }

  private Context context;

  @Before
  public void setUp() {
    context = ViewTestSupport.themedContext();
  }

  @Test
  public void menuActionExposesTextTintedImageAndContext() {
    RecordingAction action = new RecordingAction(context, "Share");
    assertEquals("Share", action.getText());
    assertNotNull(action.getImage());
    assertSame(context, action.getContext());
    assertNull(action.getAppCompatActivity());
    assertNull(action.getFragmentManager());
    action.onClick();
    assertEquals(1, action.clicks.size());
    assertSame(context, action.clicks.get(0));
  }

  @Test
  public void menuActionResolvesTextFromResourcesWithAndWithoutFormatArgs() {
    MenuAction plain =
        new MenuAction(context, R.drawable.add_transfer, R.string.about, Color.BLUE) {
          @Override
          public void onClick(Context c) {}
        };
    assertEquals(context.getString(R.string.about), plain.getText());
    MenuAction formatted =
        new MenuAction(
            context, R.drawable.add_transfer, R.string.open_menu_action, Color.BLUE, "x") {
          @Override
          public void onClick(Context c) {}
        };
    assertEquals(context.getString(R.string.open_menu_action, "x"), formatted.getText());
  }

  @Test
  public void menuActionOnAnActivityGivesItsFragmentManager() {
    AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    RecordingAction action = new RecordingAction(activity, "Open");
    assertSame(activity, action.getAppCompatActivity());
    assertSame(activity.getSupportFragmentManager(), action.getFragmentManager());
  }

  @Test
  public void menuAdapterBindsEachActionToARow() {
    RecordingAction a = new RecordingAction(context, "One");
    RecordingAction b = new RecordingAction(context, "Two");
    MenuAdapter adapter = new MenuAdapter(context, "Title", Arrays.<MenuAction>asList(a, b));
    assertEquals("Title", adapter.getTitle());
    assertEquals(2, adapter.getCount());
    assertSame(b, adapter.getItem(1));
    assertEquals(1L, adapter.getItemId(1));
    assertSame(context, adapter.getContext());

    View row = adapter.getView(0, null, null);
    assertEquals("One", ((TextView) row).getText().toString());
    assertSame(a, row.getTag());
    View reused = adapter.getView(1, row, null);
    assertSame(row, reused);
    assertEquals("Two", ((TextView) reused).getText().toString());
    assertSame(b, reused.getTag());
  }

  @Test
  public void menuAdapterTitleCanComeFromAResource() {
    MenuAdapter adapter = new MenuAdapter(context, R.string.about, new ArrayList<>());
    assertEquals(context.getString(R.string.about), adapter.getTitle());
    assertEquals(0, adapter.getCount());
  }

  @Test
  public void menuBuilderRunsTheChosenActionAndDismisses() {
    AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    RecordingAction a = new RecordingAction(activity, "One");
    RecordingAction b = new RecordingAction(activity, "Two");
    MenuBuilder builder =
        new MenuBuilder(new MenuAdapter(activity, "T", Arrays.<MenuAction>asList(a, b)));
    AlertDialog dialog = builder.show();
    assertTrue(dialog.isShowing());

    builder.onClick(dialog, 1);
    assertEquals(0, a.clicks.size());
    assertEquals(1, b.clicks.size());
    assertFalse(dialog.isShowing());
  }

  @Test
  public void menuBuilderCancelDismissesWithoutRunningAnything() {
    AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    RecordingAction a = new RecordingAction(activity, "One");
    MenuBuilder builder =
        new MenuBuilder(new MenuAdapter(activity, "T", Arrays.<MenuAction>asList(a)));
    AlertDialog dialog = builder.show();
    builder.onCancel(dialog);
    assertFalse(dialog.isShowing());
    assertEquals(0, a.clicks.size());
  }

  @Test
  public void menuBuilderDoesNotShowWhenTheHostIsNotAnActivityOrIsFinishing() {
    RecordingAction a = new RecordingAction(context, "One");
    AlertDialog notShown =
        new MenuBuilder(new MenuAdapter(context, "T", Arrays.<MenuAction>asList(a))).show();
    assertFalse(notShown.isShowing());

    AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    activity.finish();
    AlertDialog finishing =
        new MenuBuilder(new MenuAdapter(activity, "T", Arrays.<MenuAction>asList(a))).show();
    assertFalse(finishing.isShowing());
  }
}
