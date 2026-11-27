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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AdapterAndObserverTest {

  private Context context;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    context = ViewTestSupport.themedContext();
  }

  // ---- AbstractAdapter

  static final class Words extends AbstractAdapter<String> {
    final List<String> bound = new ArrayList<>();

    Words(Context c, String... words) {
      super(c, R.layout.view_selectable_list_item);
      addAll(words);
    }

    @Override
    protected void setupView(View view, ViewGroup parent, String item) {
      bound.add(item);
      TextView text = findView(view, R.id.view_selectable_list_item_checkbox);
      text.setText(item);
    }
  }

  private static String checkboxText(View row) {
    return ((TextView) row.findViewById(R.id.view_selectable_list_item_checkbox))
        .getText()
        .toString();
  }

  @Test
  public void eachPositionInflatesOnceThenIsRebound() {
    Words adapter = new Words(context, "a", "b");
    View first = adapter.getView(0, null, null);
    assertEquals("a", checkboxText(first));
    View recycled = adapter.getView(1, first, null);
    assertSame(first, recycled);
    assertEquals("b", checkboxText(recycled));
    assertEquals(List.of("a", "b"), adapter.bound);
  }

  @Test
  public void viewLookupsAreCachedInTheRowsHolder() {
    Words adapter = new Words(context, "a");
    View row = adapter.getView(0, null, null);
    // the second bind finds the holder the first one stored in the row's tag
    adapter.getView(0, row, null);
    assertNotNull(row.getTag());
    assertEquals(2, adapter.bound.size());
  }

  @Test
  public void itemClicksAreRoutedToTheAdaptersOwnHandler() {
    Words adapter = new Words(context, "a", "b");
    ListView list = new ListView(context);
    list.setAdapter(adapter);
    List<String> clicked = new ArrayList<>();
    AdapterView.OnItemClickListener listener =
        new AbstractAdapter.OnItemClickAdapter<String>() {
          @Override
          public void onItemClick(
              AdapterView<?> parent,
              View view,
              AbstractAdapter<String> adapter,
              int position,
              long id) {
            clicked.add(adapter.getItem(position));
          }
        };

    listener.onItemClick(list, null, 1, 1L);

    assertEquals(List.of("b"), clicked);
  }

  @Test
  public void itemClicksOnAForeignAdapterAreIgnored() {
    ListView list = new ListView(context);
    list.setAdapter(
        new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, new String[] {"x"}));
    AtomicInteger calls = new AtomicInteger();
    AdapterView.OnItemClickListener listener =
        new AbstractAdapter.OnItemClickAdapter<String>() {
          @Override
          public void onItemClick(
              AdapterView<?> parent,
              View view,
              AbstractAdapter<String> adapter,
              int position,
              long id) {
            calls.incrementAndGet();
          }
        };

    listener.onItemClick(list, null, 0, 0L);

    assertEquals(0, calls.get());
  }

  // ---- IntentWizardPage.finish

  @Test
  public void finishingTheWizardShowsTheSocialLinksAndClosesTheActivityOnDismiss() {
    FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    IntentWizardPage page = new IntentWizardPage(activity, null);
    page.onFinishInflate();
    try (MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      page.finish();

      ArgumentCaptor<DialogInterface.OnDismissListener> dismiss =
          ArgumentCaptor.forClass(DialogInterface.OnDismissListener.class);
      ui.verify(
          () ->
              UIUtils.showSocialLinksDialog(
                  eq(activity), eq(true), dismiss.capture(), eq("wizard")));
      assertFalse(activity.isFinishing());

      dismiss.getValue().onDismiss(null);

      assertTrue(activity.isFinishing());
    }
  }

  @Test
  public void finishingTheWizardOutsideAnActivityLeavesTheContextAlone() {
    IntentWizardPage page = new IntentWizardPage(context, null);
    page.onFinishInflate();
    try (MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      page.finish();

      ArgumentCaptor<DialogInterface.OnDismissListener> dismiss =
          ArgumentCaptor.forClass(DialogInterface.OnDismissListener.class);
      ui.verify(() -> UIUtils.showSocialLinksDialog(any(), anyBoolean(), dismiss.capture(), any()));
      dismiss.getValue().onDismiss(null);
    }
  }

  // ---- TimerSubscription with screens as observers

  public static final class TimedFragment extends AbstractFragment implements TimerObserver {
    int ticks;

    public TimedFragment() {
      super(R.layout.view_selectable_list_item);
    }

    @Override
    public void onTime() {
      ticks++;
    }
  }

  public static final class PlainTimedFragment extends Fragment implements TimerObserver {
    int ticks;

    @Override
    public void onTime() {
      ticks++;
    }
  }

  public static final class TimedActivity extends AbstractActivity implements TimerObserver {
    int ticks;

    public TimedActivity() {
      super(R.layout.activity_settings);
    }

    @Override
    public void onTime() {
      ticks++;
    }
  }

  private static void tick() {
    ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
  }

  @Test
  public void aFragmentIsOnlyNotifiedWhileItIsVisible() {
    FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    FrameLayout host = new FrameLayout(activity);
    host.setId(0x7f0f0050);
    activity.setContentView(host);
    PlainTimedFragment fragment = new PlainTimedFragment();
    TimerSubscription subscription = TimerService.subscribe(fragment, 1);

    // not added yet, so not visible: not notified, but the subscription stays alive
    tick();
    assertEquals(0, fragment.ticks);
    assertTrue(subscription.isSubscribed());

    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0050, fragment, "f")
        .commitNow();
    // added without a view in a window, so still not visible
    tick();
    assertEquals(0, fragment.ticks);
    assertTrue(subscription.isSubscribed());
  }

  @Test
  public void anAbstractFragmentIsSkippedWhilePaused() {
    ActivityController<FragmentActivity> controller =
        Robolectric.buildActivity(FragmentActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    FragmentActivity activity = controller.get();
    FrameLayout host = new FrameLayout(activity);
    host.setId(0x7f0f0051);
    activity.setContentView(host);
    TimedFragment fragment = new TimedFragment();
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0051, fragment, "f")
        .commitNow();
    TimerService.subscribe(fragment, 1);

    tick();
    assertEquals(1, fragment.ticks);

    controller.pause();
    tick();
    assertEquals(1, fragment.ticks);

    controller.resume();
    tick();
    assertEquals(2, fragment.ticks);
  }

  @Test
  public void anAbstractActivityIsSkippedWhilePaused() {
    ActivityController<TimedActivity> controller = Robolectric.buildActivity(TimedActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    TimedActivity activity = controller.get();
    TimerService.subscribe(activity, 1);

    tick();
    assertEquals(1, activity.ticks);

    controller.pause();
    tick();
    assertEquals(1, activity.ticks);

    controller.resume();
    tick();
    assertEquals(2, activity.ticks);
  }
}
