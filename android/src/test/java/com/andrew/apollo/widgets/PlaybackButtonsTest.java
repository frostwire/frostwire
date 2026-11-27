/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import com.frostwire.android.R;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The music player's transport buttons with no playback service bound: they must still show the
 * right icon and description for the idle player and run their callbacks, never crash.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PlaybackButtonsTest {

  private Activity activity;

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
  }

  @Test
  public void theRepeatButtonStartsOffAndFiresItsCallbackOnClick() {
    RepeatButton button = new RepeatButton(activity, null);
    final AtomicInteger clicks = new AtomicInteger();
    button.setOnClickedCallback(clicks::incrementAndGet);

    button.updateRepeatState();
    assertEquals(activity.getString(R.string.accessibility_repeat), button.getContentDescription());

    assertTrue(button.performClick());

    assertEquals(1, clicks.get());
    assertEquals(
        "with no service the mode stays off",
        activity.getString(R.string.accessibility_repeat),
        button.getContentDescription());
  }

  @Test
  public void aThrowingRepeatCallbackDoesNotBreakTheClick() {
    RepeatButton button = new RepeatButton(activity, null);
    button.setOnClickedCallback(
        () -> {
          throw new IllegalStateException("callback failure");
        });

    assertTrue(button.performClick());
  }

  @Test
  public void theRepeatButtonClicksWithoutACallback() {
    assertTrue(new RepeatButton(activity, null).performClick());
  }

  @Test
  public void theShuffleButtonStartsOffAndFiresItsCallbackOnClick() {
    ShuffleButton button = new ShuffleButton(activity, null);
    final AtomicInteger clicks = new AtomicInteger();
    button.setOnClickedCallback(clicks::incrementAndGet);

    button.updateShuffleState();
    assertEquals(
        activity.getString(R.string.accessibility_shuffle), button.getContentDescription());

    assertTrue(button.performClick());

    assertEquals(1, clicks.get());
    assertEquals(
        activity.getString(R.string.accessibility_shuffle), button.getContentDescription());
  }

  @Test
  public void aThrowingShuffleCallbackDoesNotBreakTheClick() {
    ShuffleButton button = new ShuffleButton(activity, null);
    button.setOnClickedCallback(
        () -> {
          throw new IllegalStateException("callback failure");
        });

    assertTrue(button.performClick());
  }

  @Test
  public void theShuffleButtonClicksWithoutACallback() {
    assertTrue(new ShuffleButton(activity, null).performClick());
  }

  @Test
  public void theIdlePlayerOffersPlay() {
    PlayPauseButton button = new PlayPauseButton(activity, null);

    button.updateState();

    assertEquals(activity.getString(R.string.accessibility_play), button.getContentDescription());
    assertEquals(
        R.drawable.btn_playback_play, shadowOf(button.getDrawable()).getCreatedFromResId());
  }

  @Test
  public void thePlayAndPauseIconsCanBeReplaced() {
    PlayPauseButton button = new PlayPauseButton(activity, null);
    button.setPlayDrawable(R.drawable.btn_playback_pause);
    button.setPauseDrawable(R.drawable.btn_playback_play);

    button.updateState();

    assertEquals(
        "the idle player now shows the replaced play icon",
        R.drawable.btn_playback_pause,
        shadowOf(button.getDrawable()).getCreatedFromResId());
  }

  @Test
  public void clickingPlayWithNothingBoundKeepsTheButtonOnPlay() {
    PlayPauseButton button = new PlayPauseButton(activity, null);

    assertTrue(button.performClick());

    assertEquals(activity.getString(R.string.accessibility_play), button.getContentDescription());
  }

  @Test
  public void longPressingShowsTheCheatSheetOnlyWhenTheButtonIsDescribed() {
    PlayPauseButton button = new PlayPauseButton(activity, null);
    activity.setContentView(button);
    shadowOf(Looper.getMainLooper()).idle();
    assertFalse("nothing to show without a description", button.performLongClick());

    button.updateState();

    assertTrue(button.performLongClick());
    assertEquals(
        activity.getString(R.string.accessibility_play),
        org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
  }

  @Test
  public void aCustomLongClickListenerIsRemembered() {
    PlayPauseButton button = new PlayPauseButton(activity, null);
    assertTrue("the button handles long presses itself", button.hasOnLongClickListener());

    button.setOnLongClickListener(null);
    assertFalse(button.hasOnLongClickListener());

    button.setOnLongClickListener(v -> true);
    assertTrue(button.hasOnLongClickListener());
  }

  // ---------------------------------------------------------------------------------------------

  private RepeatingImageButton repeating(int id) {
    RepeatingImageButton button = new RepeatingImageButton(activity, null);
    button.setId(id);
    return button;
  }

  @Test
  public void thePreviousAndNextButtonsShowTheirIcons() {
    RepeatingImageButton next = repeating(R.id.action_button_next);
    next.updateState();
    assertEquals(R.drawable.btn_playback_next, shadowOf(next.getDrawable()).getCreatedFromResId());

    RepeatingImageButton previous = repeating(R.id.action_button_previous);
    previous.updateState();
    assertEquals(
        R.drawable.btn_playback_previous, shadowOf(previous.getDrawable()).getCreatedFromResId());
  }

  @Test
  public void theIconsCanBeReplacedAndTheRefreshShowsThem() {
    RepeatingImageButton next = repeating(R.id.action_button_next);
    next.setNextDrawable(R.drawable.btn_playback_play);
    assertEquals(R.drawable.btn_playback_play, shadowOf(next.getDrawable()).getCreatedFromResId());

    RepeatingImageButton previous = repeating(R.id.action_button_previous);
    previous.setPreviousDrawable(R.drawable.btn_playback_pause);
    assertEquals(
        R.drawable.btn_playback_pause, shadowOf(previous.getDrawable()).getCreatedFromResId());
  }

  @Test
  public void aButtonWithAnotherIdHasNoIcon() {
    RepeatingImageButton other = repeating(View.NO_ID);
    other.updateState();
    assertNull(other.getDrawable());
  }

  @Test
  public void clickingPreviousOrNextDoesNotCrashWithoutAService() {
    assertTrue(repeating(R.id.action_button_next).performClick());
    assertTrue(repeating(R.id.action_button_previous).performClick());
    assertTrue(repeating(View.NO_ID).performClick());
  }

  @Test
  public void aLongPressRepeatsWhileHeldAndReportsAFinalRepeatOnRelease() {
    RepeatingImageButton button = repeating(R.id.action_button_next);
    final List<int[]> repeats = new ArrayList<>();
    button.setRepeatListener(
        (v, duration, count) -> repeats.add(new int[] {(int) duration, count}));
    activity.setContentView(button);
    shadowOf(Looper.getMainLooper()).idle();

    button.setPressed(true);
    assertTrue(button.performLongClick());
    ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS);
    SystemClock.setCurrentTimeMillis(SystemClock.uptimeMillis() + 900);
    ShadowLooper.idleMainLooper(900, TimeUnit.MILLISECONDS);
    int heldRepeats = repeats.size();
    assertTrue("repeats while pressed: " + heldRepeats, heldRepeats >= 2);
    assertEquals(0, repeats.get(0)[1]);
    assertEquals(1, repeats.get(1)[1]);

    button.setPressed(false);
    long now = SystemClock.uptimeMillis();
    button.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1f, 1f, 0));

    assertEquals("one more call on release", heldRepeats + 1, repeats.size());
    assertEquals("the release repeat reports -1", -1, repeats.get(repeats.size() - 1)[1]);
    int after = repeats.size();
    ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS);
    assertEquals("nothing repeats after the release", after, repeats.size());
  }

  @Test
  public void aReleaseWithoutALongPressDoesNotRepeat() {
    RepeatingImageButton button = repeating(R.id.action_button_next);
    final AtomicInteger repeats = new AtomicInteger();
    button.setRepeatListener((v, duration, count) -> repeats.incrementAndGet());
    long now = SystemClock.uptimeMillis();

    button.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1f, 1f, 0));

    assertEquals(0, repeats.get());
  }

  @Test
  public void holdingEnterRepeatsAndReleasingEndsIt() {
    RepeatingImageButton button = repeating(R.id.action_button_next);
    final List<Integer> counts = new ArrayList<>();
    button.setRepeatListener((v, duration, count) -> counts.add(count));
    activity.setContentView(button);

    KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
    KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER);
    assertTrue(button.onKeyDown(KeyEvent.KEYCODE_ENTER, down));
    assertTrue(button.performLongClick());
    button.onKeyUp(KeyEvent.KEYCODE_ENTER, up);

    assertEquals(-1, (int) counts.get(counts.size() - 1));
    assertFalse("other keys fall through", button.onKeyDown(KeyEvent.KEYCODE_A, down));
  }
}
