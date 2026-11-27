/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.TaskThrottle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AbstractTransferDetailFragmentTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final String OTHER_HASH = "fedcba9876543210fedcba9876543210fedcba98";

  public static final class DetailFragment extends AbstractTransferDetailFragment {
    int transferChanges;
    int componentUpdates;
    View referenced;

    public DetailFragment() {
      super(R.layout.fragment_transfer_detail_status);
    }

    @Override
    protected int getTabTitleStringId() {
      return R.string.status;
    }

    @Override
    protected void ensureComponentsReferenced(View rootView) {
      referenced = rootView;
    }

    @Override
    protected void updateComponents() {
      componentUpdates++;
    }

    @Override
    protected void onTransferChanged() {
      transferChanges++;
    }

    String time(long seconds) {
      return seconds2time(seconds);
    }
  }

  private MockedStatic<SystemUtils> system;
  private MockedStatic<TaskThrottle> throttle;
  private MockedStatic<TransferManager> managers;
  private TransferManager manager;
  private final List<Runnable> background = new ArrayList<>();
  private ActivityController<FragmentActivity> controller;
  private FragmentActivity activity;
  private DetailFragment fragment;

  @Before
  public void setUp() {
    // The background work is queued and run by the test: BTEngine.find needs jlibtorrent's native
    // library, which unit tests do not have.
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            i -> {
              background.add(i.getArgument(1));
              return null;
            });
    system.when(SystemUtils::isUIThread).thenReturn(true);
    throttle = mockStatic(TaskThrottle.class);
    throttle.when(() -> TaskThrottle.isReadyToSubmitTask(anyString(), anyLong())).thenReturn(true);
    manager = mock(TransferManager.class);
    managers = mockStatic(TransferManager.class);
    managers.when(TransferManager::instance).thenReturn(manager);

    controller = Robolectric.buildActivity(FragmentActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    activity = controller.get();
    fragment = new DetailFragment();
  }

  @After
  public void tearDown() {
    managers.close();
    throttle.close();
    system.close();
  }

  private static UIBittorrentDownload transfer(String hash, String name) {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getInfoHash()).thenReturn(hash);
    when(dl.getDisplayName()).thenReturn(name);
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.getProgress()).thenReturn(42);
    when(dl.getDownloadSpeed()).thenReturn(2048L);
    when(dl.getUploadSpeed()).thenReturn(1024L);
    return dl;
  }

  private void attach(DetailFragment f) {
    FrameLayout host = new FrameLayout(activity);
    host.setId(0x7f0f0040);
    activity.setContentView(host);
    activity.getSupportFragmentManager().beginTransaction().add(0x7f0f0040, f, "d").commitNow();
  }

  private void runBackground() {
    List<Runnable> work = new ArrayList<>(background);
    background.clear();
    for (Runnable r : work) {
      try {
        r.run();
      } catch (Throwable nativeLibraryMissing) {
        // the queued engine lookup (BTEngine.find) needs jlibtorrent's native library
      }
    }
  }

  private TextView text(int id) {
    return fragment.requireView().findViewById(id);
  }

  @Test
  public void initTakesTheTabTitleAndBindsTheTransfer() {
    SparseArray<String> titles = new SparseArray<>();
    titles.put(R.string.status, "Status");
    UIBittorrentDownload dl = transfer(HASH, "Ubuntu");

    DetailFragment same = (DetailFragment) fragment.init(activity, titles, dl);

    assertSame(fragment, same);
    assertEquals("Status", fragment.getTabTitle());
    assertEquals(1, fragment.transferChanges);
  }

  @Test
  public void bindingTheSameTransferAgainIsNotAChangeButANewOneIs() {
    UIBittorrentDownload dl = transfer(HASH, "Ubuntu");
    fragment.bindToTransfer(null);
    assertEquals(0, fragment.transferChanges);
    fragment.bindToTransfer(dl);
    fragment.bindToTransfer(transfer(HASH, "Ubuntu again"));
    assertEquals(1, fragment.transferChanges);
    fragment.bindToTransfer(transfer(OTHER_HASH, "Debian"));
    assertEquals(2, fragment.transferChanges);
  }

  @Test
  public void theHeaderShowsTheTransferWhenTheViewIsCreated() {
    fragment.bindToTransfer(transfer(HASH, "Ubuntu"));
    attach(fragment);

    assertNotNull(fragment.referenced);
    assertEquals("Ubuntu", text(R.id.view_transfer_detail_progress_title).getText().toString());
  }

  @Test
  public void theTimerPaintsTheHeaderFromTheCacheTheBackgroundRefreshFilled() {
    fragment.bindToTransfer(transfer(HASH, "Ubuntu"));
    attach(fragment);
    runBackground();
    background.clear();

    fragment.onTime();
    runBackground();
    fragment.onTime();

    ProgressBar bar =
        fragment.requireView().findViewById(R.id.view_transfer_detail_progress_progress);
    assertEquals(42, bar.getProgress());
    assertTrue(
        text(R.id.view_transfer_detail_progress_down_speed).getText().toString().endsWith("/s"));
    assertTrue(
        text(R.id.view_transfer_detail_progress_up_speed).getText().toString().endsWith("/s"));
    assertTrue(fragment.componentUpdates >= 2);
    assertEquals(
        TransferStateStringsOf(TransferState.DOWNLOADING),
        text(R.id.view_transfer_detail_progress_status).getText().toString());
  }

  private String TransferStateStringsOf(TransferState state) {
    return com.frostwire.android.gui.util.TransferStateStrings.getInstance(activity).get(state);
  }

  @Test
  public void aBackgroundRefreshFailureIsLoggedNotThrown() {
    UIBittorrentDownload dl = transfer(HASH, "Ubuntu");
    org.mockito.Mockito.doThrow(new IllegalStateException("native")).when(dl).updateCachedState();
    fragment.bindToTransfer(dl);
    attach(fragment);

    fragment.onTime();
    runBackground();

    assertEquals(
        0,
        ((ProgressBar)
                fragment.requireView().findViewById(R.id.view_transfer_detail_progress_progress))
            .getProgress());
  }

  @Test
  public void withoutATransferTheTimerRecoversItFromTheActivityIntent() {
    UIBittorrentDownload dl = transfer(HASH, "Recovered");
    when(manager.getBittorrentDownload(HASH)).thenReturn(dl);
    activity.setIntent(new Intent().putExtra("infoHash", HASH));
    attach(fragment);

    fragment.onTime();
    runBackground();
    fragment.onTime();

    assertEquals("Recovered", text(R.id.view_transfer_detail_progress_title).getText().toString());
    assertEquals(1, fragment.transferChanges);
  }

  @Test
  public void withoutATransferOrAnIntentTheTimerDoesNothing() {
    attach(fragment);
    fragment.onTime();
    runBackground();
    assertEquals(0, fragment.componentUpdates);
  }

  @Test
  public void aRecoveryTheManagerCouldNotDoYetIsRetriedInTheBackgroundButThrottled() {
    when(manager.getBittorrentDownload(HASH)).thenReturn(null);
    attach(fragment);
    activity.setIntent(new Intent().putExtra("infoHash", HASH));
    background.clear();

    fragment.onTime();
    assertEquals(1, background.size());

    background.clear();
    throttle.when(() -> TaskThrottle.isReadyToSubmitTask(anyString(), anyLong())).thenReturn(false);
    fragment.onTime();
    assertTrue(background.isEmpty());
    assertEquals(0, fragment.transferChanges);
  }

  @Test
  public void anIntentForAnotherTransferRebindsWhenTheTimerTicks() {
    fragment.bindToTransfer(transfer(HASH, "Ubuntu"));
    attach(fragment);
    UIBittorrentDownload other = transfer(OTHER_HASH, "Debian");
    when(manager.getBittorrentDownload(OTHER_HASH)).thenReturn(other);
    activity.setIntent(new Intent().putExtra("infoHash", OTHER_HASH));

    fragment.onTime();

    assertEquals(2, fragment.transferChanges);
  }

  @Test
  public void theStateKeepsTheInfoHashAndTheCacheIsClearedWithTheView() {
    UIBittorrentDownload dl = transfer(HASH, "Ubuntu");
    fragment.bindToTransfer(dl);
    attach(fragment);
    Bundle saved = new Bundle();

    fragment.onSaveInstanceState(saved);
    assertEquals(HASH, saved.getString("infohash"));

    activity.getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
    verify(dl, org.mockito.Mockito.atLeastOnce()).clearCachedItems();
  }

  @Test
  public void resumingRefreshesTheCacheInTheBackground() {
    UIBittorrentDownload dl = transfer(HASH, "Ubuntu");
    fragment.bindToTransfer(dl);
    attach(fragment);
    controller.pause().resume();
    // the cache refresh is queued with the engine lookup; only the refresh is run here
    int queued = background.size();
    assertTrue(queued > 0);
  }

  @Test
  public void secondsAreFormattedAsDaysHoursMinutesAndSeconds() {
    attach(fragment);
    assertEquals("0:05", fragment.time(5));
    assertEquals("1:05", fragment.time(65));
    assertEquals("1:01:05", fragment.time(3665));
    assertEquals("1:00:00", fragment.time(3600));
    assertEquals("1:01:01:01", fragment.time(86400 + 3600 + 60 + 1));
    assertEquals(activity.getString(R.string.infinity), fragment.time(-1));
  }

  @Test
  public void theShareRatioIsSentOverReceived() {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getBytesSent()).thenReturn(50L);
    when(dl.getBytesReceived()).thenReturn(100L);
    assertEquals("50.0%", AbstractTransferDetailFragment.getShareRatio(dl));
    when(dl.getBytesReceived()).thenReturn(-1L);
    assertEquals("0%", AbstractTransferDetailFragment.getShareRatio(dl));
  }

  // ---- updateAdapterItems

  /** Records what its observer was told changed; notify* methods are final, observers are not. */
  static final class RecordingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final List<String> events = new ArrayList<>();
    boolean throwOnFullRefresh;

    RecordingAdapter() {
      registerAdapterDataObserver(
          new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
              if (throwOnFullRefresh) {
                throw new IllegalStateException("computing layout");
              }
              events.add("all");
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
              events.add("changed" + positionStart);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
              events.add("inserted" + positionStart);
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
              events.add("removed" + positionStart + "x" + itemCount);
            }
          });
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {}

    @Override
    public int getItemCount() {
      return 0;
    }
  }

  @Test
  public void anEmptyListIsFilledWithAFullRefresh() {
    RecordingAdapter adapter = new RecordingAdapter();
    List<String> items = new ArrayList<>();
    AbstractTransferDetailFragment.updateAdapterItems(adapter, items, Arrays.asList("a", "b"));
    assertEquals(Arrays.asList("a", "b"), items);
    assertEquals(List.of("all"), adapter.events);
  }

  @Test
  public void aBiggerFreshListUpdatesAndAppends() {
    RecordingAdapter adapter = new RecordingAdapter();
    List<String> items = new ArrayList<>(Arrays.asList("a", "b"));
    AbstractTransferDetailFragment.updateAdapterItems(adapter, items, Arrays.asList("A", "B", "C"));
    assertEquals(Arrays.asList("A", "B", "C"), items);
    assertEquals(List.of("changed0", "changed1", "inserted2"), adapter.events);
  }

  @Test
  public void aSmallerFreshListShrinksTheItems() {
    RecordingAdapter adapter = new RecordingAdapter();
    List<String> items = new ArrayList<>(Arrays.asList("a", "b", "c"));
    AbstractTransferDetailFragment.updateAdapterItems(adapter, items, Arrays.asList("A"));
    assertEquals(List.of("A"), items);
    // one removal notification for each item dropped, at the position the item had; it used to
    // announce the position before it, which is an item that stays
    assertEquals(List.of("changed0", "removed2x1", "removed1x1"), adapter.events);
  }

  @Test
  public void noFreshItemsClearTheList() {
    RecordingAdapter adapter = new RecordingAdapter();
    List<String> items = new ArrayList<>(Arrays.asList("a"));
    AbstractTransferDetailFragment.updateAdapterItems(adapter, items, Collections.emptyList());
    assertTrue(items.isEmpty());
    assertEquals(List.of("all"), adapter.events);
    items.add("b");
    AbstractTransferDetailFragment.updateAdapterItems(adapter, items, null);
    assertTrue(items.isEmpty());
  }

  @Test
  public void aRefreshDuringLayoutIsDeferredToTheUiThread() {
    RecordingAdapter adapter = new RecordingAdapter();
    adapter.throwOnFullRefresh = true;
    List<String> items = new ArrayList<>();
    List<Runnable> posted = new ArrayList<>();
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            i -> {
              posted.add(i.getArgument(0));
              return null;
            });

    AbstractTransferDetailFragment.updateAdapterItems(adapter, items, Arrays.asList("a"));

    assertEquals(1, posted.size());
    adapter.throwOnFullRefresh = false;
    posted.get(0).run();
    assertEquals(List.of("all"), adapter.events);
  }
}
