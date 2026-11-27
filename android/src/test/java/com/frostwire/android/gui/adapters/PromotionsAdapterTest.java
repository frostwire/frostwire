/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.frostclick.Slide;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The featured-downloads grid adapter, with ads switched off and a fake image loader. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PromotionsAdapterTest {

  private Activity activity;
  private FWImageLoader imageLoader;
  private MockedStatic<FWImageLoader> loaders;
  private MockedStatic<Offers> offers;
  private MockedStatic<UIUtils> ui;
  private GridView grid;

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
    activity.setTheme(R.style.Theme_FrostWire);
    imageLoader = mock(FWImageLoader.class);
    loaders = mockStatic(FWImageLoader.class);
    loaders.when(() -> FWImageLoader.getInstance(any())).thenReturn(imageLoader);
    offers = mockStatic(Offers.class);
    offers.when(Offers::disabledAds).thenReturn(true);
    ui = mockStatic(UIUtils.class);
    grid = mock(GridView.class);
    when(grid.getColumnWidth()).thenReturn(530);
  }

  @After
  public void tearDown() {
    loaders.close();
    offers.close();
    ui.close();
  }

  private static Slide slide(String image, String video, String click) {
    Slide s = new Slide();
    s.imageSrc = image;
    s.videoURL = video;
    s.clickURL = click;
    return s;
  }

  @Test
  public void countsSlidesPlusTheAllFeaturesButtonAndABannerOnlyWhenAdsAreOn() {
    List<Slide> slides = Arrays.asList(slide("a", null, null), slide("b", null, null));
    PromotionsAdapter adapter = new PromotionsAdapter(activity, slides, s -> {});
    assertEquals(3, adapter.getCount());
    assertEquals(1, new PromotionsAdapter(activity, null, s -> {}).getCount());
    offers.when(Offers::disabledAds).thenReturn(false);
    assertEquals(4, adapter.getCount());
    assertSame(slides.get(1), adapter.getItem(1));
  }

  @Test
  public void gridPositionsMapToSlidesThenTheFeaturesButtonThenAnInvisiblePlaceholder() {
    List<Slide> slides = Collections.singletonList(slide("a", null, null));
    PromotionsAdapter adapter = new PromotionsAdapter(activity, slides, s -> {});
    FrameLayout parent = new FrameLayout(activity);
    // position 0 is the only slide: inflated row with the promotion image
    View slideRow = adapter.getView(0, null, grid);
    assertNotNull(slideRow.findViewById(R.id.view_promotions_item_image));
    // position 1 is the all-features button row
    View all = adapter.getView(1, null, parent);
    assertNull(all.findViewById(R.id.view_promotions_item_image));
    // anything past it is the invisible filler
    View filler = adapter.getView(2, null, parent);
    assertNotNull(filler);
    assertNull(filler.findViewById(R.id.view_promotions_item_image));
  }

  @Test
  public void nullSlideLeavesTheRowUntouched() {
    PromotionsAdapter adapter = new PromotionsAdapter(activity, Collections.emptyList(), s -> {});
    View row = View.inflate(activity, R.layout.view_promotions_item, null);
    adapter.setupView(row, grid, null);
    assertNull(
        row.findViewById(R.id.view_promotions_item_image).getTag(R.id.view_promotions_item_image));
  }

  @Test
  public void setupViewLoadsTheImageSizedToTheColumnAfterTheDelayAndWiresClicks() {
    Slide s =
        slide("https://example.com/a.jpg", "https://example.com/v.mp4", "https://example.com/more");
    final Slide[] downloaded = new Slide[1];
    PromotionsAdapter adapter =
        new PromotionsAdapter(activity, Collections.singletonList(s), sl -> downloaded[0] = sl);
    View row = adapter.getView(0, null, grid);
    activity.addContentView(row, new android.view.ViewGroup.LayoutParams(530, 300));

    ImageView image = row.findViewById(R.id.view_promotions_item_image);
    assertEquals("https://example.com/a.jpg", image.getTag(R.id.view_promotions_item_image));
    verify(imageLoader, never())
        .load(any(Uri.class), any(ImageView.class), anyInt(), anyInt(), anyInt());
    shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150));
    verify(imageLoader)
        .load(
            Uri.parse("https://example.com/a.jpg"),
            image,
            530,
            (int) (530 * 0.52998),
            R.drawable.default_artwork);

    image.performClick();
    assertSame(s, downloaded[0]);
    downloaded[0] = null;
    row.findViewById(R.id.view_promotions_item_download_textview).performClick();
    assertSame(s, downloaded[0]);

    ImageView preview = row.findViewById(R.id.view_promotions_item_preview_imageview);
    ImageView readMore = row.findViewById(R.id.view_promotions_item_readmore_imageview);
    assertEquals(View.VISIBLE, preview.getVisibility());
    assertEquals(View.VISIBLE, readMore.getVisibility());
    readMore.performClick();
    ui.verify(() -> UIUtils.openURL(activity, "https://example.com/more"));
  }

  @Test
  public void rebindingTheSameImageDoesNotReloadItAndRowsWithoutLinksHideTheirButtons() {
    Slide s = slide("https://example.com/a.jpg", "", null);
    PromotionsAdapter adapter =
        new PromotionsAdapter(activity, Collections.singletonList(s), sl -> {});
    View row = adapter.getView(0, null, grid);
    activity.addContentView(row, new android.view.ViewGroup.LayoutParams(530, 300));
    shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150));
    // bind the same row again for the same image url: no second load is scheduled
    adapter.setupView(row, grid, s);
    shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150));
    verify(imageLoader, org.mockito.Mockito.times(1))
        .load(any(Uri.class), any(ImageView.class), anyInt(), anyInt(), anyInt());
    assertEquals(
        View.GONE, row.findViewById(R.id.view_promotions_item_preview_imageview).getVisibility());
    assertEquals(
        View.GONE, row.findViewById(R.id.view_promotions_item_readmore_imageview).getVisibility());
  }

  @Test
  public void imageIsNotLoadedWhenTheColumnHasNoWidthYet() {
    when(grid.getColumnWidth()).thenReturn(0);
    Slide s = slide("https://example.com/a.jpg", null, null);
    PromotionsAdapter adapter =
        new PromotionsAdapter(activity, Collections.singletonList(s), sl -> {});
    adapter.getView(0, null, grid);
    shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150));
    verify(imageLoader, never())
        .load(any(Uri.class), any(ImageView.class), anyInt(), anyInt(), anyInt());
  }

  @Test
  public void allFeaturedDownloadsLinkCarriesTheSourceScreen() {
    PromotionsAdapter adapter = new PromotionsAdapter(activity, null, s -> {});
    adapter.onAllFeaturedDownloadsClick("android");
    ui.verify(
        () -> UIUtils.openURL(activity, Constants.ALL_FEATURED_DOWNLOADS_URL + "?from=android"));
  }

  @Test
  public void destroyingTheViewBeforeAnyBannerWasCreatedIsSafe() {
    PromotionsAdapter adapter = new PromotionsAdapter(activity, null, s -> {});
    adapter.onDestroyView();
    assertEquals(1, adapter.getCount());
  }
}
