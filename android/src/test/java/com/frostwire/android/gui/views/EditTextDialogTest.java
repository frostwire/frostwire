/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class EditTextDialogTest {

  private DialogTestHost host;
  private final List<String> submissions = new ArrayList<>();

  @Before
  public void setUp() {
    host = new DialogTestHost();
  }

  private EditTextDialog dialog(boolean cancelable, boolean multiline, String initial) {
    return new EditTextDialog()
        .init(
            R.string.about,
            R.string.cancel,
            R.string.retry,
            cancelable,
            multiline,
            initial,
            (value, cancelled) -> submissions.add(value + ":" + cancelled));
  }

  private EditText input(Dialog d) {
    return d.findViewById(R.id.dialog_default_input_text);
  }

  @Test
  public void showsTheTitleHintAndButtonLabels() {
    Dialog shown = host.show(dialog(true, false, null), "e");
    assertEquals(
        host.activity.getString(R.string.about),
        ((TextView) shown.findViewById(R.id.dialog_default_input_title)).getText().toString());
    assertEquals(host.activity.getString(R.string.cancel), input(shown).getHint().toString());
    assertEquals(
        host.activity.getString(R.string.retry),
        ((Button) shown.findViewById(R.id.dialog_default_input_button_yes)).getText().toString());
    assertEquals(
        host.activity.getString(R.string.cancel),
        ((Button) shown.findViewById(R.id.dialog_default_input_button_no)).getText().toString());
    assertEquals(
        View.VISIBLE, shown.findViewById(R.id.dialog_default_input_button_no).getVisibility());
    assertEquals(1, input(shown).getMaxLines());
  }

  @Test
  public void anUncancelableDialogHasNoCancelButtonAndAMultilineOneAllowsFiveLines() {
    Dialog shown = host.show(dialog(false, true, null), "e");
    assertEquals(
        View.GONE, shown.findViewById(R.id.dialog_default_input_button_no).getVisibility());
    assertEquals(5, input(shown).getMaxLines());
  }

  @Test
  public void startsWithTheOptionalValue() {
    Dialog shown = host.show(dialog(true, false, "preset"), "e");
    assertEquals("preset", input(shown).getText().toString());
  }

  @Test
  public void submittingDeliversTheTypedTextOnceAndDismisses() {
    Dialog shown = host.show(dialog(true, false, null), "e");
    input(shown).setText("hello");
    shown.findViewById(R.id.dialog_default_input_button_yes).performClick();
    assertEquals(List.of("hello:false"), submissions);
    assertFalse(shown.isShowing());
  }

  @Test
  public void cancellingDeliversACancelledResultAndDismisses() {
    Dialog shown = host.show(dialog(true, false, null), "e");
    shown.findViewById(R.id.dialog_default_input_button_no).performClick();
    assertEquals(List.of("null:true"), submissions);
    assertFalse(shown.isShowing());
  }

  @Test
  public void aCallbackThatThrowsDoesNotStopTheDialogFromClosing() {
    EditTextDialog d =
        new EditTextDialog()
            .init(
                R.string.about,
                R.string.cancel,
                R.string.retry,
                true,
                false,
                null,
                (value, cancelled) -> {
                  throw new IllegalStateException("boom");
                });
    Dialog shown = host.show(d, "e");
    shown.findViewById(R.id.dialog_default_input_button_yes).performClick();
    assertFalse(shown.isShowing());
  }

  @Test
  public void theStateIsSavedAndRestored() {
    EditTextDialog d = dialog(true, true, null);
    Dialog shown = host.show(d, "e");
    input(shown).setText("typed");
    Bundle saved = new Bundle();
    d.onSaveInstanceState(saved);
    assertEquals(R.string.about, saved.getInt("titleStringId"));
    assertEquals(R.string.cancel, saved.getInt("messageStringId"));
    assertEquals(R.string.retry, saved.getInt("positiveButtonStringId"));
    assertTrue(saved.getBoolean("cancelable"));
    assertTrue(saved.getBoolean("multilineInput"));
    assertEquals("typed", saved.getString("inputEditText"));

    // a dialog that was recreated gets its labels and the typed text back from that state
    EditTextDialog other =
        new EditTextDialog()
            .init(R.string.cancel, R.string.about, R.string.about, false, false, null, null);
    Dialog otherShown = host.show(other, "e2");
    other.initComponents(otherShown, saved);
    assertEquals(
        host.activity.getString(R.string.about),
        ((TextView) otherShown.findViewById(R.id.dialog_default_input_title)).getText().toString());
    assertEquals("typed", input(otherShown).getText().toString());
    assertEquals(5, input(otherShown).getMaxLines());
    assertEquals(
        View.VISIBLE, otherShown.findViewById(R.id.dialog_default_input_button_no).getVisibility());
  }

  @Test
  public void emptyTextIsNotSavedAsTyped() {
    EditTextDialog d = dialog(true, false, null);
    host.show(d, "e");
    Bundle saved = new Bundle();
    d.onSaveInstanceState(saved);
    assertNull(saved.getString("inputEditText"));
  }
}
