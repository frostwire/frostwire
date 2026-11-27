/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.view.View;
import android.widget.Button;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ViewFlipper;
import com.frostwire.android.gui.views.WizardPageView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The first-run wizard: page navigation, completion gating and the final settings flags. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class WizardActivityTest {

  private MockedStatic<ConfigurationManager> configs;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<com.frostwire.android.AndroidPlatform> platform;
  private ConfigurationManager config;
  private WizardActivity activity;

  @Before
  public void setUp() {
    config = mock(ConfigurationManager.class);
    configs = mockStatic(ConfigurationManager.class);
    configs.when(ConfigurationManager::instance).thenReturn(config);
    ui = mockStatic(UIUtils.class);
    platform = mockStatic(com.frostwire.android.AndroidPlatform.class);
    activity =
        Robolectric.buildActivity(WizardActivity.class).create().start().resume().visible().get();
  }

  @After
  public void tearDown() {
    configs.close();
    ui.close();
    platform.close();
  }

  private Button next() {
    return activity.findViewById(R.id.activity_wizard_button_next);
  }

  private Button previous() {
    return activity.findViewById(R.id.activity_wizard_button_previous);
  }

  private ViewFlipper flipper() {
    return activity.findViewById(R.id.activity_wizard_view_flipper);
  }

  @Test
  public void startsOnTheFirstPageWithNoBackButtonAndNextWaitingForTheUser() {
    assertEquals(View.INVISIBLE, previous().getVisibility());
    assertEquals(activity.getString(R.string.wizard_next), next().getText().toString());
    assertTrue(flipper().getChildCount() >= 2);
    assertEquals(0, flipper().getCurrentView().getTag());
    WizardPageView first = (WizardPageView) flipper().getCurrentView();
    assertFalse(first.hasPrevious());
  }

  @Test
  public void everyPageIsTaggedWithItsIndex() {
    for (int i = 0; i < flipper().getChildCount(); i++) {
      assertEquals(i, flipper().getChildAt(i).getTag());
    }
  }

  @Test
  public void theFirstPageUnlocksNextOnceItHasValidated() {
    // GeneralWizardPage validates itself on load() and reports completion to the activity
    assertTrue(next().isEnabled());
  }

  @Test
  public void nextAndPreviousFlipThroughThePagesAndFinishPersistsThePageChoices() {
    int pages = flipper().getChildCount();
    for (int i = 0; i < pages - 1; i++) {
      WizardPageView page = (WizardPageView) flipper().getCurrentView();
      assertTrue("page " + i + " has a next page", page.hasNext());
      next().setEnabled(true);
      next().performClick();
      assertEquals(i + 1, flipper().getCurrentView().getTag());
      assertEquals(View.VISIBLE, previous().getVisibility());
    }
    // on the last page the button says Finish
    assertEquals(activity.getString(R.string.wizard_finish), next().getText().toString());
    verify(config, never()).setBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED, true);
    next().setEnabled(true);
    next().performClick();
    verify(config).setBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED, true);
    verify(config).setBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE, true);
    verify(config)
        .setLong(
            org.mockito.ArgumentMatchers.eq(Constants.PREF_KEY_GUI_INSTALLATION_TIMESTAMP),
            org.mockito.ArgumentMatchers.anyLong());

    previous().performClick();
    assertEquals(pages - 2, flipper().getCurrentView().getTag());
  }

  @Test
  public void backOnTheFirstPageShutsTheAppDown() {
    activity.getOnBackPressedDispatcher().onBackPressed();
    ui.verify(() -> UIUtils.sendShutdownIntent(activity));
    assertTrue(activity.isFinishing());
  }

  @Test
  public void backOnALaterPageGoesToThePreviousPage() {
    next().setEnabled(true);
    next().performClick();
    assertEquals(1, flipper().getCurrentView().getTag());
    activity.getOnBackPressedDispatcher().onBackPressed();
    assertEquals(0, flipper().getCurrentView().getTag());
    assertFalse(activity.isFinishing());
  }
}
