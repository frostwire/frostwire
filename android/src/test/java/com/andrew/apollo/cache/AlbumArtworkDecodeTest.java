/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.cache;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.FakeMediaStore;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Album art comes from MediaStore's art file, or from the song file's own picture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AlbumArtworkDecodeTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    FakeMediaStore.install();
  }

  private File png(String name, int size) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.RED);
    File file = folder.newFile(name);
    try (FileOutputStream out = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    return file;
  }

  @Test
  public void anAlbumsArtFileFromMediaStoreIsDecoded() throws Exception {
    FakeMediaStore.openableFile = png("art.png", 40);

    Bitmap artwork = AlbumArtwork.decode(context, 5);

    assertNotNull("the descriptor MediaStore handed out was decoded", artwork);
  }

  @Test
  public void withoutAnArtFileTheSongFilesSiblingArtFileIsUsed() throws Exception {
    File song = folder.newFile("song.mp3");
    File art = png("song.art", 24);
    assertNotNull(art);
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_data", song.getAbsolutePath()));

    Bitmap artwork = AlbumArtwork.decode(context, 5);

    assertNotNull("the .art next to the song", artwork);
  }

  @Test
  public void anAlbumWithNoArtAnywhereHasNoArtwork() throws Exception {
    File song = folder.newFile("plain.mp3");
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_data", song.getAbsolutePath()));

    assertNull(AlbumArtwork.decode(context, 5));
  }

  @Test
  public void anAlbumMediaStoreKnowsNoSongOfHasNoArtwork() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    assertNull(AlbumArtwork.decode(context, 5));
  }

  @Test
  public void aFailingStoreMeansNoArtworkRatherThanACrash() {
    FakeMediaStore.failure = new IllegalStateException("down");

    assertNull(AlbumArtwork.decode(context, 5));
  }
}
