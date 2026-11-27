/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.fragment.app.FragmentManager;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.views.EditTextDialog;
import com.frostwire.android.util.SystemUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

/** The dialog and snackbar helpers of UIUtils, with a themed activity and mocked dialog classes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class UIUtilsDialogsTest {
  private MockedStatic<SystemUtils> system;
  private final List<Runnable> ui = new ArrayList<>();
  private Activity activity;

  @Before
  public void setUp() {
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            call -> {
              ui.add(call.getArgument(0));
              return null;
            });
    activity = Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
    activity.setTheme(R.style.Theme_FrostWire);
  }

  @After
  public void tearDown() {
    system.close();
  }

  @Test
  public void aYesNoDialogRunsTheListenerOfTheButtonThatWasPressed() {
    YesNoDialog dialog = mock(YesNoDialog.class);
    Dialog underlying = mock(Dialog.class);
    when(dialog.getDialog()).thenReturn(underlying);
    FragmentManager fragments = mock(FragmentManager.class);
    DialogInterface.OnClickListener positive = mock(DialogInterface.OnClickListener.class);
    DialogInterface.OnClickListener negative = mock(DialogInterface.OnClickListener.class);
    try (MockedStatic<YesNoDialog> dialogs = mockStatic(YesNoDialog.class)) {
      dialogs
          .when(
              () ->
                  YesNoDialog.newInstance(
                      any(),
                      org.mockito.ArgumentMatchers.anyInt(),
                      any(String.class),
                      org.mockito.ArgumentMatchers.anyByte()))
          .thenReturn(dialog);
      UIUtils.showYesNoDialog(fragments, "Sure?", R.string.dismiss, positive, negative);
      verify(dialog).show(fragments);
      ArgumentCaptor<com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener>
          listener =
              ArgumentCaptor.forClass(
                  com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener.class);
      verify(dialog).setOnDialogClickListener(listener.capture());
      listener.getValue().onDialogClick("tag", Dialog.BUTTON_POSITIVE);
      verify(positive).onClick(underlying, Dialog.BUTTON_POSITIVE);
      verify(negative, never()).onClick(any(), org.mockito.ArgumentMatchers.anyInt());
      listener.getValue().onDialogClick("tag", Dialog.BUTTON_NEGATIVE);
      verify(negative).onClick(underlying, Dialog.BUTTON_NEGATIVE);
      verify(dialog, org.mockito.Mockito.times(2)).dismiss();
    }
  }

  @Test
  public void theSimpleYesNoDialogDismissesOnNo() {
    YesNoDialog dialog = mock(YesNoDialog.class);
    Dialog underlying = mock(Dialog.class);
    when(dialog.getDialog()).thenReturn(underlying);
    DialogInterface.OnClickListener positive = mock(DialogInterface.OnClickListener.class);
    try (MockedStatic<YesNoDialog> dialogs = mockStatic(YesNoDialog.class)) {
      dialogs
          .when(
              () ->
                  YesNoDialog.newInstance(
                      any(),
                      org.mockito.ArgumentMatchers.anyInt(),
                      any(String.class),
                      org.mockito.ArgumentMatchers.anyByte()))
          .thenReturn(dialog);
      UIUtils.showYesNoDialog(mock(FragmentManager.class), "Sure?", R.string.dismiss, positive);
      ArgumentCaptor<com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener>
          listener =
              ArgumentCaptor.forClass(
                  com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener.class);
      verify(dialog).setOnDialogClickListener(listener.capture());
      listener.getValue().onDialogClick("tag", Dialog.BUTTON_NEGATIVE);
      verify(underlying).dismiss();
      verify(positive, never()).onClick(any(), org.mockito.ArgumentMatchers.anyInt());
    }
  }

  @Test
  public void theEditTextDialogIsInitializedAndShown() {
    FragmentManager fragments = mock(FragmentManager.class);
    EditTextDialog.TextViewInputDialogCallback callback =
        mock(EditTextDialog.TextViewInputDialogCallback.class);
    try (MockedConstruction<EditTextDialog> constructed =
        mockConstruction(
            EditTextDialog.class,
            (mock, context) ->
                when(mock.init(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        any(),
                        any()))
                    .thenReturn(mock))) {
      UIUtils.showEditTextDialog(
          fragments,
          R.string.dismiss,
          R.string.dismiss,
          R.string.dismiss,
          true,
          false,
          "x",
          callback);
      assertEquals(1, constructed.constructed().size());
      verify(constructed.constructed().get(0)).show(fragments);
    }
  }

  @Test
  public void snackbarsAreShownOnTheViewHierarchy() {
    FrameLayout root = new FrameLayout(activity);
    activity.setContentView(root);
    UIUtils.showShortMessage(root, R.string.dismiss);
    UIUtils.showLongMessage(root, R.string.dismiss);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertNotNull(root);
  }

  @Test
  public void aDismissableMessageIsQueuedForTheUiThreadAndHasADismissAction() {
    FrameLayout root = new FrameLayout(activity);
    activity.setContentView(root);
    UIUtils.showDismissableMessage(root, R.string.dismiss);
    assertEquals(1, ui.size());
    ui.get(0).run();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    View action = activity.findViewById(com.google.android.material.R.id.snackbar_action);
    assertNotNull("the snackbar offers a dismiss action", action);
    assertEquals(
        activity.getString(R.string.dismiss),
        ((android.widget.Button) action).getText().toString());
    action.performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  }

  @Test
  public void theSocialLinksDialogOpensTheSocialPagesWithAReferrer() {
    DialogInterface.OnDismissListener dismissed = mock(DialogInterface.OnDismissListener.class);
    UIUtils.showSocialLinksDialog(activity, true, dismissed, " install ");
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(dialog);
    View facebook = dialog.findViewById(R.id.view_social_buttons_facebook_button);
    facebook.performClick();
    assertEquals(1, background().size());
    background().get(0).run();
    Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
    assertEquals(
        Constants.SOCIAL_URL_FACEBOOK_PAGE + "?ref=android_install", opened.getDataString());
    dialog.findViewById(R.id.view_social_buttons_twitter_button).performClick();
    background().get(1).run();
    assertEquals(
        Constants.SOCIAL_URL_TWITTER_PAGE + "?ref=android_install",
        Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
    // the installation complete section has a check button that dismisses the dialog
    dialog.findViewById(R.id.view_social_buttons_dismiss_check).performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    verify(dismissed).onDismiss(any());
  }

  @Test
  public void theSocialLinksDialogWithoutAReferrerSuffixStillWorks() {
    UIUtils.showSocialLinksDialog(activity, false, null, null);
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(dialog);
    dialog.findViewById(R.id.view_social_buttons_facebook_button).performClick();
    assertEquals(1, background().size());
  }

  private final List<Runnable> backgroundWork = new ArrayList<>();

  private List<Runnable> background() {
    return backgroundWork;
  }

  @Before
  public void captureBackgroundWork() {
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              backgroundWork.add(call.getArgument(1));
              return null;
            });
  }
}
