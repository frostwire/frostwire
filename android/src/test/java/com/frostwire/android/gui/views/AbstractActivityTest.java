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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.frostwire.android.R;
import com.frostwire.android.util.Debug;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AbstractActivityTest {

  public static final class TestActivity extends AbstractActivity {
    Bundle initialized;
    Toolbar initializedToolbar;
    View insets;

    public TestActivity() {
      super(R.layout.activity_settings);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
      initialized = savedInstanceState == null ? new Bundle() : savedInstanceState;
    }

    @Override
    protected void initToolbar(Toolbar toolbar) {
      initializedToolbar = toolbar;
    }

    @Override
    protected View getInsetsTarget() {
      insets = super.getInsetsTarget();
      return insets;
    }

    void setView(View view) {
      setToolbarView(view);
    }

    void setView(View view, int gravity) {
      setToolbarView(view, gravity);
    }

    Fragment byId(int id) {
      return findFragment(id);
    }
  }

  public static final class Tagged extends Fragment {}

  private ActivityController<TestActivity> controller;
  private TestActivity activity;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    controller = Robolectric.buildActivity(TestActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    activity = controller.get();
  }

  @After
  public void tearDown() {
    AbstractActivity.setMenuIconsVisible(false);
  }

  @Test
  public void createBuildsTheContentAndTheToolbar() {
    assertNotNull(activity.initialized);
    Toolbar toolbar = activity.findToolbar();
    assertNotNull(toolbar);
    assertSame(toolbar, activity.initializedToolbar);
    assertNotNull(activity.getSupportActionBar());
    assertNotNull(activity.insets);
  }

  @Test
  public void theTitleIsMirroredOnTheToolbar() {
    activity.setTitle("Settings");
    assertEquals("Settings", activity.findToolbar().getTitle().toString());
  }

  @Test
  public void pausingAndResumingTogglesIsPaused() {
    assertFalse(activity.isPaused());
    controller.pause();
    assertTrue(activity.isPaused());
    controller.resume();
    assertFalse(activity.isPaused());
  }

  @Test
  public void attachedFragmentsWithTagsAreTrackedAndFoundByTypeAndId() {
    Tagged tagged = new Tagged();
    Tagged untagged = new Tagged();
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(tagged, "tagged")
        .add(untagged, null)
        .commitNow();
    ShadowLooper.idleMainLooper();

    List<Fragment> tracked = activity.getFragments();
    assertEquals(1, tracked.size());
    assertSame(tagged, tracked.get(0));
    assertSame(tagged, activity.findFragment(Tagged.class));
    assertNull(activity.byId(0x7f0f0099));
  }

  @Test
  public void aToolbarViewReplacesWhatWasThereBefore() {
    FrameLayout placeholder = activity.findViewById(R.id.toolbar_main_placeholder);
    TextView first = new TextView(activity);
    TextView second = new TextView(activity);
    activity.setView(first);
    assertEquals(View.VISIBLE, placeholder.getVisibility());
    assertEquals(1, placeholder.getChildCount());
    assertEquals(
        Gravity.START | Gravity.CENTER_VERTICAL,
        ((FrameLayout.LayoutParams) first.getLayoutParams()).gravity);

    activity.setView(second, Gravity.END);
    assertEquals(1, placeholder.getChildCount());
    assertSame(second, placeholder.getChildAt(0));
    assertEquals(Gravity.END, ((FrameLayout.LayoutParams) second.getLayoutParams()).gravity);

    activity.setView(null);
    assertEquals(0, placeholder.getChildCount());
  }

  @Test
  public void homeGoesBackAndOtherItemsAreLeftToTheSuperclass() {
    MenuItem home = mock(MenuItem.class);
    when(home.getItemId()).thenReturn(android.R.id.home);
    assertTrue(activity.onOptionsItemSelected(home));
    assertTrue(activity.isFinishing());

    MenuItem other = mock(MenuItem.class);
    when(other.getItemId()).thenReturn(R.id.toolbar_main);
    assertFalse(activity.onOptionsItemSelected(other));
  }

  @Test
  public void menuIconsAreForcedOnlyWhenTheAppAsksForThem() throws Exception {
    MenuBuilder menu = new MenuBuilder(activity);
    Field optionalIcons = MenuBuilder.class.getDeclaredField("mOptionalIconsVisible");
    optionalIcons.setAccessible(true);

    activity.onCreateOptionsMenu(menu);
    assertFalse(optionalIcons.getBoolean(menu));

    AbstractActivity.setMenuIconsVisible(true);
    activity.onCreateOptionsMenu(menu);
    assertTrue(optionalIcons.getBoolean(menu));
  }

  @Test
  public void actionModeCallbacksAreForwardedWithIconsApplied() {
    List<String> calls = new ArrayList<>();
    ActionMode.Callback callback =
        new ActionMode.Callback() {
          @Override
          public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            calls.add("create");
            return true;
          }

          @Override
          public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            calls.add("prepare");
            return false;
          }

          @Override
          public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            calls.add("item");
            return true;
          }

          @Override
          public void onDestroyActionMode(ActionMode mode) {
            calls.add("destroy");
          }
        };

    ActionMode mode = activity.startSupportActionMode(callback);
    assertNotNull(mode);
    assertTrue(calls.contains("create"));
    assertTrue(calls.contains("prepare"));
    mode.finish();
    assertTrue(calls.contains("destroy"));
  }

  @Test
  public void unregisteringAReceiverThatWasNeverRegisteredThrowsWhenDebugging() {
    BroadcastReceiver receiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {}
        };
    try (MockedStatic<Debug> debug = mockStatic(Debug.class)) {
      debug.when(Debug::isEnabled).thenReturn(true);
      try {
        activity.unregisterReceiver(receiver);
        fail("debug builds surface the bad unregister");
      } catch (IllegalArgumentException expected) {
        // rethrown so it can be fixed
      }
    }
  }

  @Test
  public void unregisteringAReceiverThatWasNeverRegisteredIsIgnoredInRelease() {
    BroadcastReceiver receiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {}
        };
    try (MockedStatic<Debug> debug = mockStatic(Debug.class)) {
      debug.when(Debug::isEnabled).thenReturn(false);
      activity.unregisterReceiver(receiver);
    }
  }

  @Test
  public void aRegisteredReceiverIsUnregisteredNormally() {
    BroadcastReceiver receiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {}
        };
    activity.registerReceiver(
        receiver, new android.content.IntentFilter("test.action"), Context.RECEIVER_NOT_EXPORTED);
    activity.unregisterReceiver(receiver);
  }

  // ---- AbstractFragment

  public static final class Screen extends AbstractFragment {
    boolean componentsInitialized;

    public Screen() {
      super(R.layout.view_selectable_list_item);
    }

    @Override
    protected void initComponents(View rootView, Bundle savedInstanceState) {
      componentsInitialized = true;
    }

    <T extends View> T find(View v, int id) {
      return findView(v, id);
    }
  }

  @Test
  public void aFragmentInflatesItsLayoutAndInitializesItsComponents() {
    Screen screen = new Screen();
    FrameLayout host = new FrameLayout(activity);
    host.setId(0x7f0f0030);
    activity.setContentView(host);
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0030, screen, "screen")
        .commitNow();

    assertTrue(screen.componentsInitialized);
    assertNotNull(screen.getView());
    assertNotNull(screen.find(screen.getView(), R.id.view_selectable_list_item_checkbox));
    assertNull(screen.find(null, R.id.view_selectable_list_item_checkbox));
  }

  @Test
  public void aFragmentKnowsWhenItIsPaused() {
    Screen screen = new Screen();
    FrameLayout host = new FrameLayout(activity);
    host.setId(0x7f0f0031);
    activity.setContentView(host);
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0031, screen, "screen")
        .commitNow();
    assertFalse(screen.isPaused());
    controller.pause();
    assertTrue(screen.isPaused());
    controller.resume();
    assertFalse(screen.isPaused());
  }

  @Test
  public void startingAnActionModeNeedsAnAppCompatActivity() {
    Screen screen = new Screen();
    FragmentActivity plain = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    FrameLayout host = new FrameLayout(plain);
    host.setId(0x7f0f0032);
    plain.setContentView(host);
    plain
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0032, screen, "screen")
        .commitNow();
    try {
      screen.startActionMode(mock(ActionMode.Callback.class));
      fail("a plain FragmentActivity can not host an action mode");
    } catch (UnsupportedOperationException expected) {
      assertTrue(expected.getMessage().contains("AppCompatActivity"));
    }
  }

  @Test
  public void anAppCompatHostStartsTheActionMode() {
    Screen screen = new Screen();
    FrameLayout host = new FrameLayout(activity);
    host.setId(0x7f0f0033);
    activity.setContentView(host);
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0033, screen, "screen")
        .commitNow();
    List<String> calls = new ArrayList<>();
    screen.startActionMode(
        new ActionMode.Callback() {
          @Override
          public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            calls.add("create");
            return true;
          }

          @Override
          public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
          }

          @Override
          public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
          }

          @Override
          public void onDestroyActionMode(ActionMode mode) {}
        });
    assertEquals(List.of("create"), calls);
  }
}
