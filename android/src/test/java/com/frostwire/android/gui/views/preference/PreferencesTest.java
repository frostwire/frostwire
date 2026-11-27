/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.ViewTestSupport;
import com.frostwire.android.util.SystemUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PreferencesTest {

  /** The torrent settings screen, which holds the seek bar and port range preferences. */
  public static final class TorrentSettings extends AbstractPreferenceFragment {
    public TorrentSettings() {
      super(R.xml.settings_torrent);
    }

    void show(DialogFragment dialog) {
      showPreferenceDialog(dialog);
    }
  }

  private static final String MAX_DOWNLOAD_SPEED = "frostwire.prefs.torrent.max_download_speed";
  private static final String MAX_DOWNLOADS = "frostwire.prefs.torrent.max_downloads";
  private static final String PORT_RANGE = "frostwire.prefs.torrent.incoming_connection_port_range";

  private MockedStatic<SystemUtils> system;
  private ConfigurationManager config;
  private FragmentActivity activity;
  private TorrentSettings settings;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    config = ViewTestSupport.configuration();
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            i -> {
              ((Runnable) i.getArgument(1)).run();
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            i -> {
              ((Runnable) i.getArgument(0)).run();
              return null;
            });
    activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    FrameLayout container = new FrameLayout(activity);
    container.setId(0x7f0f0060);
    activity.setContentView(container);
    settings = new TorrentSettings();
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0060, settings, "torrent")
        .commitNow();
    ShadowLooper.idleMainLooper();
  }

  @After
  public void tearDown() {
    system.close();
  }

  private <T extends Preference> T pref(String key) {
    Preference found = settings.findPreference(key);
    assertNotNull(key, found);
    @SuppressWarnings("unchecked")
    T typed = (T) found;
    return typed;
  }

  private Dialog showSeekBarDialog(String key) {
    CustomSeekBarPreference preference = pref(key);
    CustomSeekBarPreference.CustomSeekBarPreferenceDialog dialog =
        CustomSeekBarPreference.CustomSeekBarPreferenceDialog.newInstance(preference);
    settings.show(dialog);
    ShadowLooper.idleMainLooper();
    return dialog.getDialog();
  }

  private TextView current(Dialog dialog) {
    return dialog.findViewById(R.id.dialog_preference_seekbar_with_checkbox_current_value_textview);
  }

  private SeekBar seekBar(Dialog dialog) {
    return dialog.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar);
  }

  private CheckBox unlimited(Dialog dialog) {
    return dialog.findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox);
  }

  private static void positive(Dialog dialog) {
    ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    // the dialog reports its result once its dismissal has been handled
    ShadowLooper.idleMainLooper();
  }

  // ---- CustomSeekBarPreference

  @Test
  public void aByteRatePreferenceSummarizesItsStoredValue() {
    config.setLong(MAX_DOWNLOAD_SPEED, 2048);
    CustomSeekBarPreference preference = pref(MAX_DOWNLOAD_SPEED);
    FrameLayout row = new FrameLayout(activity);
    TextView summary = new TextView(activity);
    summary.setId(android.R.id.summary);
    row.addView(summary);

    preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));

    assertEquals(UIUtils.getBytesInHuman(2048), summary.getText().toString());
  }

  @Test
  public void anUnlimitedValueIsSummarizedAsUnlimited() {
    config.setLong(MAX_DOWNLOAD_SPEED, 0);
    CustomSeekBarPreference preference = pref(MAX_DOWNLOAD_SPEED);
    FrameLayout row = new FrameLayout(activity);
    TextView summary = new TextView(activity);
    summary.setId(android.R.id.summary);
    row.addView(summary);

    preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));

    assertEquals(activity.getString(R.string.unlimited), summary.getText().toString());
  }

  @Test
  public void aPluralPreferenceSummarizesWithItsUnit() {
    config.setLong(MAX_DOWNLOADS, 3);
    CustomSeekBarPreference preference = pref(MAX_DOWNLOADS);
    FrameLayout row = new FrameLayout(activity);
    TextView summary = new TextView(activity);
    summary.setId(android.R.id.summary);
    row.addView(summary);

    preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));

    assertEquals(
        activity.getResources().getQuantityString(R.plurals.unit_downloads, 3, 3L),
        summary.getText().toString());
  }

  @Test
  public void theSeekBarDialogStartsFromTheStoredValue() {
    config.setLong(MAX_DOWNLOADS, 7);
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOADS);

    assertEquals(10, seekBar(dialog).getMax());
    assertEquals(7, seekBar(dialog).getProgress());
    assertEquals(
        activity.getResources().getQuantityString(R.plurals.unit_downloads, 7, 7),
        current(dialog).getText().toString());
    assertEquals(View.GONE, unlimited(dialog).getVisibility());
  }

  @Test
  public void draggingBelowTheMinimumSticksAtTheMinimum() {
    config.setLong(MAX_DOWNLOADS, 7);
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOADS);

    seekBar(dialog).setProgress(0);

    assertEquals(1, seekBar(dialog).getProgress());
    assertEquals(
        activity.getResources().getQuantityString(R.plurals.unit_downloads, 1, 1),
        current(dialog).getText().toString());
  }

  @Test
  public void aByteRateDialogShowsTheRateAndOffersUnlimited() {
    config.setLong(MAX_DOWNLOAD_SPEED, 4096);
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOAD_SPEED);

    assertEquals(View.VISIBLE, unlimited(dialog).getVisibility());
    assertFalse(unlimited(dialog).isChecked());
    assertEquals(
        String.format("%s/s", UIUtils.getBytesInHuman(4096)), current(dialog).getText().toString());

    seekBar(dialog).setProgress(8192);
    assertEquals(
        String.format("%s/s", UIUtils.getBytesInHuman(8192)), current(dialog).getText().toString());
  }

  @Test
  public void checkingUnlimitedPinsTheBarAtItsEndAndUncheckingReleasesIt() {
    config.setLong(MAX_DOWNLOAD_SPEED, 4096);
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOAD_SPEED);

    unlimited(dialog).performClick();

    assertTrue(unlimited(dialog).isChecked());
    assertFalse(seekBar(dialog).isEnabled());
    assertEquals(seekBar(dialog).getMax(), seekBar(dialog).getProgress());
    assertEquals(activity.getString(R.string.unlimited), current(dialog).getText().toString());

    unlimited(dialog).performClick();

    assertFalse(unlimited(dialog).isChecked());
    assertTrue(seekBar(dialog).isEnabled());
    assertFalse(activity.getString(R.string.unlimited).contentEquals(current(dialog).getText()));
  }

  @Test
  public void aStoredUnlimitedValueOpensTheDialogWithUnlimitedChecked() {
    config.setLong(MAX_DOWNLOAD_SPEED, 0);
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOAD_SPEED);

    assertTrue(unlimited(dialog).isChecked());
    assertFalse(seekBar(dialog).isEnabled());
    assertEquals(seekBar(dialog).getMax(), seekBar(dialog).getProgress());
    assertEquals(activity.getString(R.string.unlimited), current(dialog).getText().toString());
  }

  @Test
  public void confirmingStoresTheValueAndNotifiesTheChangeListener() {
    config.setLong(MAX_DOWNLOADS, 7);
    CustomSeekBarPreference preference = pref(MAX_DOWNLOADS);
    List<Object> changes = new ArrayList<>();
    preference.setOnPreferenceChangeListener(
        (p, value) -> {
          changes.add(value);
          return true;
        });
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOADS);
    seekBar(dialog).setProgress(9);

    positive(dialog);

    assertEquals(9L, config.getLong(MAX_DOWNLOADS, -1));
    assertEquals(List.of(9), changes);
  }

  @Test
  public void confirmingWithUnlimitedCheckedStoresTheUnlimitedValue() {
    config.setLong(MAX_DOWNLOAD_SPEED, 0);
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOAD_SPEED);
    assertTrue(unlimited(dialog).isChecked());

    positive(dialog);

    assertEquals(0L, config.getLong(MAX_DOWNLOAD_SPEED, -1));
  }

  @Test
  public void aListenerThatThrowsDoesNotStopTheValueFromBeingStored() {
    config.setLong(MAX_DOWNLOADS, 7);
    CustomSeekBarPreference preference = pref(MAX_DOWNLOADS);
    preference.setOnPreferenceChangeListener(
        (p, value) -> {
          throw new IllegalStateException("boom");
        });
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOADS);
    seekBar(dialog).setProgress(5);

    positive(dialog);

    assertEquals(5L, config.getLong(MAX_DOWNLOADS, -1));
  }

  @Test
  public void cancellingStoresNothing() {
    config.setLong(MAX_DOWNLOADS, 7);
    Dialog dialog = showSeekBarDialog(MAX_DOWNLOADS);
    seekBar(dialog).setProgress(3);

    ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    ShadowLooper.idleMainLooper();

    assertEquals(7L, config.getLong(MAX_DOWNLOADS, -1));
  }

  // ---- PortRangePreference

  private Dialog showPortDialog() {
    PortRangePreference.PortRangePreferenceDialog dialog =
        PortRangePreference.PortRangePreferenceDialog.newInstance(PORT_RANGE);
    settings.show(dialog);
    ShadowLooper.idleMainLooper();
    return dialog.getDialog();
  }

  private EditText startPort(Dialog d) {
    return d.findViewById(R.id.start_port_edit_text);
  }

  private EditText endPort(Dialog d) {
    return d.findViewById(R.id.end_port_edit_text);
  }

  @Test
  public void thePortRangeLoadsFromTheSettingsAndShowsInItsSummary() {
    config.setInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, 2000);
    config.setInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END, 3000);
    Context context = settings.requireContext();
    PortRangePreference preference = new PortRangePreference(context, null);

    assertEquals(2000, preference.getStartPort());
    assertEquals(3000, preference.getEndPort());
    assertEquals(
        context.getString(R.string.port_range_current_range, 2000, 3000),
        preference.getSummary().toString());
  }

  @Test
  public void anInvalidStoredPortRangeFallsBackToTheDefaults() {
    config.setInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, 5000);
    config.setInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END, 4000);

    PortRangePreference preference = new PortRangePreference(settings.requireContext(), null);

    assertEquals(Constants.DEFAULT_TORRENT_INCOMING_PORT_START, preference.getStartPort());
    assertEquals(Constants.DEFAULT_TORRENT_INCOMING_PORT_END, preference.getEndPort());
  }

  @Test
  public void thePortDialogShowsTheCurrentRangeAndResetsToTheDefaultRange() {
    PortRangePreference preference = pref(PORT_RANGE);
    preference.setPortRange(2000, 3000);
    Dialog dialog = showPortDialog();

    assertEquals("2000", startPort(dialog).getText().toString());
    assertEquals("3000", endPort(dialog).getText().toString());

    ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
    assertEquals("1024", startPort(dialog).getText().toString());
    assertEquals("57000", endPort(dialog).getText().toString());
  }

  @Test
  public void applyingAValidRangeStoresItAndUpdatesTheSummary() {
    PortRangePreference preference = pref(PORT_RANGE);
    Dialog dialog = showPortDialog();
    startPort(dialog).setText("40000");
    endPort(dialog).setText("41000");

    positive(dialog);

    assertEquals(40000, preference.getStartPort());
    assertEquals(41000, preference.getEndPort());
    assertEquals(40000, config.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, -1));
    assertEquals(41000, config.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END, -1));
    assertEquals(
        activity.getString(R.string.port_range_current_range, 40000, 41000),
        preference.getSummary().toString());
  }

  @Test
  public void applyingTheSameRangeChangesNothing() {
    PortRangePreference preference = pref(PORT_RANGE);
    int start = preference.getStartPort();
    int end = preference.getEndPort();
    config.setInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, 1111);

    preference.setPortRange(start, end);

    assertEquals(1111, config.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, -1));
  }

  @Test
  public void aPortBelowTheAllowedRangeIsRejectedWithAMessage() {
    PortRangePreference preference = pref(PORT_RANGE);
    int start = preference.getStartPort();
    Dialog dialog = showPortDialog();
    startPort(dialog).setText("80");
    endPort(dialog).setText("2000");

    positive(dialog);

    assertEquals(start, preference.getStartPort());
    assertEquals(
        activity.getString(R.string.port_range_invalid_port), ShadowToast.getTextOfLatestToast());
  }

  @Test
  public void aReversedRangeIsRejectedWithAMessage() {
    PortRangePreference preference = pref(PORT_RANGE);
    int start = preference.getStartPort();
    Dialog dialog = showPortDialog();
    startPort(dialog).setText("5000");
    endPort(dialog).setText("2000");

    positive(dialog);

    assertEquals(start, preference.getStartPort());
    assertEquals(
        activity.getString(R.string.port_range_invalid_range), ShadowToast.getTextOfLatestToast());
  }

  @Test
  public void aNonNumericPortIsRejectedWithAMessage() {
    Dialog dialog = showPortDialog();
    startPort(dialog).setText("");
    endPort(dialog).setText("2000");

    positive(dialog);

    assertEquals(
        activity.getString(R.string.port_range_invalid_port), ShadowToast.getTextOfLatestToast());
  }

  @Test
  public void cancellingThePortDialogChangesNothing() {
    PortRangePreference preference = pref(PORT_RANGE);
    int start = preference.getStartPort();
    Dialog dialog = showPortDialog();
    startPort(dialog).setText("40000");

    ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    ShadowLooper.idleMainLooper();

    assertEquals(start, preference.getStartPort());
    assertNull(ShadowToast.getLatestToast());
  }

  // ---- ButtonActionPreference

  @SuppressWarnings("deprecation")
  private AttributeSet buttonAttributes(String text) {
    return Robolectric.buildAttributeSet().addAttribute(R.attr.button_text, text).build();
  }

  private View buttonRow() {
    return LayoutInflater.from(activity).inflate(R.layout.view_preference_button_action, null);
  }

  @Test
  public void theActionButtonShowsItsTextAndRunsTheListener() {
    ButtonActionPreference preference =
        new ButtonActionPreference(settings.requireContext(), buttonAttributes("Generate"));
    List<View> clicks = new ArrayList<>();
    preference.setOnActionListener(clicks::add);
    View row = buttonRow();

    preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));

    Button button = row.findViewById(R.id.view_preference_button_action_button);
    assertEquals("Generate", button.getText().toString());
    button.performClick();
    assertEquals(1, clicks.size());
  }

  @Test
  public void aListenerSetAfterBindingReachesTheBoundButton() {
    ButtonActionPreference preference =
        new ButtonActionPreference(settings.requireContext(), buttonAttributes("Go"));
    View row = buttonRow();
    preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));
    List<View> clicks = new ArrayList<>();

    preference.setOnActionListener(clicks::add);
    row.findViewById(R.id.view_preference_button_action_button).performClick();

    assertEquals(1, clicks.size());
  }

  // ---- CommunityButtonsPreference

  @Test
  public void theCommunityButtonsLinkToTheProjectPagesAndRateTheApp() {
    CommunityButtonsPreference preference =
        new CommunityButtonsPreference(settings.requireContext(), null);
    View row =
        LayoutInflater.from(activity).inflate(R.layout.view_preference_community_buttons, null);
    try (MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));

      String ref = "?ref=android_preferences";
      ui.verify(
          () ->
              UIUtils.setupClickUrl(
                  eq(row.findViewById(R.id.view_preference_community_blog_button)),
                  eq(Constants.FROSTWIRE_BLOG_URL + ref)));
      ui.verify(
          () ->
              UIUtils.setupClickUrl(
                  eq(row.findViewById(R.id.view_preference_community_facebook_button)),
                  eq(Constants.SOCIAL_URL_FACEBOOK_PAGE + ref)));
      ui.verify(
          () ->
              UIUtils.setupClickUrl(
                  eq(row.findViewById(R.id.view_preference_community_twitter_button)),
                  eq(Constants.SOCIAL_URL_TWITTER_PAGE + ref)));
      ui.verify(
          () ->
              UIUtils.setupClickUrl(
                  eq(row.findViewById(R.id.view_preference_community_github_button)),
                  eq(Constants.SOCIAL_URL_GITHUB_PAGE + ref)));
      ui.verify(
          () ->
              UIUtils.setupClickUrl(
                  eq(row.findViewById(R.id.view_preference_community_slack_button)),
                  eq(Constants.SOCIAL_URL_SLACK_PAGE + ref)));
    }

    row.findViewById(R.id.view_preference_community_rate_our_app_button).performClick();
    Intent started =
        org.robolectric.Shadows.shadowOf(activity.getApplication()).getNextStartedActivity();
    assertNotNull(started);
    assertEquals("market://details?id=" + Constants.APP_PACKAGE_NAME, started.getDataString());
    assertSame(Intent.ACTION_VIEW, started.getAction());
  }
}
