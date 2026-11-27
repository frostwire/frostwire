/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.AbstractConfirmListDialog;
import com.frostwire.android.gui.dialogs.ConfirmListDialogDefaultAdapter;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialog;
import com.frostwire.android.gui.dialogs.TellurideSearchResultDownloadDialog;
import com.frostwire.android.gui.views.DialogTestHost;
import com.frostwire.android.gui.views.ViewTestSupport;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.search.telluride.TellurideSearchResult;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Row taps, saved state and the lifecycle of the confirm list dialogs.
 *
 * <p>Lives outside the dialogs test package because build.gradle excludes that whole package (its
 * old tests boot MainApplication); these use a plain Application.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ConfirmListInteractionsTest {

  private static final String LIST_JSON =
      "{\"list\":["
          + "{\"index\":0,\"name\":\"a.txt\",\"path\":\"a.txt\",\"size\":10},"
          + "{\"index\":1,\"name\":\"b.txt\",\"path\":\"b.txt\",\"size\":20}]}";

  private DialogTestHost host;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    host = new DialogTestHost();
  }

  private static TellurideSearchResult telluride(String id, String title) {
    return new TellurideSearchResult(
        id, title, title + ".mp4", "youtube", "https://example.com/" + id, "", "", 100, 1L, null);
  }

  private TellurideSearchResultDownloadDialog singleSelection(String... titles) {
    TellurideSearchResult[] options = new TellurideSearchResult[titles.length];
    for (int i = 0; i < titles.length; i++) {
      options[i] = telluride("id" + i, titles[i]);
    }
    return TellurideSearchResultDownloadDialog.newInstance(host.activity, Arrays.asList(options));
  }

  private HandpickedTorrentDownloadDialog multipleSelection() {
    Bundle bundle = new Bundle();
    bundle.putString("title", "t");
    bundle.putString("dialogText", "text");
    bundle.putString("listData", LIST_JSON);
    bundle.putInt(
        "selectionMode", AbstractConfirmListDialog.SelectionMode.MULTIPLE_SELECTION.ordinal());
    HandpickedTorrentDownloadDialog dialog = new HandpickedTorrentDownloadDialog();
    dialog.setArguments(bundle);
    host.show(dialog, "m");
    return dialog;
  }

  @Test
  public void tappingARowSelectsItsRadioButtonInSingleSelection() {
    TellurideSearchResultDownloadDialog dialog = singleSelection("First", "Second");
    host.show(dialog, "s");
    ListView list = dialog.getDialog().findViewById(R.id.dialog_confirm_list_listview);
    ConfirmListDialogDefaultAdapter<TellurideSearchResult> adapter = dialog.getAdapter();
    assertEquals("First", dialog.getSelectedItem().getDisplayName());

    View row = adapter.getView(1, null, list);
    // the art image is a child of the row, tagged with its item like every row view
    row.findViewById(R.id.confirmation_dialog_single_selection_list_item_art).performClick();

    assertEquals("Second", dialog.getSelectedItem().getDisplayName());
    // the choice is kept in the arguments so a recreated dialog starts from it
    assertEquals(1, dialog.getArguments().getInt("lastSelectedRadioButtonIndex"));
  }

  @Test
  public void singleSelectionStateIsSavedWithTheSelectedRadioButton() {
    TellurideSearchResultDownloadDialog dialog = singleSelection("First", "Second");
    host.show(dialog, "s");
    ListView list = dialog.getDialog().findViewById(R.id.dialog_confirm_list_listview);
    dialog
        .getAdapter()
        .getView(1, null, list)
        .findViewById(R.id.confirmation_dialog_single_selection_list_item_art)
        .performClick();

    Bundle saved = new Bundle();
    dialog.onSaveInstanceState(saved);

    assertEquals(1, saved.getInt("lastSelectedRadioButtonIndex"));
  }

  @Test
  public void tappingARowChecksItsBoxInMultipleSelection() {
    HandpickedTorrentDownloadDialog dialog = multipleSelection();
    ListView list = dialog.getDialog().findViewById(R.id.dialog_confirm_list_listview);
    View row = dialog.getAdapter().getView(0, null, list);

    row.findViewById(R.id.confirmation_dialog_multiple_selection_list_item_art).performClick();

    assertEquals(1, dialog.getChecked().size());
  }

  @Test
  public void tappingARowDoesNothingWhenThereIsNoSelection() {
    HandpickedTorrentDownloadDialog dialog = new HandpickedTorrentDownloadDialog();
    Bundle bundle = new Bundle();
    bundle.putString("title", "t");
    bundle.putString("dialogText", "text");
    bundle.putString("listData", LIST_JSON);
    bundle.putInt("selectionMode", AbstractConfirmListDialog.SelectionMode.NO_SELECTION.ordinal());
    dialog.setArguments(bundle);
    host.show(dialog, "n");
    ListView list = dialog.getDialog().findViewById(R.id.dialog_confirm_list_listview);
    View row = dialog.getAdapter().getView(0, null, list);

    row.findViewById(R.id.confirmation_dialog_no_selection_list_item_art).performClick();

    // everything is already part of the answer, and no checkbox is shown
    assertEquals(2, dialog.getChecked().size());
    assertEquals(0, dialog.getAdapter().getCheckedCount());
  }

  @Test
  public void aDialogWithoutArgumentsHasNoTitle() {
    assertNull(new TellurideSearchResultDownloadDialog().getDialogTitle());
  }

  @Test
  public void itemsWithAThumbnailUrlGetItLoadedIntoTheirArt() {
    TellurideSearchResult withThumbnail =
        new TellurideSearchResult(
            "id",
            "With thumb",
            "t.mp4",
            "youtube",
            "https://x",
            "",
            "https://x/t.png",
            100,
            1L,
            null);
    TellurideSearchResultDownloadDialog dialog =
        TellurideSearchResultDownloadDialog.newInstance(
            host.activity, Arrays.asList(withThumbnail));
    host.show(dialog, "s");
    ListView list = dialog.getDialog().findViewById(R.id.dialog_confirm_list_listview);
    FWImageLoader loader = mock(FWImageLoader.class);
    try (MockedStatic<FWImageLoader> images = mockStatic(FWImageLoader.class)) {
      images.when(() -> FWImageLoader.getInstance(any())).thenReturn(loader);

      dialog.getAdapter().getView(0, null, list);

      verify(loader)
          .load(
              eq(Uri.parse("https://x/t.png")),
              any(ImageView.class),
              eq(R.drawable.default_artwork));
    }
  }

  @Test
  public void itemsWithAnUnknownSizeHideTheirSize() {
    TellurideSearchResult unknownSize =
        new TellurideSearchResult(
            "id", "No size", "t.mp4", "youtube", "https://x", "", "", -1, 1L, null);
    TellurideSearchResultDownloadDialog dialog =
        TellurideSearchResultDownloadDialog.newInstance(host.activity, Arrays.asList(unknownSize));
    host.show(dialog, "s");
    ListView list = dialog.getDialog().findViewById(R.id.dialog_confirm_list_listview);

    View row = dialog.getAdapter().getView(0, null, list);

    assertEquals(
        View.GONE,
        row.findViewById(R.id.confirmation_dialog_single_selection_list_item_size).getVisibility());
  }
}
