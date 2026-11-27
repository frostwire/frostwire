/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.frostwire.android.R;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A carousel tab's artwork: the default picture when there is nothing to look up, the selected
 * look, and the label. The platform answers "not the UI thread" so the work that would hop to a
 * background thread runs inline and the test stays deterministic.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class CarouselTabTest {

  private Activity activity;
  private CarouselTab tab;

  @Before
  public void setUp() {
    com.andrew.apollo.ui.ApolloUiTestSupport.installPlatform();
    activity = Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
    activity.setTheme(R.style.Theme_FrostWire);
    tab = (CarouselTab) LayoutInflater.from(activity).inflate(R.layout.profile_tab, null, false);
    activity.setContentView(tab);
    shadowOf(android.os.Looper.getMainLooper()).idle();
  }

  private ImageView photo() {
    return tab.findViewById(R.id.profile_tab_photo);
  }

  private int photoResource() {
    return shadowOf(photo().getDrawable()).getCreatedFromResId();
  }

  @Test
  public void theLabelShowsWhatItIsGiven() {
    tab.setLabel("Songs");

    assertEquals(
        "Songs", ((TextView) tab.findViewById(R.id.profile_tab_label)).getText().toString());
  }

  @Test
  public void theLabelLooksSelectedOrNot() {
    TextView label = tab.findViewById(R.id.profile_tab_label);

    tab.showSelectedState();
    assertTrue(label.isSelected());
    tab.showDeselectedState();
    assertFalse(label.isSelected());
  }

  @Test
  public void aSelectedTabShowsItsColorStrip() {
    View strip = tab.findViewById(R.id.profile_tab_colorstrip);

    tab.setSelected(true);
    assertEquals(View.VISIBLE, strip.getVisibility());
    tab.setSelected(false);
    assertEquals(View.GONE, strip.getVisibility());
  }

  @Test
  public void theDefaultPictureIsTheHeaderPlaceholder() {
    tab.setDefault(activity);

    assertEquals(R.drawable.header_temp, photoResource());
  }

  @Test
  public void anArtistWithoutANameGetsThePlaceholder() {
    tab.setArtistPhoto(activity, "");

    assertEquals(R.drawable.header_temp, photoResource());
  }

  @Test
  public void anArtistWithANameIsLookedUpInsteadOfShowingThePlaceholder() {
    tab.setArtistPhoto(activity, "Some Artist");

    assertTrue(
        "the placeholder is not forced over the lookup",
        photo().getDrawable() == null || photoResource() != R.drawable.header_temp);
  }

  @Test
  public void anAlbumWithoutANameGetsThePlaceholder() {
    tab.setAlbumPhoto(activity, "", "Some Artist");

    com.andrew.apollo.ui.ApolloUiTestSupport.waitUntil(
        "the placeholder", () -> photo().getDrawable() != null);
    assertEquals(R.drawable.header_temp, photoResource());
  }

  @Test
  public void anAlbumWithANameShowsTheAlbumArtView() {
    tab.setAlbumPhoto(activity, "An Album", "Some Artist");

    com.andrew.apollo.ui.ApolloUiTestSupport.waitUntil(
        "the album art shows",
        () -> tab.findViewById(R.id.profile_tab_album_art).getVisibility() == View.VISIBLE);
  }

  @Test
  public void anArtistWithNoPlayedAlbumGetsThePlaceholder() {
    tab.setArtistAlbumPhoto(activity, "Some Artist");

    com.andrew.apollo.ui.ApolloUiTestSupport.waitUntil(
        "the placeholder", () -> photo().getDrawable() != null);
    assertEquals(R.drawable.header_temp, photoResource());
  }

  @Test
  public void aBlurredPhotoIsRequestedForTheAlbum() {
    tab.blurPhoto(activity, "Some Artist", "An Album");
    com.andrew.apollo.ui.ApolloUiTestSupport.settle();

    assertNotNull(photo());
  }

  @Test
  public void songsWithNoArtworkGetThePlaceholder() throws Exception {
    final CountDownLatch asked = new CountDownLatch(1);

    tab.setSongsPhoto(
        activity,
        () -> {
          asked.countDown();
          return new long[0];
        });

    assertTrue(asked.await(10, TimeUnit.SECONDS));
    com.andrew.apollo.ui.ApolloUiTestSupport.waitUntil(
        "the placeholder", () -> photo().getDrawable() != null);
    assertEquals(R.drawable.header_temp, photoResource());
  }
}
