/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.app.Dialog;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.dialogs.SoftwareUpdaterDialog;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.DialogTestHost;
import com.frostwire.android.gui.views.ViewTestSupport;
import com.frostwire.search.FileSearchResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Lives outside the dialogs test package because build.gradle excludes that whole package (its old
 * tests boot MainApplication); these use a plain Application.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SimpleDialogsTest {

  private DialogTestHost host;
  private MockedStatic<UIUtils> ui;
  private final List<String> clicks = new ArrayList<>();

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    host = new DialogTestHost();
    ui = Mockito.mockStatic(UIUtils.class, Mockito.CALLS_REAL_METHODS);
    ui.when(() -> UIUtils.openURL(any(), any())).then(invocation -> null);
  }

  @After
  public void tearDown() {
    ui.close();
  }

  private static FileSearchResult result(String name, long size) {
    FileSearchResult sr = mock(FileSearchResult.class);
    when(sr.getDisplayName()).thenReturn(name);
    when(sr.getSize()).thenReturn(size);
    return sr;
  }

  // ---- NewTransferDialog

  private Dialog showNewTransfer(FileSearchResult sr, boolean hideCheck) {
    NewTransferDialog dialog =
        NewTransferDialog.newInstance(sr, hideCheck, (tag, which) -> clicks.add(tag + ":" + which));
    return host.show(dialog, "n");
  }

  @Test
  public void newTransferDialogAsksAboutTheNamedFileWithItsSize() {
    ViewTestSupport.configuration();
    Dialog dialog = showNewTransfer(result("song.mp3", 2048), false);
    String text =
        ((TextView) dialog.findViewById(R.id.dialog_default_checkbox_text)).getText().toString();
    assertTrue(text, text.contains("song.mp3"));
    assertTrue(text, text.contains(UIUtils.getBytesInHuman(2048)));
    assertEquals(
        android.view.View.VISIBLE,
        dialog.findViewById(R.id.dialog_default_checkbox_show).getVisibility());
  }

  @Test
  public void newTransferDialogSaysSizeUnknownForUnknownSizes() {
    ViewTestSupport.configuration();
    Dialog dialog = showNewTransfer(result("mystery.bin", 0), false);
    String text =
        ((TextView) dialog.findViewById(R.id.dialog_default_checkbox_text)).getText().toString();
    assertTrue(text, text.contains(host.activity.getString(R.string.size_unknown)));
  }

  @Test
  public void newTransferDialogCanHideTheDontAskAgainCheckbox() {
    ViewTestSupport.configuration();
    Dialog dialog = showNewTransfer(result("a", 1), true);
    assertEquals(
        android.view.View.GONE,
        dialog.findViewById(R.id.dialog_default_checkbox_show).getVisibility());
  }

  @Test
  public void newTransferYesReportsTheClickAndDismisses() {
    ViewTestSupport.configuration();
    Dialog dialog = showNewTransfer(result("a", 1), false);
    ((Button) dialog.findViewById(R.id.dialog_default_checkbox_button_yes)).performClick();
    assertEquals(List.of(NewTransferDialog.TAG + ":" + Dialog.BUTTON_POSITIVE), clicks);
    assertFalse(dialog.isShowing());
  }

  @Test
  public void newTransferNoOnlyDismisses() {
    ViewTestSupport.configuration();
    Dialog dialog = showNewTransfer(result("a", 1), false);
    ((Button) dialog.findViewById(R.id.dialog_default_checkbox_button_no)).performClick();
    assertTrue(clicks.isEmpty());
    assertFalse(dialog.isShowing());
  }

  @Test
  public void newTransferCheckboxPersistsTheShowAgainPreference() {
    com.frostwire.android.core.ConfigurationManager config = ViewTestSupport.configuration();
    config.setBoolean(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG, true);
    Dialog dialog = showNewTransfer(result("a", 1), false);
    CheckBox show = dialog.findViewById(R.id.dialog_default_checkbox_show);
    assertTrue(show.isChecked());
    show.setChecked(false);
    assertFalse(config.getBoolean(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG));
    show.setChecked(true);
    assertTrue(config.getBoolean(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG));
  }

  @Test
  public void newTransferKeepsAWeakReferenceToTheResult() {
    FileSearchResult sr = result("a", 1);
    NewTransferDialog.newInstance(sr, false, null);
    assertEquals(sr, NewTransferDialog.srRef.get());
  }

  // ---- SoftwareUpdaterDialog

  private Dialog showUpdater(String url, List<String> changelog) {
    Map<String, String> messages = new HashMap<>();
    messages.put("en", "A new version is out");
    return host.show(SoftwareUpdaterDialog.newInstance(url, messages, changelog), "u");
  }

  @Test
  public void updaterDialogShowsTheMessageAndTheChangelogBullets() {
    Dialog dialog = showUpdater("https://example.com/f.apk", Arrays.asList("Fixed A", "Added B"));
    assertEquals(
        "A new version is out",
        ((TextView) dialog.findViewById(R.id.dialog_default_update_text)).getText().toString());
    assertEquals(
        host.activity.getString(R.string.update_title),
        ((TextView) dialog.findViewById(R.id.dialog_default_update_title)).getText().toString());
    ListView list = dialog.findViewById(R.id.dialog_default_update_list_view);
    assertEquals(2, list.getAdapter().getCount());
    assertEquals("• Fixed A", list.getAdapter().getItem(0).toString());
  }

  @Test
  public void updaterYesOpensTheApkUrlAndDismisses() {
    Dialog dialog = showUpdater("https://example.com/f.apk", Arrays.asList("x"));
    ((Button) dialog.findViewById(R.id.dialog_default_update_button_yes)).performClick();
    ui.verify(() -> UIUtils.openURL(any(), eq("https://example.com/f.apk")));
    assertFalse(dialog.isShowing());
  }

  @Test
  public void updaterYesWithoutAnApkUrlOpensTheStorePage() {
    Dialog dialog = showUpdater(null, Arrays.asList("x"));
    ((Button) dialog.findViewById(R.id.dialog_default_update_button_yes)).performClick();
    ui.verify(
        () ->
            UIUtils.openURL(
                any(),
                eq(
                    Constants.IS_GOOGLE_PLAY_DISTRIBUTION
                        ? Constants.FROSTWIRE_ANDROID_GOOGLE_PLAY_URL
                        : Constants.FROSTWIRE_ANDROID_DOWNLOAD_PAGE_URL)));
    assertFalse(dialog.isShowing());
  }

  @Test
  public void updaterCancelJustDismisses() {
    Dialog dialog = showUpdater("https://example.com/f.apk", Arrays.asList("x"));
    ((Button) dialog.findViewById(R.id.dialog_default_update_button_no)).performClick();
    ui.verify(() -> UIUtils.openURL(any(), any()), never());
    assertFalse(dialog.isShowing());
  }

  // ---- HandpickedTorrentDownloadDialogOnFetch

  @Test
  public void fetchedTorrentWithoutDataShowsNothing() {
    AppCompatActivity activity = mock(AppCompatActivity.class);
    FragmentManager fragments = mock(FragmentManager.class);
    when(activity.getSupportFragmentManager()).thenReturn(fragments);
    HandpickedTorrentDownloadDialogOnFetch listener =
        new HandpickedTorrentDownloadDialogOnFetch(activity, true);

    listener.onTorrentInfoFetched(null, "magnet", 1);
    listener.onTorrentInfoFetched(new byte[0], "magnet", 1);

    verifyNoInteractions(fragments);
  }

  @Test
  public void fetchedTorrentIsNotShownAgainWhileItsDialogIsUp() {
    AppCompatActivity activity = mock(AppCompatActivity.class);
    FragmentManager fragments = mock(FragmentManager.class);
    when(activity.getSupportFragmentManager()).thenReturn(fragments);
    when(fragments.findFragmentByTag("HANDPICKED_TORRENT_DOWNLOAD_DIALOG"))
        .thenReturn(mock(androidx.fragment.app.Fragment.class));
    HandpickedTorrentDownloadDialogOnFetch listener =
        new HandpickedTorrentDownloadDialogOnFetch(activity, false);

    listener.onTorrentInfoFetched(new byte[] {1, 2, 3}, "magnet", 1);

    verify(fragments).findFragmentByTag("HANDPICKED_TORRENT_DOWNLOAD_DIALOG");
    verify(fragments, never()).beginTransaction();
  }

  @Test
  public void fetchedTorrentThatCannotBeDecodedIsSwallowed() {
    AppCompatActivity activity = mock(AppCompatActivity.class);
    FragmentManager fragments = mock(FragmentManager.class);
    when(activity.getSupportFragmentManager()).thenReturn(fragments);
    HandpickedTorrentDownloadDialogOnFetch listener =
        new HandpickedTorrentDownloadDialogOnFetch(activity, false);

    // bdecode of garbage (or the missing native library) must not escape to the fetcher thread
    listener.onTorrentInfoFetched(new byte[] {1, 2, 3}, "magnet", 1);

    verify(fragments, never()).beginTransaction();
  }
}
