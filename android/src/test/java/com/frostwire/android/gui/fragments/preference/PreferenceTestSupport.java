/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

/** What the preference fragment tests share: laying the list out and tapping what it shows. */
final class PreferenceTestSupport {
  private PreferenceTestSupport() {}

  /** Measures and lays out the preference list, which binds the rows (and their buttons). */
  static void layoutList(PreferenceFragmentCompat fragment) {
    RecyclerView list = fragment.getListView();
    list.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY));
    list.layout(0, 0, 1080, 20000);
  }

  /** The button of a {@code ButtonActionPreference} row, found by the text it shows. */
  static Button button(PreferenceFragmentCompat fragment, CharSequence text) {
    layoutList(fragment);
    return findButton(fragment.getListView(), text);
  }

  private static Button findButton(View view, CharSequence text) {
    if (view instanceof Button && text.toString().contentEquals(((Button) view).getText())) {
      return (Button) view;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        Button found = findButton(group.getChildAt(i), text);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /** What the framework does when a preference's value is picked: the listener may veto it. */
  static boolean change(Preference preference, Object newValue) {
    return preference.getOnPreferenceChangeListener().onPreferenceChange(preference, newValue);
  }
}
