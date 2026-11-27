/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.AbstractConfirmListDialog;
import com.frostwire.android.gui.dialogs.ConfirmListDialogDefaultAdapter;
import com.frostwire.android.gui.dialogs.ConfirmSoundcloudDownloadDialog;
import com.frostwire.android.gui.dialogs.TellurideSearchResultDownloadDialog;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.DialogTestHost;
import com.frostwire.android.gui.views.ViewTestSupport;
import com.frostwire.search.soundcloud.SoundcloudItem;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.HttpClientFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Lives outside the dialogs test package because build.gradle excludes that whole package (its old
 * tests boot MainApplication); these use a plain Application.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ConfirmListDialogsTest {

  private DialogTestHost host;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<SearchFragment> search;
  private MockedStatic<HttpClientFactory> http;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    host = new DialogTestHost();
    ui = Mockito.mockStatic(UIUtils.class, Mockito.CALLS_REAL_METHODS);
    search = Mockito.mockStatic(SearchFragment.class);
    // the real HTTP client can not be serialized to JSON; a result without one can
    http = Mockito.mockStatic(HttpClientFactory.class);
  }

  @After
  public void tearDown() {
    http.close();
    search.close();
    ui.close();
  }

  private static TellurideSearchResult telluride(String id, String title, long size) {
    return new TellurideSearchResult(
        id,
        title,
        title + ".mp4",
        "youtube",
        "https://example.com/watch?v=" + id,
        "https://example.com/dl/" + id,
        "",
        size,
        1L,
        null);
  }

  private static SoundcloudSearchResult soundcloud(long id, String title) {
    SoundcloudItem item = new SoundcloudItem();
    item.id = id;
    item.title = title;
    item.permalink = "track-" + id;
    item.permalink_url = "https://soundcloud.com/a/track-" + id;
    item.created_at = "2020/01/01 00:00:00 +0000";
    return new SoundcloudSearchResult(item, "client", "version");
  }

  // ---- single selection: Telluride

  @Test
  public void tellurideDialogListsTheOptionsWithTheFirstSelected() {
    Context ctx = host.activity;
    TellurideSearchResultDownloadDialog dialog =
        TellurideSearchResultDownloadDialog.newInstance(
            ctx, Arrays.asList(telluride("a", "Video A", 1000), telluride("b", "Audio B", 2000)));
    Dialog shown = host.show(dialog, "t");

    assertEquals(ctx.getString(R.string.downloads), dialog.getDialogTitle());
    assertEquals(
        ctx.getString(R.string.select_option_below),
        ((TextView) shown.findViewById(R.id.dialog_confirm_list_text)).getText().toString());
    ConfirmListDialogDefaultAdapter<TellurideSearchResult> adapter = dialog.getAdapter();
    assertEquals(2, adapter.getCount());
    assertEquals("Video A", dialog.getSelectedItem().getDisplayName());
    // single selection has no summary nor select all
    assertEquals(
        View.GONE, shown.findViewById(R.id.dialog_confirm_list_selection_summary).getVisibility());
    assertEquals(
        View.GONE,
        shown.findViewById(R.id.dialog_confirm_list_select_all_checkbox).getVisibility());
    assertEquals(1, dialog.getChecked().size());
  }

  @Test
  public void tellurideAdapterPicksTheIconByNameAndFormatsTheItems() {
    TellurideSearchResultDownloadDialog dialog =
        TellurideSearchResultDownloadDialog.newInstance(
            host.activity, Arrays.asList(telluride("a", "Video A", 1000)));
    host.show(dialog, "t");
    TellurideSearchResultDownloadDialog.TellurideSearchResultDownloadDialogAdapter adapter =
        new TellurideSearchResultDownloadDialog.TellurideSearchResultDownloadDialogAdapter(
            host.activity,
            new ArrayList<>(),
            AbstractConfirmListDialog.SelectionMode.SINGLE_SELECTION);
    TellurideSearchResult audio = telluride("c", "Audio only", 5);
    TellurideSearchResult video = telluride("d", "Full video", 6);
    assertEquals(R.drawable.list_item_audio_icon, adapter.getItemThumbnailResourceId(audio));
    assertEquals(R.drawable.list_item_video_icon, adapter.getItemThumbnailResourceId(video));
    assertEquals("Audio only", adapter.getItemTitle(audio).toString());
    assertEquals(5L, adapter.getItemSize(audio));
    assertEquals("", adapter.getItemThumbnailUrl(audio));
    adapter.addItem(video);
    ShadowLooper.idleMainLooper();
    assertEquals(1, adapter.getCount());
  }

  @Test
  public void tellurideYesStartsTheSelectedDownloadAndDismisses() {
    List<TellurideSearchResult> options =
        Arrays.asList(telluride("a", "Video A", 1000), telluride("b", "Audio B", 2000));
    TellurideSearchResultDownloadDialog dialog =
        TellurideSearchResultDownloadDialog.newInstance(host.activity, options);
    Dialog shown = host.show(dialog, "t");

    shown.findViewById(R.id.dialog_confirm_list_button_yes).performClick();

    search.verify(
        () ->
            SearchFragment.startDownload(
                any(), Mockito.argThat(r -> "Video A".equals(r.getDisplayName())), anyString()));
    assertFalse(shown.isShowing());
  }

  @Test
  public void noDismissesWithoutStartingAnything() {
    TellurideSearchResultDownloadDialog dialog =
        TellurideSearchResultDownloadDialog.newInstance(
            host.activity, Arrays.asList(telluride("a", "Video A", 1000)));
    Dialog shown = host.show(dialog, "t");
    shown.findViewById(R.id.dialog_confirm_list_button_no).performClick();
    assertFalse(shown.isShowing());
    search.verifyNoInteractions();
  }

  @Test
  public void showingTheTellurideDialogTwiceKeepsASingleInstance() {
    FragmentActivity activity = host.activity;
    List<TellurideSearchResult> options = Arrays.asList(telluride("a", "Video A", 1000));
    TellurideSearchResultDownloadDialog.show(
        activity, activity.getSupportFragmentManager(), options);
    ShadowLooper.idleMainLooper();
    assertNotNull(
        activity
            .getSupportFragmentManager()
            .findFragmentByTag("TELLURIDE_SEARCH_RESULT_DOWNLOAD_DIALOG"));
    TellurideSearchResultDownloadDialog.show(
        activity, activity.getSupportFragmentManager(), options);
    ShadowLooper.idleMainLooper();
    assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
  }

  @Test
  public void tellurideDeserializeOfGarbageIsNull() {
    TellurideSearchResultDownloadDialog dialog = new TellurideSearchResultDownloadDialog();
    assertNull(dialog.deserializeData("{}"));
    assertNull(dialog.deserializeData("null"));
  }

  // ---- Soundcloud
  //
  // Showing the dialog is not tested: JsonUtils.toObject() can not rebuild a SoundcloudSearchResult
  // (its BooleanSupplier field is an interface), so onCreateDialog throws a JsonIOException.

  @Test
  public void soundcloudDialogKeepsItsTitleInItsArguments() {
    ConfirmSoundcloudDownloadDialog dialog =
        ConfirmSoundcloudDownloadDialog.newInstance(
            host.activity, "Confirm", "Sure?", Arrays.asList(soundcloud(1, "One")));
    assertEquals("Confirm", dialog.getDialogTitle());
    assertEquals(0, dialog.getChecked().size());
    assertEquals(0, dialog.getList().size());
    assertEquals(0, dialog.getSelected().length);
    assertNull(dialog.getSelectedItem());
    assertNull(dialog.getAdapter());
  }

  @Test
  public void soundcloudAdapterDescribesItsItemsAndSumsTheChecked() {
    SoundcloudSearchResult track = soundcloud(7, "Seven");
    ConfirmSoundcloudDownloadDialog dialog = new ConfirmSoundcloudDownloadDialog();
    ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> adapter =
        dialog.createAdapter(
            host.activity,
            new ArrayList<>(Arrays.asList(track)),
            AbstractConfirmListDialog.SelectionMode.MULTIPLE_SELECTION,
            null);
    assertEquals("Seven", adapter.getItemTitle(track).toString());
    assertEquals(track.getSize(), adapter.getItemSize(track));
    assertEquals(track.getThumbnailUrl(), adapter.getItemThumbnailUrl(track));
    assertEquals(-1, adapter.getItemThumbnailResourceId(track));
    assertNull(adapter.getCheckedSum());
    adapter.getChecked().add(track);
    assertEquals(UIUtils.getBytesInHuman(track.getSize()), adapter.getCheckedSum());
  }
}
