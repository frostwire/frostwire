/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.menu;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import androidx.fragment.app.FragmentActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;

/**
 * Deleting songs removed their rows as soon as the user tapped Delete, before Android's own "Allow
 * FrostWire to delete?" dialog. Cancelling that dialog deleted nothing but the rows stayed gone,
 * and the MediaStore query that builds the request ran on the main thread (GitHub #1450).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DeleteDialogConfirmationTest {

  private static final long[] SONGS = {11L, 12L};

  /** A MediaStore stand-in: answers the track query and hands out a delete request. */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<Thread> queryThreads = new CopyOnWriteArrayList<>();

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      queryThreads.add(Thread.currentThread());
      MatrixCursor cursor = new MatrixCursor(projection);
      if (selection != null && selection.startsWith(MediaStore.Audio.Media._ID + " IN")) {
        for (long id : SONGS) {
          Object[] row = new Object[projection.length];
          for (int i = 0; i < projection.length; i++) {
            row[i] =
                MediaStore.Audio.Media._ID.equals(projection[i]) ? id : "/music/" + id + ".mp3";
          }
          cursor.addRow(row);
        }
      }
      return cursor;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
      Bundle result = new Bundle();
      result.putParcelable(
          "result",
          PendingIntent.getActivity(
              getContext(), 0, new Intent("delete-request"), PendingIntent.FLAG_IMMUTABLE));
      return result;
    }

    @Override
    public String getType(Uri uri) {
      return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
      return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
      return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
      return 0;
    }
  }

  private final List<long[]> confirmed = new ArrayList<>();
  private ActivityController<FragmentActivity> controller;

  @Before
  public void setUp() {
    FakeMediaProvider.queryThreads.clear();
    Robolectric.setupContentProvider(FakeMediaProvider.class, MediaStore.AUTHORITY);
    controller = Robolectric.buildActivity(FragmentActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
  }

  /** Shows the dialog, taps Delete and returns Android's delete request once it is launched. */
  private ShadowActivity.IntentSenderRequest confirmInTheDialog() throws InterruptedException {
    DeleteDialog.newInstance("Songs", SONGS, null)
        .setOnDeleteConfirmedCallback(confirmed::add)
        .show(controller.get().getSupportFragmentManager(), "DeleteDialog");
    ShadowLooper.idleMainLooper();
    DeleteDialog dialog =
        (DeleteDialog)
            controller.get().getSupportFragmentManager().findFragmentByTag("DeleteDialog");
    assertNotNull(dialog);
    Button delete = dialog.getDialog().findViewById(R.id.dialog_default_button_yes);
    delete.performClick();

    ShadowActivity shadow = shadowOf((Activity) controller.get());
    long deadline = System.currentTimeMillis() + 5_000;
    while (shadow.getLastIntentSenderRequest() == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
      ShadowLooper.idleMainLooper();
    }
    ShadowActivity.IntentSenderRequest request = shadow.getLastIntentSenderRequest();
    assertNotNull("Android's delete request is launched", request);
    return request;
  }

  @Test
  public void cancellingAndroidsDeleteRequestKeepsTheRows() throws Exception {
    ShadowActivity.IntentSenderRequest request = confirmInTheDialog();
    assertEquals("no row is removed before Android's dialog is answered", 0, confirmed.size());

    MusicUtils.handleDeleteTracksActivityResult(
        controller.get(), request.requestCode, Activity.RESULT_CANCELED);
    ShadowLooper.idleMainLooper();

    assertEquals("a cancelled delete keeps every row", 0, confirmed.size());
  }

  @Test
  public void allowingAndroidsDeleteRequestRemovesTheRows() throws Exception {
    ShadowActivity.IntentSenderRequest request = confirmInTheDialog();

    MusicUtils.handleDeleteTracksActivityResult(
        controller.get(), request.requestCode, Activity.RESULT_OK);
    ShadowLooper.idleMainLooper();

    assertEquals(1, confirmed.size());
    assertArrayEquals(SONGS, confirmed.get(0));
  }

  @Test
  public void theTracksAreLookedUpOffTheMainThread() throws Exception {
    confirmInTheDialog();
    assertTrue(FakeMediaProvider.queryThreads.size() > 0);
    for (Thread thread : FakeMediaProvider.queryThreads) {
      assertNotSame("MediaStore is queried off the main thread", Thread.currentThread(), thread);
    }
  }
}
