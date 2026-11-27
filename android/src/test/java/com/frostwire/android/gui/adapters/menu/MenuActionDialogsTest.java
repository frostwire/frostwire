/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Dialog;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.Transfer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/** The dialog-driven context-menu actions of transfers and music files, with fake backends. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MenuActionDialogsTest {

  /** An activity that is a TimerObserver, like the screens that host transfer menus. */
  public static final class ObserverActivity extends AppCompatActivity implements TimerObserver {
    final AtomicInteger ticks = new AtomicInteger();

    @Override
    public void onTime() {
      ticks.incrementAndGet();
    }
  }

  private ObserverActivity activity;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<MusicUtils> music;

  @Before
  public void setUp() {
    org.robolectric.android.controller.ActivityController<ObserverActivity> controller =
        Robolectric.buildActivity(ObserverActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    activity = controller.get();
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()))
        .thenAnswer(
            i -> {
              ((Runnable) i.getArgument(1)).run();
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any()))
        .thenAnswer(
            i -> {
              ((Runnable) i.getArgument(0)).run();
              return null;
            });
    music = mockStatic(MusicUtils.class);
  }

  @After
  public void tearDown() {
    system.close();
    music.close();
  }

  private <T extends Fragment> T shownDialog(Class<T> type) {
    shadowOf(Looper.getMainLooper()).idle();
    for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
      if (type.isInstance(f) && f.isAdded()) {
        return type.cast(f);
      }
    }
    return null;
  }

  private static Dialog dialogOf(Fragment f) {
    return ((androidx.fragment.app.DialogFragment) f).getDialog();
  }

  // ---- CancelMenuAction

  @Test
  public void cancelActionTextDependsOnCompletionAndWhetherDataIsDeleted() {
    Transfer running = mock(Transfer.class);
    Transfer complete = mock(Transfer.class);
    when(complete.isComplete()).thenReturn(true);
    assertEquals(
        activity.getString(R.string.cancel_menu_action),
        new CancelMenuAction(activity, running, false).getText());
    assertEquals(
        activity.getString(R.string.clear_complete),
        new CancelMenuAction(activity, complete, false).getText());
    assertEquals(
        activity.getString(R.string.cancel_delete_menu_action),
        new CancelMenuAction(activity, running, true).getText());
    assertEquals(
        activity.getString(R.string.remove_torrent_and_data),
        new CancelMenuAction(activity, mock(UIBittorrentDownload.class), true, true).getText());
  }

  @Test
  public void confirmingTheCancelDialogRemovesAPlainTransferAndDismisses() {
    HttpDownload download = mock(HttpDownload.class);
    new CancelMenuAction(activity, download, true).onClick();
    CancelMenuAction.CancelMenuActionDialog dialog =
        shownDialog(CancelMenuAction.CancelMenuActionDialog.class);
    assertNotNull(dialog);
    Dialog dlg = dialogOf(dialog);
    assertEquals(
        activity.getString(R.string.yes_no_cancel_delete_transfer_question),
        ((TextView) dlg.findViewById(R.id.dialog_default_text)).getText().toString());
    ((Button) dlg.findViewById(R.id.dialog_default_button_yes)).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    verify(download).remove(true);
    assertNull(shownDialog(CancelMenuAction.CancelMenuActionDialog.class));
  }

  @Test
  public void cloudDownloadsGetTheCloudQuestionAndKeepTheirDataWhenNotDeleting() {
    HttpDownload download = mock(HttpDownload.class);
    new CancelMenuAction(activity, download, false).onClick();
    Dialog dlg = dialogOf(shownDialog(CancelMenuAction.CancelMenuActionDialog.class));
    assertEquals(
        activity.getString(R.string.yes_no_cancel_transfer_question_cloud),
        ((TextView) dlg.findViewById(R.id.dialog_default_text)).getText().toString());
    ((Button) dlg.findViewById(R.id.dialog_default_button_yes)).performClick();
    verify(download).remove(false);
  }

  @Test
  public void confirmingForATorrentPausesItThenRemovesItWithItsFlags() {
    UIBittorrentDownload torrent = mock(UIBittorrentDownload.class);
    new CancelMenuAction(activity, torrent, true, false).onClick();
    Dialog dlg = dialogOf(shownDialog(CancelMenuAction.CancelMenuActionDialog.class));
    ((Button) dlg.findViewById(R.id.dialog_default_button_yes)).performClick();
    verify(torrent).pause();
    verify(torrent).remove(any(), eq(true), eq(false));
  }

  @Test
  public void decliningTheCancelDialogKeepsTheTransferAndTicksTheTimer() {
    HttpDownload download = mock(HttpDownload.class);
    new CancelMenuAction(activity, download, false).onClick();
    Dialog dlg = dialogOf(shownDialog(CancelMenuAction.CancelMenuActionDialog.class));
    ((Button) dlg.findViewById(R.id.dialog_default_button_no)).performClick();
    verify(download, never()).remove(anyBoolean());
  }

  // ---- RenameFileMenuAction

  private FWFileDescriptor fileDescriptor(String path) {
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.filePath = path;
    fd.title = "old";
    return fd;
  }

  @Test
  public void renameDialogStartsWithTheCurrentNameAndRenamesOnOk() {
    FWFileDescriptor fd = fileDescriptor("/sdcard/Music/old.mp3");
    Librarian librarian = mock(Librarian.class);
    when(librarian.renameFile(any(), eq(fd), eq("new.mp3"))).thenReturn("/sdcard/Music/new.mp3");
    List<String> clicks = new ArrayList<>();
    try (MockedStatic<Librarian> libs = mockStatic(Librarian.class)) {
      libs.when(Librarian::instance).thenReturn(librarian);
      RenameFileMenuAction action =
          new RenameFileMenuAction(activity, fd, (tag, which) -> clicks.add(tag + ":" + which));
      assertEquals("/sdcard/Music/old.mp3", action.getFilePath());
      action.onClick();
      RenameFileMenuAction.RenameFileMenuActionDialog dialog =
          shownDialog(RenameFileMenuAction.RenameFileMenuActionDialog.class);
      assertNotNull(dialog);
      Dialog dlg = dialogOf(dialog);
      EditText input = dlg.findViewById(R.id.dialog_default_input_text);
      assertEquals("old", input.getText().toString());
      input.setText("new.mp3");
      ((Button) dlg.findViewById(R.id.dialog_default_input_button_yes)).performClick();
      assertEquals("/sdcard/Music/new.mp3", fd.filePath);
      assertEquals("new", fd.title);
      assertEquals(Arrays.asList("new.mp3:1"), clicks);
    }
  }

  @Test
  public void renameRejectsReservedCharactersAndCancelJustCloses() {
    FWFileDescriptor fd = fileDescriptor("/sdcard/Music/old.mp3");
    try (MockedStatic<Librarian> libs = mockStatic(Librarian.class);
        MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      Librarian librarian = mock(Librarian.class);
      libs.when(Librarian::instance).thenReturn(librarian);
      List<String> clicks = new ArrayList<>();
      new RenameFileMenuAction(activity, fd, (tag, which) -> clicks.add(tag + ":" + which))
          .onClick();
      Dialog dlg = dialogOf(shownDialog(RenameFileMenuAction.RenameFileMenuActionDialog.class));
      ((EditText) dlg.findViewById(R.id.dialog_default_input_text)).setText("bad:name?.mp3");
      ((Button) dlg.findViewById(R.id.dialog_default_input_button_yes)).performClick();
      ui.verify(() -> UIUtils.showLongMessage(activity, R.string.invalid_filename));
      verify(librarian, never()).renameFile(any(), any(), anyString());
      assertEquals("/sdcard/Music/old.mp3", fd.filePath);

      new RenameFileMenuAction(activity, fd, (tag, which) -> clicks.add(tag + ":" + which))
          .onClick();
      Dialog second = dialogOf(shownDialog(RenameFileMenuAction.RenameFileMenuActionDialog.class));
      ((Button) second.findViewById(R.id.dialog_default_input_button_no)).performClick();
      assertTrue(clicks.contains("null:0"));
    }
  }

  // ---- FileInformationAction

  @Test
  public void fileInformationDialogShowsNameSizeDateAndPathAndOkCloses() {
    FWFileDescriptor fd = fileDescriptor("/sdcard/Music/song.mp3");
    fd.fileSize = 2048;
    fd.dateAdded = 86_400L * 365; // 1971-01-01 UTC
    new FileInformationAction(activity, fd).onClick();
    FileInformationAction.FileInformationDialog dialog =
        shownDialog(FileInformationAction.FileInformationDialog.class);
    assertNotNull(dialog);
    Dialog dlg = dialogOf(dialog);
    assertEquals(
        "song.mp3",
        ((TextView) dlg.findViewById(R.id.dialog_file_information_filename)).getText().toString());
    assertEquals(
        "/sdcard/Music/song.mp3",
        ((TextView) dlg.findViewById(R.id.dialog_file_information_storage_path))
            .getText()
            .toString());
    assertEquals(
        UIUtils.getBytesInHuman(2048),
        ((TextView) dlg.findViewById(R.id.dialog_file_information_filesize)).getText().toString());
    String date =
        ((TextView) dlg.findViewById(R.id.dialog_file_information_date_created))
            .getText()
            .toString();
    assertTrue(date, date.matches("19\\d\\d-\\d\\d-\\d\\d"));
    ((Button) dlg.findViewById(R.id.dialog_file_information_button_ok)).performClick();
    assertNull(shownDialog(FileInformationAction.FileInformationDialog.class));
  }

  // ---- CreateNewPlaylistMenuAction

  @Test
  public void createPlaylistDialogCreatesAndFillsAFreeNamedPlaylist() {
    long[] songs = {1, 2};
    music.when(() -> MusicUtils.getIdForPlaylist(any(), anyString())).thenReturn(-1L);
    music.when(() -> MusicUtils.createPlaylist(any(), anyString())).thenReturn(7L);
    new CreateNewPlaylistMenuAction(activity, songs).onClick();
    CreateNewPlaylistMenuAction.CreateNewPlaylistDialog dialog =
        shownDialog(CreateNewPlaylistMenuAction.CreateNewPlaylistDialog.class);
    assertNotNull(dialog);
    Dialog dlg = dialogOf(dialog);
    ((EditText) dlg.findViewById(R.id.dialog_default_input_text)).setText("  Road Trip  ");
    ((Button) dlg.findViewById(R.id.dialog_default_input_button_yes)).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    music.verify(() -> MusicUtils.createPlaylist(any(), eq("Road Trip")));
    music.verify(() -> MusicUtils.addToPlaylist(any(), eq(songs), eq(7L)));
    music.verify(MusicUtils::refresh);
    assertNull(shownDialog(CreateNewPlaylistMenuAction.CreateNewPlaylistDialog.class));
  }

  @Test
  public void createPlaylistDialogSuggestsANumberedNameWhenTheNameIsTaken() {
    music
        .when(() -> MusicUtils.getIdForPlaylist(any(), anyString()))
        .thenAnswer(i -> "Road Trip".equals(i.getArgument(1)) ? 3L : -1L);
    new CreateNewPlaylistMenuAction(activity, new long[0]).onClick();
    Dialog dlg = dialogOf(shownDialog(CreateNewPlaylistMenuAction.CreateNewPlaylistDialog.class));
    EditText input = dlg.findViewById(R.id.dialog_default_input_text);
    input.setText("Road Trip");
    ((Button) dlg.findViewById(R.id.dialog_default_input_button_yes)).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals("Road Trip 2", input.getText().toString());
    music.verify(() -> MusicUtils.createPlaylist(any(), anyString()), never());
    assertNotNull(shownDialog(CreateNewPlaylistMenuAction.CreateNewPlaylistDialog.class));
  }

  @Test
  public void createPlaylistDialogIgnoresABlankNameAndCancelCloses() {
    new CreateNewPlaylistMenuAction(activity, new long[0]).onClick();
    Dialog dlg = dialogOf(shownDialog(CreateNewPlaylistMenuAction.CreateNewPlaylistDialog.class));
    ((EditText) dlg.findViewById(R.id.dialog_default_input_text)).setText("   ");
    ((Button) dlg.findViewById(R.id.dialog_default_input_button_yes)).performClick();
    music.verify(() -> MusicUtils.createPlaylist(any(), anyString()), never());
    ((Button) dlg.findViewById(R.id.dialog_default_input_button_no)).performClick();
    assertNull(shownDialog(CreateNewPlaylistMenuAction.CreateNewPlaylistDialog.class));
  }

  // ---- AddToPlaylistMenuAction

  @Test
  public void addToPlaylistOffersNewPlaylistThenEveryExistingOne() {
    music
        .when(() -> MusicUtils.getPlaylists(any()))
        .thenReturn(Arrays.asList(new Playlist(1, "Rock"), new Playlist(2, "Jazz")));
    new AddToPlaylistMenuAction(activity, new long[] {9}).onClick();
    shadowOf(Looper.getMainLooper()).idle();
    android.app.AlertDialog menu = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
    assertNotNull(menu);
    MenuAdapter adapter = (MenuAdapter) menu.getListView().getAdapter();
    assertEquals(3, adapter.getCount());
    assertTrue(adapter.getItem(0) instanceof CreateNewPlaylistMenuAction);
    assertEquals("Rock", adapter.getItem(1).getText());
    assertEquals("Jazz", adapter.getItem(2).getText());
  }

  @Test
  public void addToPlaylistFromFileDescriptorsUsesTheirIds() {
    FWFileDescriptor a = fileDescriptor("/a.mp3");
    a.id = 11;
    FWFileDescriptor b = fileDescriptor("/b.mp3");
    b.id = 12;
    music
        .when(() -> MusicUtils.getPlaylists(any()))
        .thenReturn(Arrays.asList(new Playlist(5, "Mix")));
    new AddToPlaylistMenuAction(activity, Arrays.asList(a, b)).onClick();
    shadowOf(Looper.getMainLooper()).idle();
    android.app.AlertDialog menu = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
    MenuAdapter adapter = (MenuAdapter) menu.getListView().getAdapter();
    adapter.getItem(1).onClick();
    music.verify(() -> MusicUtils.addToPlaylist(any(), eq(new long[] {11, 12}), eq(5L)));
    assertFalse(activity.isFinishing());
  }
}
