/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.os.Bundle;
import com.andrew.apollo.Config;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Opening a user playlist showed it empty: its id was read in onActivityCreated, after the loader
 * had been created with id 0 (GitHub #1447). The loader now gets the id from its arguments.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class PlaylistSongFragmentPlaylistIdTest {

  private static Bundle withId(long id) {
    Bundle bundle = new Bundle();
    bundle.putLong(Config.ID, id);
    return bundle;
  }

  @Test
  public void theLoaderGetsThePlaylistFromItsArguments() {
    assertEquals(5L, PlaylistSongFragment.playlistIdFrom(withId(5), null));
    assertEquals(5L, PlaylistSongFragment.playlistIdFrom(withId(5), withId(9)));
  }

  @Test
  public void withoutLoaderArgumentsTheFragmentsArgumentsGiveIt() {
    assertEquals(9L, PlaylistSongFragment.playlistIdFrom(null, withId(9)));
    assertEquals(9L, PlaylistSongFragment.playlistIdFrom(new Bundle(), withId(9)));
    assertEquals(0L, PlaylistSongFragment.playlistIdFrom(null, null));
  }

  @Test
  public void theIdIsNoLongerSetAfterTheLoaderExists() throws Exception {
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve(
                            "apollo/src/com/andrew/apollo/ui/fragments/profile/PlaylistSongFragment.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertFalse(source.contains("publicvoidonActivityCreated("));
  }
}
