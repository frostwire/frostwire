/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.menu;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractActivity;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Renaming a playlist: the dialog starts with the playlist's name and saves the new one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class RenamePlaylistDialogTest {

  /** The screen the playlist dialogs are shown from. */
  public static final class Host extends AbstractActivity {
    public Host() {
      super(android.R.layout.simple_list_item_1);
    }
  }

  private ActivityController<Host> controller;
  private FragmentManager fragments;

  @Before
  public void setUp() throws Exception {
    ApolloServiceFixture.installPlatform(true);
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    FakeConfig.install();
    // The dialog reads the playlist's name through the app context SystemUtils hands out.
    setAppContext(ApplicationProvider.getApplicationContext());
    controller = Robolectric.buildActivity(Host.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    fragments = controller.get().getSupportFragmentManager();
  }

  @After
  public void tearDown() throws Exception {
    setAppContext(null);
    FakeConfig.uninstall();
  }

  private static void setAppContext(android.content.Context context) throws Exception {
    java.lang.reflect.Field field =
        com.frostwire.android.gui.MainApplication.class.getDeclaredField("appContext");
    field.setAccessible(true);
    field.set(null, context);
  }

  /** The dialog loads its name on a background thread and shows it from the main looper. */
  private static void waitFor(BooleanSupplier condition) {
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.onSpinWait();
    }
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue("timed out waiting", condition.getAsBoolean());
  }

  private RenamePlaylist show(long id) {
    RenamePlaylist dialog = RenamePlaylist.getInstance(id);
    dialog.show(fragments, "rename");
    fragments.executePendingTransactions();
    shadowOf(Looper.getMainLooper()).idle();
    return dialog;
  }

  private static EditText nameField(RenamePlaylist dialog) {
    return dialog.getDialog().findViewById(R.id.dialog_default_input_text);
  }

  @Test
  public void theDialogStartsWithTheNameOfThePlaylistBeingRenamed() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("name", "Road Trip"));

    RenamePlaylist dialog = show(5);

    waitFor(() -> "Road Trip".equals(nameField(dialog).getText().toString()));
    assertEquals(9, nameField(dialog).getSelectionEnd());
    assertNotNull(fragments.findFragmentByTag("rename"));
  }

  @Test
  public void savingRenamesThePlaylistWithACapitalizedNameAndClosesTheDialog() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("name", "Road Trip"));
    RenamePlaylist dialog = show(5);
    waitFor(() -> "Road Trip".equals(nameField(dialog).getText().toString()));

    nameField(dialog).setText("gym mix");
    Button save = dialog.getDialog().findViewById(R.id.dialog_default_input_button_yes);
    save.performClick();

    waitFor(() -> !FakeMediaStore.updates.isEmpty());
    assertTrue(FakeMediaStore.updates.get(0).contains("/5"));
    assertEquals("Gym Mix", FakeMediaStore.updatedValues.get(0).getAsString("name"));
    assertFalse("the dialog is closed", dialog.isAdded());
  }

  @Test
  public void anEmptyNameIsNotSaved() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("name", "Road Trip"));
    RenamePlaylist dialog = show(5);
    waitFor(() -> "Road Trip".equals(nameField(dialog).getText().toString()));

    nameField(dialog).setText("");
    dialog.onSaveClick();
    shadowOf(Looper.getMainLooper()).idle();

    assertTrue(FakeMediaStore.updates.isEmpty());
  }

  @Test
  public void cancellingClosesTheDialogWithoutRenaming() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("name", "Road Trip"));
    RenamePlaylist dialog = show(5);
    waitFor(() -> "Road Trip".equals(nameField(dialog).getText().toString()));

    Button cancel = dialog.getDialog().findViewById(R.id.dialog_default_input_button_no);
    cancel.performClick();
    shadowOf(Looper.getMainLooper()).idle();

    assertFalse(dialog.isAdded());
    assertTrue(FakeMediaStore.updates.isEmpty());
  }

  @Test
  public void aPlaylistThatNoLongerExistsClosesTheDialog() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    RenamePlaylist dialog = show(5);

    waitFor(() -> !dialog.isAdded());
  }

  @Test
  public void aFailingStoreStillLetsTheRenameFinishAndCloseTheDialog() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("name", "Road Trip"));
    RenamePlaylist dialog = show(5);
    waitFor(() -> "Road Trip".equals(nameField(dialog).getText().toString()));
    FakeMediaStore.failure = new IllegalStateException("down");

    dialog.onSaveClick();

    waitFor(() -> !dialog.isAdded());
  }

  @Test
  public void theNameAndIdSurviveARecreation() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("name", "Road Trip"));
    RenamePlaylist dialog = show(5);
    waitFor(() -> "Road Trip".equals(nameField(dialog).getText().toString()));
    nameField(dialog).setText("Typed");

    Bundle saved = new Bundle();
    dialog.onSaveInstanceState(saved);

    assertEquals("Typed", saved.getString("defaultname"));
    assertEquals(5, saved.getLong("rename"));
    dialog.initObjects(saved);
    assertTrue("a valid saved state keeps the dialog", dialog.isAdded());
    Bundle broken = new Bundle();
    broken.putLong("rename", -1);
    broken.putString("defaultname", "x");
    dialog.initObjects(broken);
    shadowOf(Looper.getMainLooper()).idle();
    assertFalse("an invalid one closes it", dialog.isAdded());
  }
}
