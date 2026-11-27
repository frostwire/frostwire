/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import com.frostwire.android.R;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowLooper;

/** Shows dialog fragments in a plain FragmentActivity with the app theme. */
public final class DialogTestHost {
  public final FragmentActivity activity;

  public DialogTestHost() {
    this(Robolectric.buildActivity(FragmentActivity.class).setup().get());
  }

  public DialogTestHost(FragmentActivity activity) {
    this.activity = activity;
    activity.setTheme(R.style.Theme_FrostWire);
  }

  /** Shows the dialog and returns the platform dialog it created. */
  public Dialog show(DialogFragment fragment, String tag) {
    fragment.show(activity.getSupportFragmentManager(), tag);
    ShadowLooper.idleMainLooper();
    return fragment.getDialog();
  }
}
