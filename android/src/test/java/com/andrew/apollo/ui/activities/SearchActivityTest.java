/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.FakeMediaProvider;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** The music search results screen: loading, binding artist, album and song rows, and taps. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchActivityTest {

  private MockedStatic<MusicUtils> music;

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    FakeMediaProvider.install();
  }

  @After
  public void tearDown() {
    if (music != null) {
      music.close();
    }
  }

  private ActivityController<SearchActivity> launch(String query) {
    Intent intent = new Intent();
    if (query != null) {
      intent.putExtra(SearchManager.QUERY, query);
    }
    ActivityController<SearchActivity> controller =
        Robolectric.buildActivity(SearchActivity.class, intent);
    controller.setup();
    settle();
    return controller;
  }

  private static void settle() {
    for (int i = 0; i < 5; i++) {
      shadowOf(Looper.getMainLooper()).idle();
    }
  }

  private static GridView grid(SearchActivity activity) {
    return activity.findViewById(R.id.apollo_activity_search_grid_base);
  }

  private static View row(SearchActivity activity, int position) {
    GridView grid = grid(activity);
    return grid.getAdapter().getView(position, null, grid);
  }

  private static String lineOne(View row) {
    return ((TextView) row.findViewById(R.id.line_one)).getText().toString();
  }

  private static SearchActivity withRows(SearchActivity activity, int rows) throws Exception {
    waitUntil(() -> grid(activity).getAdapter().getCount() == rows);
    return activity;
  }

  private static void waitUntil(Supplier<Boolean> done) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!done.get() && System.nanoTime() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(5);
    }
    assertTrue("timed out", done.get());
  }

  @Test
  public void theQueryIsShownAsTheSubtitle() {
    SearchActivity activity = launch("beatles").get();

    TextView subtitle = activity.findViewById(R.id.view_toolbar_header_subtitle);
    assertEquals(View.VISIBLE, subtitle.getVisibility());
    assertEquals("\"beatles\"", subtitle.getText().toString());
  }

  @Test
  public void withoutAQueryThereIsNoSubtitle() {
    SearchActivity activity = launch(null).get();

    TextView subtitle = activity.findViewById(R.id.view_toolbar_header_subtitle);
    assertTrue(subtitle == null || !"\"null\"".contentEquals(subtitle.getText()));
  }

  @Test
  public void aPortraitScreenListsOneColumnAndALandscapeScreenTwo() {
    SearchActivity portrait = launch("x").get();
    assertEquals(1, grid(portrait).getNumColumns());

    RuntimeEnvironment.setQualifiers("+land");
    SearchActivity landscape = launch("x").get();
    assertEquals(2, grid(landscape).getNumColumns());
  }

  @Test
  public void noMatchesShowTheEmptyMessage() {
    SearchActivity activity = launch("zzz").get();

    TextView empty = activity.findViewById(R.id.apollo_activity_search_text_empty);
    assertEquals(activity.getString(R.string.empty_search), empty.getText().toString());
    assertEquals(0, grid(activity).getAdapter().getCount());
  }

  @Test
  public void everyKindOfMatchIsListedWithItsOwnText() throws Exception {
    FakeMediaProvider.addArtist(1, "Beach Boys");
    FakeMediaProvider.addAlbum(2, "Pet Sounds", "Beach Boys");
    FakeMediaProvider.addSong(3, "God Only Knows", "Pet Sounds", "Beach Boys");
    SearchActivity activity = withRows(launch("beach").get(), 3);

    assertEquals(3, grid(activity).getAdapter().getCount());
    assertEquals("Beach Boys", lineOne(row(activity, 0)));
    assertEquals("Pet Sounds", lineOne(row(activity, 1)));
    View album = row(activity, 1);
    assertEquals("Beach Boys", ((TextView) album.findViewById(R.id.line_two)).getText().toString());
    View song = row(activity, 2);
    assertEquals("God Only Knows", lineOne(song));
    assertEquals(
        "Beach Boys", ((TextView) song.findViewById(R.id.line_three)).getText().toString());
  }

  @Test
  public void aRowOfAnotherTypeIsLeftUntouched() throws Exception {
    FakeMediaProvider.addRow("_id", 4L, "mime_type", "image/png");
    SearchActivity activity = withRows(launch("a").get(), 1);

    View row = row(activity, 0);

    assertEquals("", lineOne(row));
  }

  @Test
  public void typingNarrowsTheSearchAndASubmitHidesTheKeyboard() throws Exception {
    FakeMediaProvider.addSong(3, "Help", "Help!", "The Beatles");
    ActivityController<SearchActivity> controller = launch("be");
    SearchActivity activity = controller.get();
    android.view.Menu menu = new androidx.appcompat.view.menu.MenuBuilder(activity);
    assertTrue(activity.onCreateOptionsMenu(menu));
    SearchView searchView = (SearchView) menu.findItem(R.id.menu_player_search).getActionView();

    assertFalse("an empty change is ignored", activity.onQueryTextChange(""));
    assertTrue(activity.onQueryTextChange("beat"));
    settle();
    assertEquals("%beat%", lastLikeArgument());

    assertFalse("an empty submit is ignored", activity.onQueryTextSubmit(""));
    assertTrue(activity.onQueryTextSubmit("beat"));
    assertNotNull(searchView);
  }

  private static String lastLikeArgument() {
    String[] args = FakeMediaProvider.lastSelectionArgs;
    return args == null || args.length == 0 ? null : args[0];
  }

  @Test
  public void aNewIntentSearchesAgain() throws Exception {
    ActivityController<SearchActivity> controller = launch("one");
    FakeMediaProvider.addSong(3, "Two", "x", "y");

    controller.newIntent(new Intent().putExtra(SearchManager.QUERY, "two"));

    withRows(controller.get(), 1);
    assertEquals("%TWO%".toLowerCase(), FakeMediaProvider.lastSelectionArgs[0].toLowerCase());
  }

  @Test
  public void aScrollThatStopsRefreshesTheList() throws Exception {
    FakeMediaProvider.addSong(3, "Help", "x", "y");
    SearchActivity activity = withRows(launch("h").get(), 1);
    final int[] changes = new int[1];
    grid(activity)
        .getAdapter()
        .registerDataSetObserver(
            new android.database.DataSetObserver() {
              @Override
              public void onChanged() {
                changes[0]++;
              }
            });

    activity.onScrollStateChanged(
        grid(activity), android.widget.AbsListView.OnScrollListener.SCROLL_STATE_FLING);
    assertEquals(0, changes[0]);
    activity.onScrollStateChanged(
        grid(activity), android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
    assertEquals(1, changes[0]);
    activity.onScroll(grid(activity), 0, 1, 1);
  }

  @Test
  public void showingAndLeavingTheScreenReportsTheForegroundChange() {
    music = Mockito.mockStatic(MusicUtils.class, Mockito.CALLS_REAL_METHODS);
    music
        .when(() -> MusicUtils.notifyForegroundStateChanged(Mockito.any(), Mockito.anyBoolean()))
        .thenAnswer(i -> null);

    ActivityController<SearchActivity> controller = launch("x");
    controller.pause().stop();

    music.verify(() -> MusicUtils.notifyForegroundStateChanged(controller.get(), true));
    music.verify(() -> MusicUtils.notifyForegroundStateChanged(controller.get(), false));
  }

  @Test
  public void tappingASongPlaysItAndClosesTheSearch() throws Exception {
    FakeMediaProvider.addSong(33, "Help", "Help!", "The Beatles");
    music = Mockito.mockStatic(MusicUtils.class, Mockito.CALLS_REAL_METHODS);
    music
        .when(() -> MusicUtils.playFDs(Mockito.any(), Mockito.anyInt(), Mockito.anyBoolean()))
        .thenAnswer(i -> null);
    music
        .when(
            () -> MusicUtils.startMusicPlaybackService(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(2)).run();
              return null;
            });
    SearchActivity activity = withRows(launch("help").get(), 1);

    grid(activity).performItemClick(row(activity, 0), 0, 33);

    ArgumentCaptor<long[]> played = ArgumentCaptor.forClass(long[].class);
    music.verify(() -> MusicUtils.playFDs(played.capture(), Mockito.eq(0), Mockito.anyBoolean()));
    assertEquals(33L, played.getValue()[0]);
    assertTrue(activity.isFinishing());
  }

  @Test
  public void tappingAnArtistClosesTheSearch() throws Exception {
    FakeMediaProvider.addArtist(1, "Beach Boys");
    SearchActivity activity = withRows(launch("beach").get(), 1);

    grid(activity).performItemClick(row(activity, 0), 0, 1);

    assertTrue(activity.isFinishing());
  }

  @Test
  public void tappingAnAlbumClosesTheSearch() throws Exception {
    FakeMediaProvider.addAlbum(2, "Pet Sounds", "Beach Boys");
    SearchActivity activity = withRows(launch("pet").get(), 1);

    grid(activity).performItemClick(row(activity, 0), 0, 2);

    assertTrue(activity.isFinishing());
  }
}
