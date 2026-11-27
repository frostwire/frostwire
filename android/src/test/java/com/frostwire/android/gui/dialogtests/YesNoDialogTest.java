/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Dialog;
import android.widget.Button;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.views.DialogTestHost;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Lives outside the dialogs test package because build.gradle excludes that whole package (its old
 * tests boot MainApplication); these use a plain Application.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class YesNoDialogTest {

  private final List<String> clicks = new ArrayList<>();

  private Dialog show(YesNoDialog dialog) {
    dialog.setOnDialogClickListener((tag, which) -> clicks.add(tag + ":" + which));
    return new DialogTestHost().show(dialog, "yesno");
  }

  @Test
  public void showsTheTitleAndTheMessageFromAResource() {
    Dialog dialog = show(YesNoDialog.newInstance("id1", R.string.about, R.string.cancel));
    assertNotNull(dialog);
    assertEquals(
        dialog.getContext().getString(R.string.about),
        ((TextView) dialog.findViewById(R.id.dialog_default_title)).getText().toString());
    assertEquals(
        dialog.getContext().getString(R.string.cancel),
        ((TextView) dialog.findViewById(R.id.dialog_default_text)).getText().toString());
  }

  @Test
  public void showsAMessageString() {
    Dialog dialog = show(YesNoDialog.newInstance("id2", R.string.about, "Delete it?", (byte) 0));
    assertEquals(
        "Delete it?",
        ((TextView) dialog.findViewById(R.id.dialog_default_text)).getText().toString());
  }

  @Test
  public void yesReportsThePositiveClickAndKeepsTheDialogUnlessFlagged() {
    Dialog dialog = show(YesNoDialog.newInstance("id3", R.string.about, R.string.cancel));
    ((Button) dialog.findViewById(R.id.dialog_default_button_yes)).performClick();
    assertEquals(List.of("id3:" + Dialog.BUTTON_POSITIVE), clicks);
    assertTrue(dialog.isShowing());
  }

  @Test
  public void yesCanDismissBeforeReporting() {
    Dialog dialog =
        show(
            YesNoDialog.newInstance(
                "id4",
                R.string.about,
                R.string.cancel,
                YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK));
    ((Button) dialog.findViewById(R.id.dialog_default_button_yes)).performClick();
    assertFalse(dialog.isShowing());
    assertEquals(List.of("id4:" + Dialog.BUTTON_POSITIVE), clicks);
  }

  @Test
  public void noDismissesAndReportsTheNegativeClick() {
    Dialog dialog = show(YesNoDialog.newInstance("id5", R.string.about, R.string.cancel));
    ((Button) dialog.findViewById(R.id.dialog_default_button_no)).performClick();
    assertFalse(dialog.isShowing());
    assertEquals(List.of("id5:" + Dialog.BUTTON_NEGATIVE), clicks);
  }
}
