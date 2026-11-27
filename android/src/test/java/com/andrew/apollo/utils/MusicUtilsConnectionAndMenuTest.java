/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.menu.FragmentMenuItems;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The connection the UI keeps to the playback service (its callbacks wait for the service to
 * answer), and the "add to playlist" submenu.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class MusicUtilsConnectionAndMenuTest {

  private Application app;
  private Context context;
  private ApolloServiceFixture fixture;

  @Before
  public void setUp() throws Exception {
    app = ApplicationProvider.getApplicationContext();
    context = app;
    shadowOf(app)
        .setComponentNameAndServiceForBindService(
            new ComponentName(context, MusicPlaybackService.class), new android.os.Binder());
    ApolloServiceFixture.installPlatform(true);
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    FakeConfig.install();
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.stop();
    }
    FakeConfig.uninstall();
    ApolloServiceFixture.noService();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  private ServiceConnection theBoundConnection() {
    List<ServiceConnection> bound = shadowOf(app).getBoundServiceConnections();
    assertFalse("the UI bound to the service", bound.isEmpty());
    return bound.get(bound.size() - 1);
  }

  @Test
  public void aCallbackWaitingForTheServiceRunsOnceItAnswers() throws Exception {
    fixture = ApolloServiceFixture.start();
    AtomicInteger ran = new AtomicInteger();

    MusicUtils.startMusicPlaybackService(
        context, MusicUtils.buildStartMusicPlaybackServiceIntent(context), ran::incrementAndGet);
    idle();

    assertEquals(1, ran.get());
    assertTrue(MusicUtils.isMusicPlaybackServiceRunning());
  }

  @Test
  public void aServiceThatIsNotThereWhenConnectedRunsNoCallbacks() throws Exception {
    AtomicInteger ran = new AtomicInteger();
    MusicUtils.startMusicPlaybackService(
        context, MusicUtils.buildStartMusicPlaybackServiceIntent(context), ran::incrementAndGet);
    ServiceConnection connection = theBoundConnection();
    ApolloServiceFixture.noService();

    connection.onServiceConnected(new ComponentName(context, MusicPlaybackService.class), null);
    idle();

    assertEquals(0, ran.get());
    assertFalse(MusicUtils.isMusicPlaybackServiceRunning());
  }

  @Test
  public void aNullBindingForgetsTheWaitingCallbacks() throws Exception {
    AtomicInteger ran = new AtomicInteger();
    MusicUtils.startMusicPlaybackService(
        context, MusicUtils.buildStartMusicPlaybackServiceIntent(context), ran::incrementAndGet);
    ServiceConnection connection = theBoundConnection();
    connection.onNullBinding(new ComponentName(context, MusicPlaybackService.class));
    fixture = ApolloServiceFixture.start();

    connection.onServiceConnected(new ComponentName(context, MusicPlaybackService.class), null);
    idle();

    assertEquals("nobody is waiting any more", 0, ran.get());
  }

  @Test
  public void afterTheServiceDisconnectsTheNextStartBindsAgain() throws Exception {
    Intent intent = MusicUtils.buildStartMusicPlaybackServiceIntent(context);
    MusicUtils.startMusicPlaybackService(context, intent, () -> {});
    ServiceConnection connection = theBoundConnection();
    fixture = ApolloServiceFixture.start();
    connection.onServiceConnected(new ComponentName(context, MusicPlaybackService.class), null);
    idle();
    int boundBefore = shadowOf(app).getBoundServiceConnections().size();

    connection.onServiceDisconnected(new ComponentName(context, MusicPlaybackService.class));
    AtomicInteger ran = new AtomicInteger();
    MusicUtils.startMusicPlaybackService(context, intent, ran::incrementAndGet);

    assertTrue(
        "a new connection was made",
        shadowOf(app).getBoundServiceConnections().size() > boundBefore);
    assertNotNull(theBoundConnection());
  }

  private static void waitFor(BooleanSupplier condition) {
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      idle();
      Thread.onSpinWait();
    }
    idle();
    assertTrue("timed out waiting", condition.getAsBoolean());
  }

  @Test
  public void theSubmenuOffersFavoritesANewPlaylistAndEveryExistingPlaylist() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            cursor(p, row("_id", 30L, "name", "Road"), row("_id", 31L, "name", "Gym"));
    SubMenu subMenu = new MenuBuilder(context).addSubMenu("Add to");

    MusicUtils.makePlaylistMenu(context, 7, subMenu, true);

    waitFor(() -> subMenu.size() == 4);
    assertEquals(FragmentMenuItems.ADD_TO_FAVORITES, subMenu.getItem(0).getItemId());
    assertEquals(FragmentMenuItems.NEW_PLAYLIST, subMenu.getItem(1).getItemId());
    MenuItem road = subMenu.getItem(2);
    assertEquals(FragmentMenuItems.PLAYLIST_SELECTED, road.getItemId());
    assertEquals("Road", road.getTitle().toString());
    assertEquals(30L, road.getIntent().getLongExtra("playlist", -1));
    assertEquals(31L, subMenu.getItem(3).getIntent().getLongExtra("playlist", -1));
  }

  @Test
  public void theSubmenuWithoutFavoritesStartsWithTheNewPlaylistItem() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);
    SubMenu subMenu = new MenuBuilder(context).addSubMenu("Add to");

    MusicUtils.makePlaylistMenu(context, 7, subMenu, false);

    assertEquals(1, subMenu.size());
    assertEquals(FragmentMenuItems.NEW_PLAYLIST, subMenu.getItem(0).getItemId());
    assertEquals(Menu.NONE, subMenu.getItem(0).getOrder());
    MusicUtils.makePlaylistMenu(null, 7, subMenu, false);
    assertEquals("no context, no menu", 1, subMenu.size());
  }
}
