/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.AbstractConfirmListDialog;
import com.frostwire.android.gui.dialogs.ConfirmListDialogDefaultAdapter;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialog;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.DialogTestHost;
import com.frostwire.android.gui.views.ViewTestSupport;
import com.frostwire.android.util.SystemUtils;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The multiple-selection confirm list, driven through the torrent file picker. The dialog is given
 * its arguments directly because newInstance needs a real TorrentInfo, and jlibtorrent's native
 * library is not available to unit tests.
 *
 * <p>Lives outside the dialogs test package because build.gradle excludes that whole package (its
 * old tests boot MainApplication); these use a plain Application.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class HandpickedTorrentDownloadDialogTest {

  private static final String LIST_JSON =
      "{\"list\":["
          + "{\"index\":0,\"name\":\"b.mp3\",\"path\":\"dir/b.mp3\",\"size\":1000},"
          + "{\"index\":1,\"name\":\"a.txt\",\"path\":\"dir/a.txt\",\"size\":2000},"
          + "{\"index\":2,\"name\":\"c.avi\",\"path\":\"dir/c.avi\",\"size\":3000}]}";

  private DialogTestHost host;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<TransferManager> managers;
  private MockedStatic<UIUtils> ui;
  private TransferManager manager;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    host = new DialogTestHost();
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
              return null;
            });
    manager = mock(TransferManager.class);
    managers = mockStatic(TransferManager.class);
    managers.when(TransferManager::instance).thenReturn(manager);
    ui = mockStatic(UIUtils.class, CALLS_REAL_METHODS);
  }

  @After
  public void tearDown() {
    ui.close();
    managers.close();
    system.close();
  }

  private static Bundle arguments(AbstractConfirmListDialog.SelectionMode mode) {
    Bundle bundle = new Bundle();
    bundle.putString("title", "torrent name");
    bundle.putString("dialogText", "pick");
    bundle.putString("listData", LIST_JSON);
    bundle.putInt("selectionMode", mode.ordinal());
    return bundle;
  }

  private Dialog show(HandpickedTorrentDownloadDialog dialog, Bundle arguments) {
    dialog.setArguments(arguments);
    return host.show(dialog, "h");
  }

  private HandpickedTorrentDownloadDialog multiple(boolean[] checkedOffsets) {
    Bundle bundle = arguments(AbstractConfirmListDialog.SelectionMode.MULTIPLE_SELECTION);
    if (checkedOffsets != null) {
      bundle.putBooleanArray("checkedOffsets", checkedOffsets);
    }
    HandpickedTorrentDownloadDialog dialog = new HandpickedTorrentDownloadDialog();
    show(dialog, bundle);
    return dialog;
  }

  private static CheckBox checkboxOfRow(
      HandpickedTorrentDownloadDialog dialog, ListView list, int row) {
    View view = dialog.getAdapter().getView(row, null, list);
    return view.findViewById(R.id.view_selectable_list_item_checkbox);
  }

  @Test
  public void listsTheFilesSortedByNameWithTheRestoredSelectionChecked() {
    HandpickedTorrentDownloadDialog dialog = multiple(new boolean[] {true, false, true});
    Dialog shown = dialog.getDialog();

    assertEquals("torrent name", dialog.getDialogTitle());
    ConfirmListDialogDefaultAdapter<HandpickedTorrentDownloadDialog.TorrentFileEntry> adapter =
        dialog.getAdapter();
    assertEquals(3, adapter.getCount());
    assertEquals("a.txt", adapter.getItem(0).getDisplayName());
    assertEquals("b.mp3", adapter.getItem(1).getDisplayName());
    assertEquals("c.avi", adapter.getItem(2).getDisplayName());
    // the offsets are positions in the sorted list
    assertEquals(2, dialog.getChecked().size());
    assertEquals(
        "2 " + host.activity.getString(R.string.selected),
        ((TextView) shown.findViewById(R.id.dialog_confirm_list_num_checked_textview))
            .getText()
            .toString());
    assertEquals(
        View.VISIBLE,
        shown.findViewById(R.id.dialog_confirm_list_selection_summary).getVisibility());
    assertFalse(
        ((CheckBox) shown.findViewById(R.id.dialog_confirm_list_select_all_checkbox)).isChecked());
  }

  @Test
  public void adapterDescribesEntriesAndSumsTheChecked() {
    HandpickedTorrentDownloadDialog dialog = multiple(null);
    ConfirmListDialogDefaultAdapter<HandpickedTorrentDownloadDialog.TorrentFileEntry> adapter =
        dialog.getAdapter();
    HandpickedTorrentDownloadDialog.TorrentFileEntry entry = adapter.getItem(2);
    assertEquals("c.avi", adapter.getItemTitle(entry).toString());
    assertEquals(3000L, adapter.getItemSize(entry));
    assertEquals("dir/c.avi", entry.getPath());
    assertEquals(2, entry.getIndex());
    assertEquals(3000L, entry.getSize());
    assertNull(adapter.getItemThumbnailUrl(entry));
    assertTrue(adapter.getItemThumbnailResourceId(entry) != 0);
    assertNull(adapter.getCheckedSum());
    adapter.getChecked().add(entry);
    adapter.getChecked().add(adapter.getItem(0));
    assertEquals(UIUtils.getBytesInHuman(5000), adapter.getCheckedSum());
  }

  @Test
  public void checkingFilesUpdatesTheSelectionTheSummaryAndTheSelectAllBox() {
    HandpickedTorrentDownloadDialog dialog = multiple(null);
    Dialog shown = dialog.getDialog();
    ListView list = shown.findViewById(R.id.dialog_confirm_list_listview);
    CheckBox all = shown.findViewById(R.id.dialog_confirm_list_select_all_checkbox);
    assertEquals(0, dialog.getChecked().size());
    assertEquals(
        View.GONE, shown.findViewById(R.id.dialog_confirm_list_selection_summary).getVisibility());

    checkboxOfRow(dialog, list, 0).setChecked(true);
    assertEquals(1, dialog.getChecked().size());
    assertFalse(all.isChecked());
    boolean[] selected = dialog.getSelected();
    assertTrue(selected[0]);
    assertFalse(selected[1]);

    checkboxOfRow(dialog, list, 1).setChecked(true);
    checkboxOfRow(dialog, list, 2).setChecked(true);
    assertTrue(all.isChecked());

    checkboxOfRow(dialog, list, 1).setChecked(false);
    assertFalse(all.isChecked());
    assertEquals(2, dialog.getChecked().size());
  }

  @Test
  public void selectAllBoxChecksAndClearsEverything() {
    HandpickedTorrentDownloadDialog dialog = multiple(null);
    CheckBox all = dialog.getDialog().findViewById(R.id.dialog_confirm_list_select_all_checkbox);
    all.setChecked(true);
    ShadowLooper.idleMainLooper();
    assertEquals(3, dialog.getChecked().size());
    assertEquals(3, dialog.getSelected().length);
    all.setChecked(false);
    ShadowLooper.idleMainLooper();
    assertEquals(0, dialog.getChecked().size());
    assertEquals(0, dialog.getSelected().length);
  }

  @Test
  public void startingWithTheSelectionDismissesAndCountsTheStartedTransfer() {
    HandpickedTorrentDownloadDialog dialog = multiple(new boolean[] {true, true, false});
    Dialog shown = dialog.getDialog();

    shown.findViewById(R.id.dialog_confirm_list_button_yes).performClick();
    ShadowLooper.idleMainLooper();

    verify(manager).incrementStartedTransfers();
    assertFalse(shown.isShowing());
  }

  @Test
  public void startingWithNothingCheckedKeepsTheDialogOpen() {
    HandpickedTorrentDownloadDialog dialog = multiple(null);
    Dialog shown = dialog.getDialog();
    shown.findViewById(R.id.dialog_confirm_list_button_yes).performClick();
    assertTrue(shown.isShowing());
    org.mockito.Mockito.verifyNoInteractions(manager);
  }

  @Test
  public void noJustDismisses() {
    HandpickedTorrentDownloadDialog dialog = multiple(new boolean[] {true, true, true});
    Dialog shown = dialog.getDialog();
    shown.findViewById(R.id.dialog_confirm_list_button_no).performClick();
    assertFalse(shown.isShowing());
    org.mockito.Mockito.verifyNoInteractions(manager);
  }

  @Test
  public void theSelectionSurvivesASaveOfTheInstanceState() {
    HandpickedTorrentDownloadDialog dialog = multiple(new boolean[] {false, true, false});
    Bundle saved = new Bundle();
    dialog.onSaveInstanceState(saved);
    boolean[] offsets = saved.getBooleanArray("checkedOffsets");
    assertEquals(3, offsets.length);
    assertFalse(offsets[0]);
    assertTrue(offsets[1]);
    assertFalse(offsets[2]);
  }

  @Test
  public void singleSelectionRemembersTheRadioButtonInTheSavedState() {
    HandpickedTorrentDownloadDialog dialog = new HandpickedTorrentDownloadDialog();
    Bundle bundle = arguments(AbstractConfirmListDialog.SelectionMode.SINGLE_SELECTION);
    bundle.putInt("lastSelectedRadioButtonIndex", 2);
    show(dialog, bundle);
    assertEquals("c.avi", dialog.getSelectedItem().getDisplayName());
    assertEquals(1, dialog.getChecked().size());
    Bundle saved = new Bundle();
    dialog.onSaveInstanceState(saved);
    assertEquals(2, saved.getInt("lastSelectedRadioButtonIndex"));
    assertEquals(
        View.GONE,
        dialog
            .getDialog()
            .findViewById(R.id.dialog_confirm_list_selection_summary)
            .getVisibility());
  }

  @Test
  public void noSelectionModeChecksEverythingAndHidesSelectAll() {
    HandpickedTorrentDownloadDialog dialog = new HandpickedTorrentDownloadDialog();
    show(dialog, arguments(AbstractConfirmListDialog.SelectionMode.NO_SELECTION));
    assertEquals(3, dialog.getChecked().size());
    assertEquals(3, dialog.getList().size());
    assertEquals(
        View.GONE,
        dialog
            .getDialog()
            .findViewById(R.id.dialog_confirm_list_select_all_checkbox)
            .getVisibility());
  }

  @Test
  public void selectionModeFromAnUnknownNumberIsNoSelection() {
    assertEquals(
        AbstractConfirmListDialog.SelectionMode.NO_SELECTION,
        AbstractConfirmListDialog.SelectionMode.fromInt(99));
    assertEquals(
        AbstractConfirmListDialog.SelectionMode.SINGLE_SELECTION,
        AbstractConfirmListDialog.SelectionMode.fromInt(1));
    assertEquals(
        AbstractConfirmListDialog.SelectionMode.MULTIPLE_SELECTION,
        AbstractConfirmListDialog.SelectionMode.fromInt(2));
  }

  @Test
  public void deserializingNothingGivesAnEmptyList() {
    HandpickedTorrentDownloadDialog dialog = new HandpickedTorrentDownloadDialog();
    assertTrue(dialog.deserializeData(null).isEmpty());
    assertTrue(dialog.deserializeData("").isEmpty());
    assertTrue(dialog.deserializeData("null").isEmpty());
    List<HandpickedTorrentDownloadDialog.TorrentFileEntry> entries =
        dialog.deserializeData(LIST_JSON);
    assertEquals(3, entries.size());
  }
}
