/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.GridView;
import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.PromotionsAdapter;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.frostclick.Slide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PromotionsViewTest {

  private Context context;
  private PromotionsView view;
  private MockedStatic<Offers> offers;
  private MockedStatic<FWImageLoader> images;
  private MockedStatic<UIUtils> ui;
  private final List<Slide> downloaded = new ArrayList<>();

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    context = ViewTestSupport.themedContext();
    offers = mockStatic(Offers.class);
    offers.when(Offers::disabledAds).thenReturn(true);
    images = mockStatic(FWImageLoader.class);
    images.when(() -> FWImageLoader.getInstance(any())).thenReturn(mock(FWImageLoader.class));
    ui = mockStatic(UIUtils.class);
    view = new PromotionsView(context, null);
    view.onFinishInflate();
    view.setPromotionDownloader(downloaded::add);
  }

  @After
  public void tearDown() {
    ui.close();
    images.close();
    offers.close();
  }

  private static Slide slide(String title, boolean advertisement) {
    Slide s = new Slide();
    s.title = title;
    s.imageSrc = "https://example.com/" + title + ".png";
    s.flags = advertisement ? Slide.IS_ADVERTISEMENT : 0;
    return s;
  }

  private GridView grid() {
    return view.findViewById(R.id.view_promotions_gridview);
  }

  @Test
  public void slidesGoToTheGridAdapterAndAdsAreStrippedWhenDisabled() {
    List<Slide> slides =
        new ArrayList<>(Arrays.asList(slide("a", false), slide("ad", true), slide("b", false)));
    view.setSlides(slides);

    assertSame(slides, view.getSlides());
    assertEquals(2, slides.size());
    PromotionsAdapter adapter = (PromotionsAdapter) grid().getAdapter();
    // two slides and the "all free downloads" button, no banner while ads are disabled
    assertEquals(3, adapter.getCount());
  }

  @Test
  public void adsStayAndABannerSlotIsAddedWhenAdsAreEnabled() {
    offers.when(Offers::disabledAds).thenReturn(false);
    List<Slide> slides = new ArrayList<>(Arrays.asList(slide("a", false), slide("ad", true)));
    view.setSlides(slides);

    assertEquals(2, slides.size());
    assertEquals(4, ((PromotionsAdapter) grid().getAdapter()).getCount());
  }

  @Test
  public void nullSlidesAreIgnored() {
    view.setSlides(null);
    assertNull(view.getSlides());
    assertNull(grid().getAdapter());
    view.updateAdapter();
    assertNull(grid().getAdapter());
  }

  @Test
  public void clickingTheLastCellOpensTheAllFeaturedDownloadsPage() {
    view.setSlides(new ArrayList<>(Arrays.asList(slide("a", false))));
    GridView grid = grid();
    grid.getOnItemClickListener().onItemClick(grid, null, 1, 0);
    ui.verify(
        () ->
            UIUtils.openURL(
                any(), org.mockito.ArgumentMatchers.contains("allFreeDownloadsButton")));
    // other cells do nothing more
    grid.getOnItemClickListener().onItemClick(grid, null, 0, 0);
  }

  @Test
  public void clickingBeforeThereIsAnAdapterDoesNothing() {
    GridView grid = grid();
    grid.getOnItemClickListener().onItemClick(grid, null, 0, 0);
    assertNull(grid.getAdapter());
  }

  @Test
  public void detachingKeepsTheAdapterAndSurvivesWithoutABanner() {
    view.setSlides(new ArrayList<>(Arrays.asList(slide("a", false))));
    view.onDetachedFromWindow();
    assertEquals(2, grid().getAdapter().getCount());
  }

  @Test
  public void destroyingTheBannerBeforeInflationIsSafe() {
    PromotionsView bare = new PromotionsView(context, null);
    bare.destroyPromotionsBanner();
    bare.onDetachedFromWindow();
    assertEquals(View.VISIBLE, bare.getVisibility());
  }
}
