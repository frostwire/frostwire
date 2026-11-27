/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransfersNoSeedsViewTest {

  private Context context;
  private TransfersNoSeedsView view;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    context = ViewTestSupport.themedContext();
    view = new TransfersNoSeedsView(context, null);
    view.onFinishInflate();
  }

  private String text(int id) {
    return ((TextView) view.findViewById(id)).getText().toString();
  }

  @Test
  public void inactiveHidesTheView() {
    view.setVisibility(View.VISIBLE);
    view.setMode(TransfersNoSeedsView.Mode.INACTIVE);
    assertEquals(View.GONE, view.getVisibility());
  }

  @Test
  public void seedingDisabledOffersToEnableSeeding() {
    view.setMode(TransfersNoSeedsView.Mode.SEEDING_DISABLED);
    assertEquals(View.VISIBLE, view.getVisibility());
    assertEquals(
        context.getString(R.string.seeding_is_currently_disabled),
        text(R.id.view_transfers_no_seeds_top_textview));
    assertEquals(
        context.getString(R.string.seeding_keeps_the_network_healthy),
        text(R.id.view_transfers_no_seeds_mid_bold_textview));
    assertEquals(
        context.getString(R.string.the_more_you_give_the_more_you_receive),
        text(R.id.view_transfers_no_seeds_bottom_textview));
    assertEquals(
        context.getString(R.string.enable_seeding),
        ((Button) view.findViewById(R.id.view_transfers_no_seeds_button)).getText().toString());
  }

  @Test
  public void seedAllFinishedOffersToSeedTheFinishedTransfers() {
    view.setMode(TransfersNoSeedsView.Mode.SEED_ALL_FINISHED);
    assertEquals(View.VISIBLE, view.getVisibility());
    assertEquals(
        context.getString(R.string.nothing_is_currently_seeding),
        text(R.id.view_transfers_no_seeds_top_textview));
    assertEquals(
        context.getString(R.string.would_you_like_to_seed_your_finished_torrent_transfers),
        text(R.id.view_transfers_no_seeds_mid_bold_textview));
    assertEquals(
        context.getString(R.string.seed_all_finished),
        ((Button) view.findViewById(R.id.view_transfers_no_seeds_button)).getText().toString());
  }

  @Test
  public void theEnableButtonTurnsSeedingOnAndHidesTheView() {
    ConfigurationManager config = ViewTestSupport.configuration();
    config.setSeedFinishedTorrents(false);
    view.setMode(TransfersNoSeedsView.Mode.SEEDING_DISABLED);

    view.findViewById(R.id.view_transfers_no_seeds_button).performClick();

    assertTrue(
        config.getBoolean(
            com.frostwire.android.core.Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS));
    assertEquals(View.GONE, view.getVisibility());
  }

  @Test
  public void theSeedAllButtonRunsTheSeedActionAndHidesTheView() {
    try (MockedConstruction<SeedAction> seedActions = Mockito.mockConstruction(SeedAction.class)) {
      view.setMode(TransfersNoSeedsView.Mode.SEED_ALL_FINISHED);

      view.findViewById(R.id.view_transfers_no_seeds_button).performClick();

      assertEquals(1, seedActions.constructed().size());
      verify(seedActions.constructed().get(0)).onClick();
      assertEquals(View.GONE, view.getVisibility());
    }
  }

  @Test
  public void theButtonDoesNothingWhileInactive() {
    view.setMode(TransfersNoSeedsView.Mode.INACTIVE);
    view.setVisibility(View.VISIBLE);
    view.findViewById(R.id.view_transfers_no_seeds_button).performClick();
    assertEquals(View.VISIBLE, view.getVisibility());
    assertFalse(text(R.id.view_transfers_no_seeds_bottom_textview).isEmpty());
  }
}
