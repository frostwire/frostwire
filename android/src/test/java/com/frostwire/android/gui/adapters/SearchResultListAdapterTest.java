/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.PreviewPlayerActivity;
import com.frostwire.android.gui.adapters.menu.BrowseSharedTorrentsMenuAction;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Row binding, file-type filtering and menus of the search results list, with fake results. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchResultListAdapterTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final long MINUTE = 60_000L;
  private static final long HOUR = 60 * MINUTE;
  private static final long DAY = 24 * HOUR;

  private Activity activity;
  private SearchResultListAdapter adapter;
  private final List<SearchResult> clicked = new ArrayList<>();
  private MockedStatic<FWImageLoader> loaders;
  private MockedStatic<UIUtils> ui;
  private FWImageLoader imageLoader;

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
    activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
    activity.setTheme(R.style.Theme_FrostWire);
    imageLoader = mock(FWImageLoader.class);
    loaders = mockStatic(FWImageLoader.class);
    loaders.when(() -> FWImageLoader.getInstance(any())).thenReturn(imageLoader);
    ui = mockStatic(UIUtils.class);
    ui.when(() -> UIUtils.getBytesInHuman(org.mockito.ArgumentMatchers.anyDouble()))
        .thenAnswer(i -> i.getArgument(0) + " B");
    adapter =
        new SearchResultListAdapter(activity) {
          @Override
          protected void searchResultClicked(SearchResult sr) {
            clicked.add(sr);
          }
        };
  }

  @After
  public void tearDown() {
    loaders.close();
    ui.close();
  }

  private View row() {
    return View.inflate(activity, R.layout.view_bittorrent_search_result_list_item, null);
  }

  private static String text(View row, int id) {
    return ((TextView) row.findViewById(id)).getText().toString();
  }

  private static FileSearchResult file(String name, String filename) {
    FileSearchResult r = mock(FileSearchResult.class);
    when(r.getDisplayName()).thenReturn(name);
    when(r.getFilename()).thenReturn(filename);
    when(r.getSource()).thenReturn("src");
    return r;
  }

  private static FileSearchResult torrentFile(String name, int seeds, long createdAt) {
    FileSearchResult r =
        mock(FileSearchResult.class, withSettings().extraInterfaces(TorrentSearchResult.class));
    when(r.getDisplayName()).thenReturn(name);
    when(r.getFilename()).thenReturn(name + ".torrent");
    when(r.getSource()).thenReturn("src");
    TorrentSearchResult t = (TorrentSearchResult) r;
    when(t.getSeeds()).thenReturn(seeds);
    when(t.getCreationTime()).thenReturn(createdAt);
    when(t.getHash()).thenReturn(HASH);
    return r;
  }

  private String elapsedFor(long ageMs) {
    View row = row();
    FileSearchResult r = torrentFile("t", 0, System.currentTimeMillis() - ageMs);
    adapter.populateView(row, r);
    return text(row, R.id.view_bittorrent_ssearch_result_list_item_text_age);
  }

  @Test
  public void torrentRowsShowHowLongAgoTheyWereCreated() {
    assertEquals(activity.getString(R.string.one_second), elapsedFor(0));
    assertEquals(activity.getString(R.string.n_minutes, "5"), elapsedFor(5 * MINUTE + 30_000));
    assertEquals(activity.getString(R.string.one_minute), elapsedFor(MINUTE + 10_000));
    assertEquals(activity.getString(R.string.one_hour), elapsedFor(HOUR + MINUTE));
    assertEquals(activity.getString(R.string.n_hours, "5"), elapsedFor(5 * HOUR + MINUTE));
    assertEquals(activity.getString(R.string.one_day), elapsedFor(DAY + HOUR));
    assertEquals(activity.getString(R.string.n_days, "5"), elapsedFor(5 * DAY + HOUR));
    assertEquals(activity.getString(R.string.one_month), elapsedFor(35 * DAY));
    assertEquals(activity.getString(R.string.n_months, "5"), elapsedFor(160 * DAY));
    assertEquals(activity.getString(R.string.one_year), elapsedFor(400 * DAY));
    assertEquals(activity.getString(R.string.n_years, "5"), elapsedFor(5 * 365 * DAY + 30 * DAY));
    // a creation time older than 20 years is treated as unknown
    assertEquals("", elapsedFor(25 * 365 * DAY));
    String seconds = elapsedFor(30_000);
    assertTrue(
        seconds,
        seconds.equals(activity.getString(R.string.n_seconds, "30"))
            || seconds.equals(activity.getString(R.string.n_seconds, "31")));
  }

  @Test
  public void torrentRowShowsSeedCountOnlyWhenThereAreSeeds() {
    View seeded = row();
    adapter.populateView(seeded, torrentFile("seeded", 12, System.currentTimeMillis()));
    assertEquals(
        activity.getResources().getQuantityString(R.plurals.count_seeds_source, 12, 12),
        text(seeded, R.id.view_bittorrent_search_result_list_item_text_seeds));
    View none = row();
    adapter.populateView(none, torrentFile("none", 0, System.currentTimeMillis()));
    assertEquals("", text(none, R.id.view_bittorrent_search_result_list_item_text_seeds));
  }

  @Test
  public void fileRowShowsNameSizeExtensionSourceAndLicense() {
    CompositeFileSearchResult r =
        CompositeFileSearchResult.builder()
            .displayName("Great Song")
            .filename("great.mp3")
            .size(2048)
            .detailsUrl("https://example.com/song")
            .source("Example")
            .license(com.frostwire.licenses.Licenses.MIT)
            .creationTime(1_700_000_000L)
            .build();
    View row = row();
    adapter.populateView(row, r);
    assertEquals("Great Song", text(row, R.id.view_bittorrent_search_result_list_item_title));
    assertEquals("2048.0 B", text(row, R.id.view_bittorrent_search_result_list_item_file_size));
    assertEquals("mp3", text(row, R.id.view_bittorrent_search_result_list_item_text_extra));
    String source = text(row, R.id.view_bittorrent_search_result_list_item_text_source);
    assertTrue(source, source.startsWith("Example - "));
    View sourceLink = row.findViewById(R.id.view_bittorrent_search_result_list_item_text_source);
    assertEquals("https://example.com/song", sourceLink.getTag());
    assertTrue(sourceLink.isClickable());
    sourceLink.performClick();
    ui.verify(() -> UIUtils.openURL(sourceLink.getContext(), "https://example.com/song"));
  }

  @Test
  public void fileRowWithoutSizeOrDetailsUrlHasNoLinkAndShowsEllipsis() {
    CompositeFileSearchResult r =
        CompositeFileSearchResult.builder()
            .displayName("x")
            .filename("x.bin")
            .size(0)
            .source("S")
            .creationTime(1L)
            .build();
    View row = row();
    adapter.populateView(row, r);
    assertEquals("...", text(row, R.id.view_bittorrent_search_result_list_item_file_size));
    View sourceLink = row.findViewById(R.id.view_bittorrent_search_result_list_item_text_source);
    assertNull(sourceLink.getTag());
    sourceLink.performClick();
    ui.verify(() -> UIUtils.openURL(any(), any()), never());
  }

  @Test
  public void thumbnailsAreLoadedAndOnlyAudioAndYouTubeResultsGetAPreviewOverlay() {
    CompositeFileSearchResult withThumb =
        CompositeFileSearchResult.builder()
            .displayName("v")
            .filename("v.mp4")
            .size(1)
            .detailsUrl("https://www.youtube.com/watch?v=abc")
            .source("YouTube")
            .thumbnailUrl("https://img.example.com/t.jpg")
            .creationTime(1L)
            .build();
    View row = row();
    adapter.populateView(row, withThumb);
    verify(imageLoader)
        .load(
            Uri.parse("https://img.example.com/t.jpg"),
            row.findViewById(R.id.view_bittorrent_search_result_list_item_filetype_icon),
            96,
            96,
            R.drawable.list_item_question_mark);
    View icon = row.findViewById(R.id.view_bittorrent_search_result_list_item_filetype_icon);
    assertSame(withThumb, icon.getTag());
    View overlay =
        row.findViewById(
            R.id.view_bittorrent_search_result_list_item_filetype_icon_media_playback_overlay_view);
    assertEquals(View.VISIBLE, overlay.getVisibility());
    // a YouTube row opens its page in the browser instead of the preview player
    icon.performClick();
    ui.verify(
        () ->
            UIUtils.openURL(
                any(), org.mockito.ArgumentMatchers.eq("https://www.youtube.com/watch?v=abc")));

    CompositeFileSearchResult plain =
        CompositeFileSearchResult.builder()
            .displayName("p")
            .filename("p.pdf")
            .size(1)
            .detailsUrl("https://example.com/p")
            .source("S")
            .creationTime(1L)
            .build();
    View plainRow = row();
    adapter.populateView(plainRow, plain);
    assertNull(
        plainRow.findViewById(R.id.view_bittorrent_search_result_list_item_filetype_icon).getTag());
    assertEquals(
        View.GONE,
        plainRow
            .findViewById(
                R.id
                    .view_bittorrent_search_result_list_item_filetype_icon_media_playback_overlay_view)
            .getVisibility());
  }

  @Test
  public void fileTypeSelectsTheListIconOfTheRow() {
    try (MockedStatic<SystemUtils> sys = mockStatic(SystemUtils.class, CALLS_REAL_METHODS)) {
      sys.when(() -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()))
          .thenAnswer(i -> null);
      int[][] expected = {
        {Constants.FILE_TYPE_VIDEOS, R.drawable.list_item_video_icon},
        {Constants.FILE_TYPE_AUDIO, R.drawable.list_item_audio_icon},
        {Constants.FILE_TYPE_PICTURES, R.drawable.list_item_picture_icon},
        {Constants.FILE_TYPE_DOCUMENTS, R.drawable.list_item_document_icon},
        {Constants.FILE_TYPE_APPLICATIONS, R.drawable.list_item_application_icon},
        {Constants.FILE_TYPE_TORRENTS, R.drawable.list_item_torrent_icon},
        {Constants.FILE_TYPE_FILES, R.drawable.list_item_question_mark},
      };
      for (int[] pair : expected) {
        adapter.setFileType(pair[0], null);
        View row = row();
        adapter.populateView(row, file("x", "x.bin"));
        ImageView icon =
            row.findViewById(R.id.view_bittorrent_search_result_list_item_filetype_icon);
        assertEquals(pair[1], shadowOf(icon.getDrawable()).getCreatedFromResId());
      }
    }
  }

  @Test
  public void extractFileSearchResultsKeepsOnlyFileResults() {
    FileSearchResult f = file("f", "f.mp3");
    SearchResult other = mock(SearchResult.class);
    List<FileSearchResult> files =
        SearchResultListAdapter.extractFileSearchResults(Arrays.asList(other, f, other));
    assertEquals(Collections.singletonList(f), files);
  }

  /** Runs the body off the main thread, as the search performer thread would. */
  private static <T> T onBackgroundThread(java.util.concurrent.Callable<T> body) throws Exception {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                result.set(body.call());
              } catch (Throwable e) {
                error.set(e);
              }
            });
    t.start();
    t.join();
    if (error.get() != null) {
      throw new AssertionError(error.get());
    }
    return result.get();
  }

  @Test
  public void filteringCountsEveryMediaTypeAndKeepsOnlyTheRequestedOne() throws Exception {
    List<FileSearchResult> all =
        Arrays.asList(
            file("a", "a.mp3"),
            file("b", "b.mp3"),
            file("c", "c.mp4"),
            file("d", "d.jpg"),
            file("e", "e.apk"),
            file("f", "f.pdf"),
            file("g", "g.torrent"),
            file("h", "h.unknownext"));
    SearchResultListAdapter.FilteredSearchResults filtered =
        onBackgroundThread(() -> adapter.newFilteredSearchResults(all, Constants.FILE_TYPE_AUDIO));
    assertEquals(2, filtered.mediaTypeFiltered.size());
    assertEquals(2, filtered.numAudio);
    assertEquals(2, filtered.numFilteredAudio);
    assertEquals(1, filtered.numVideo);
    assertEquals(0, filtered.numFilteredVideo);
    assertEquals(1, filtered.numPictures);
    assertEquals(1, filtered.numApplications);
    assertEquals(1, filtered.numDocuments);
    assertEquals(1, filtered.numTorrents);

    // appending another type keeps the filtered counts of the copy and adds the new type
    SearchResultListAdapter.FilteredSearchResults appended =
        onBackgroundThread(
            () ->
                adapter.appendFilteredSearchResults(
                    filtered.copyFiltered(), all, Constants.FILE_TYPE_VIDEOS));
    assertEquals(3, appended.mediaTypeFiltered.size());
    assertEquals(2, appended.numFilteredAudio);
    assertEquals(1, appended.numFilteredVideo);
    // the original is untouched
    assertEquals(2, filtered.mediaTypeFiltered.size());
    filtered.clear();
    assertTrue(filtered.mediaTypeFiltered.isEmpty());
  }

  @Test
  public void filteringOnTheMainThreadIsRejected() {
    try {
      adapter.newFilteredSearchResults(Collections.emptyList(), Constants.FILE_TYPE_AUDIO);
      org.junit.Assert.fail("expected the background-thread guard to trip");
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("main thread"));
    }
  }

  @Test
  public void setFileTypeFiltersOffThreadThenReplacesTheVisibleListOnTheMainThread()
      throws Exception {
    List<Runnable> uiQueue = new ArrayList<>();
    AtomicBoolean onUiThread = new AtomicBoolean(true);
    try (MockedStatic<SystemUtils> sys = mockStatic(SystemUtils.class, CALLS_REAL_METHODS)) {
      sys.when(() -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()))
          .thenAnswer(
              i -> {
                Runnable r = i.getArgument(1);
                // simulate the search performer thread: the main-thread check says "background"
                onUiThread.set(false);
                try {
                  r.run();
                } finally {
                  onUiThread.set(true);
                }
                return null;
              });
      sys.when(SystemUtils::isUIThread).thenAnswer(i -> onUiThread.get());
      sys.when(() -> SystemUtils.postToUIThread(any()))
          .thenAnswer(
              i -> {
                uiQueue.add(i.getArgument(0));
                return null;
              });
      adapter.addResults(
          Arrays.asList(file("a", "a.mp3"), file("c", "c.mp4"), mock(SearchResult.class)));
      AtomicReference<SearchResultListAdapter.FilteredSearchResults> delivered =
          new AtomicReference<>();
      adapter.setFileType(Constants.FILE_TYPE_AUDIO, delivered::set);
      assertEquals(Constants.FILE_TYPE_AUDIO, adapter.getFileType());
      assertEquals(1, uiQueue.size());
      assertEquals(0, adapter.getCount());
      uiQueue.remove(0).run();
      assertEquals(1, adapter.getCount());
      assertNotNull(delivered.get());
      assertSame(delivered.get(), adapter.getFilteredSearchResults());

      // appending another type keeps the earlier one in the visible list
      adapter.setFileType(Constants.FILE_TYPE_VIDEOS, true, null);
      uiQueue.remove(0).run();
      assertEquals(2, adapter.getCount());

      // a later change supersedes one still queued for the main thread
      adapter.setFileType(Constants.FILE_TYPE_AUDIO, null);
      adapter.setFileType(Constants.FILE_TYPE_VIDEOS, null);
      uiQueue.remove(0).run();
      assertEquals("superseded update must not touch the list", 2, adapter.getCount());
      uiQueue.remove(0).run();
      assertEquals(1, adapter.getCount());

      // clear() also drops filter updates still queued
      adapter.setFileType(Constants.FILE_TYPE_AUDIO, null);
      adapter.clear();
      uiQueue.remove(0).run();
      assertEquals(0, adapter.getCount());
    }
  }

  @Test
  public void keywordSortPutsTheBestMatchFirst() throws Exception {
    List<Runnable> uiQueue = new ArrayList<>();
    try (MockedStatic<SystemUtils> sys = mockStatic(SystemUtils.class, CALLS_REAL_METHODS)) {
      sys.when(() -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()))
          .thenAnswer(
              i -> {
                ((Runnable) i.getArgument(1)).run();
                return null;
              });
      sys.when(() -> SystemUtils.postToUIThread(any()))
          .thenAnswer(
              i -> {
                uiQueue.add(i.getArgument(0));
                return null;
              });
      FileSearchResult unrelated = file("completely different thing", "z.mp3");
      FileSearchResult match = file("ubuntu linux iso", "u.iso");
      adapter.addResults(Arrays.asList(unrelated, match));
      adapter.sortByKeywordsRelevance("");
      adapter.sortByKeywordsRelevance(null);
      assertTrue(uiQueue.isEmpty());
      adapter.sortByKeywordsRelevance("ubuntu linux");
      assertEquals(1, uiQueue.size());
      uiQueue.get(0).run();
      assertSame(match, adapter.getFullList().get(0));
      assertSame(unrelated, adapter.getFullList().get(1));
    }
  }

  @Test
  public void clickingARowReportsItsSearchResult() {
    FileSearchResult r = file("r", "r.mp3");
    View row = row();
    row.setTag(r);
    // the click path of the list reports the tagged result to the subclass
    adapter.onItemClicked(row);
    assertEquals(Collections.singletonList(r), clicked);
  }

  @Test
  public void longPressMenuIsOfferedOnlyForBrowsableDistributedResults() {
    byte[] holder = new byte[32];
    holder[0] = 3;
    String b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(holder);
    FileSearchResult browsable = file("Shared", "s.torrent");
    when(browsable.getDetailsUrl())
        .thenReturn("magnet:?xt=urn:btih:" + HASH + "&x.hc=1&x.hp=" + b64);
    View row = row();
    row.setTag(browsable);
    MenuAdapter menu = adapter.getMenuAdapter(row);
    assertNotNull(menu);
    assertEquals("Shared", menu.getTitle());
    assertEquals(1, menu.getCount());
    assertTrue(menu.getItem(0) instanceof BrowseSharedTorrentsMenuAction);

    FileSearchResult noFlag = file("n", "n.torrent");
    when(noFlag.getDetailsUrl()).thenReturn("magnet:?xt=urn:btih:" + HASH + "&x.hp=" + b64);
    row.setTag(noFlag);
    assertNull(adapter.getMenuAdapter(row));

    FileSearchResult noHolder = file("h", "h.torrent");
    when(noHolder.getDetailsUrl()).thenReturn("magnet:?xt=urn:btih:" + HASH + "&x.hc=1");
    row.setTag(noHolder);
    assertNull(adapter.getMenuAdapter(row));

    row.setTag("not a result");
    assertNull(adapter.getMenuAdapter(row));
    assertNull(adapter.getMenuAdapter(null));
  }

  @Test
  public void previewClickOnAStreamableResultOpensThePreviewPlayerOnTheUiThread() {
    FileSearchResult r =
        mock(FileSearchResult.class, withSettings().extraInterfaces(StreamableSearchResult.class));
    when(r.getDisplayName()).thenReturn("Stream");
    when(r.getSource()).thenReturn("SoundCloud");
    when(((StreamableSearchResult) r).getStreamUrl()).thenReturn("https://cdn.example.com/s.mp3");
    ExecutorService pool = mock(ExecutorService.class);
    org.mockito.Mockito.doAnswer(
            i -> {
              ((Runnable) i.getArgument(0)).run();
              return null;
            })
        .when(pool)
        .execute(any());
    Engine engine = mock(Engine.class);
    when(engine.getThreadPool()).thenReturn(pool);
    View row = row();
    adapter.populateView(row, file("seed", "seed.mp3"));
    ImageView icon = row.findViewById(R.id.view_bittorrent_search_result_list_item_filetype_icon);
    icon.setTag(r);
    try (MockedStatic<Engine> engines = mockStatic(Engine.class)) {
      engines.when(Engine::instance).thenReturn(engine);
      icon.performClick();
      shadowOf(Looper.getMainLooper()).idle();
    }
    Intent started = shadowOf(activity).getNextStartedActivity();
    assertNotNull(started);
    assertEquals(PreviewPlayerActivity.class.getName(), started.getComponent().getClassName());
    assertEquals("Stream", started.getStringExtra("displayName"));
    assertEquals("https://cdn.example.com/s.mp3", started.getStringExtra("streamUrl"));
    assertEquals("SoundCloud", started.getStringExtra("source"));
    assertFalse(started.getBooleanExtra("hasVideo", true));
  }

  @Test
  public void previewClickWithoutATagDoesNothing() {
    View row = row();
    adapter.populateView(row, file("plain", "plain.pdf"));
    View icon = row.findViewById(R.id.view_bittorrent_search_result_list_item_filetype_icon);
    icon.performClick();
    assertNull(shadowOf(activity).getNextStartedActivity());
    ui.verify(() -> UIUtils.openURL(any(), any()), never());
    verify(imageLoader, never())
        .load(any(Uri.class), any(ImageView.class), anyInt(), anyInt(), anyInt());
  }
}
