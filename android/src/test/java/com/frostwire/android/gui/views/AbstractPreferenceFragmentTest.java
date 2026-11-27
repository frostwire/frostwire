/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AbstractPreferenceFragmentTest {

  /** A screen made of the application settings. */
  public static final class SettingsFragment extends AbstractPreferenceFragment {
    boolean componentsInitialized;

    public SettingsFragment() {
      super(R.xml.settings_application);
    }

    @Override
    protected void initComponents() {
      componentsInitialized = true;
    }

    <T extends Preference> T pref(String key) {
      return findPreference(key);
    }

    void enable(Preference p, boolean enabled, boolean notify) {
      setEnabled(p, enabled, notify);
    }

    void check(androidx.preference.TwoStatePreference p, boolean checked, boolean notify) {
      setChecked(p, checked, notify);
    }

    void show(androidx.fragment.app.DialogFragment d) {
      showPreferenceDialog(d);
    }
  }

  /** A dialog for the first list preference of the screen. */
  public static final class ListDialog extends AbstractPreferenceFragment.PreferenceDialogFragment {
    boolean closed;

    static ListDialog forKey(String key) {
      ListDialog d = new ListDialog();
      Bundle args = new Bundle();
      args.putString(ARG_KEY, key);
      d.setArguments(args);
      return d;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
      closed = positiveResult;
    }
  }

  private FragmentActivity activity;
  private SettingsFragment fragment;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    ViewTestSupport.configuration();
    activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    fragment = new SettingsFragment();
    View container = new android.widget.FrameLayout(activity);
    container.setId(0x7f0f0010);
    activity.setContentView(container);
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(0x7f0f0010, fragment, "settings")
        .commitNow();
    ShadowLooper.idleMainLooper();
  }

  private static ListPreference findList(androidx.preference.PreferenceGroup group) {
    for (int i = 0; i < group.getPreferenceCount(); i++) {
      Preference p = group.getPreference(i);
      if (p instanceof ListPreference) {
        return (ListPreference) p;
      }
      if (p instanceof androidx.preference.PreferenceGroup) {
        ListPreference nested = findList((androidx.preference.PreferenceGroup) p);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  private ListPreference firstListPreference() {
    ListPreference list = findList(fragment.getPreferenceScreen());
    assertNotNull("no list preference in settings_application", list);
    return list;
  }

  @Test
  public void loadsTheXmlAndNotifiesTheSubclass() {
    assertTrue(fragment.componentsInitialized);
    assertTrue(fragment.getPreferenceScreen().getPreferenceCount() > 0);
  }

  @Test
  public void aNonPositiveResourceIdIsRejected() {
    int rejected = 0;
    for (int id : new int[] {0, -5}) {
      try {
        new AbstractPreferenceFragment(id) {};
      } catch (IllegalArgumentException e) {
        rejected++;
      }
    }
    assertEquals(2, rejected);
  }

  @Test
  public void findPreferenceReturnsTheTypedPreference() {
    ListPreference list = firstListPreference();
    ListPreference found = fragment.pref(list.getKey());
    assertSame(list, found);
    assertNull(fragment.pref("no.such.key"));
  }

  @Test
  public void enablingQuietlyDoesNotFireTheChangeListener() {
    CheckBoxPreference box = new CheckBoxPreference(activity);
    List<Object> changes = new ArrayList<>();
    Preference.OnPreferenceChangeListener listener =
        (p, value) -> {
          changes.add(value);
          return true;
        };
    box.setOnPreferenceChangeListener(listener);

    fragment.enable(box, false, false);
    assertFalse(box.isEnabled());
    assertSame(listener, box.getOnPreferenceChangeListener());
    fragment.enable(box, true, true);
    assertTrue(box.isEnabled());
  }

  @Test
  public void checkingQuietlyKeepsTheListenerButSkipsIt() {
    CheckBoxPreference box = new CheckBoxPreference(activity);
    box.setPersistent(false);
    List<Object> changes = new ArrayList<>();
    Preference.OnPreferenceChangeListener listener =
        (p, value) -> {
          changes.add(value);
          return true;
        };
    box.setOnPreferenceChangeListener(listener);

    fragment.check(box, true, false);
    assertTrue(box.isChecked());
    assertTrue(changes.isEmpty());
    assertSame(listener, box.getOnPreferenceChangeListener());

    fragment.check(box, false, true);
    assertFalse(box.isChecked());
  }

  @Test
  public void aPreferenceDialogIsBuiltFromThePreferenceItBelongsTo() {
    ListPreference list = firstListPreference();
    list.setDialogTitle("Pick one");
    list.setPositiveButtonText("Yes please");
    ListDialog dialog = ListDialog.forKey(list.getKey());

    fragment.show(dialog);
    ShadowLooper.idleMainLooper();

    Dialog shown = dialog.getDialog();
    assertNotNull(shown);
    assertTrue(shown.isShowing());
    assertSame(list, dialog.getPreference());
    TextView title = shown.findViewById(androidx.appcompat.R.id.alertTitle);
    assertEquals("Pick one", title.getText().toString());
  }

  @Test
  public void aDialogWithoutArgumentsIsRejected() {
    ListDialog dialog = new ListDialog();
    int rejected = 0;
    try {
      dialog.getPreference();
    } catch (IllegalStateException e) {
      rejected++;
    }
    assertEquals(1, rejected);
  }
}
