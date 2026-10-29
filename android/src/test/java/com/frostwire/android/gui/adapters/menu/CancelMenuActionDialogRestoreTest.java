/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.adapters.menu;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.frostwire.android.R;
import com.frostwire.transfers.Transfer;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * frostwire/frostwire#1381: the cancel-transfer dialog kept its transfer in static fields, so a
 * dialog the FragmentManager re-created after process death confirmed a cancel of null and crashed
 * in removeTransfer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class CancelMenuActionDialogRestoreTest {

  private static Transfer fakeTransfer() {
    return (Transfer)
        Proxy.newProxyInstance(
            Transfer.class.getClassLoader(),
            new Class<?>[] {Transfer.class},
            (proxy, method, args) -> {
              Class<?> type = method.getReturnType();
              if (type == boolean.class) return false;
              if (type == int.class) return 0;
              if (type == long.class) return 0L;
              if (type == double.class) return 0.0;
              return null;
            });
  }

  private static Fragment cancelDialogIn(FragmentActivity activity) {
    for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
      if (fragment instanceof CancelMenuAction.CancelMenuActionDialog && fragment.isAdded()) {
        return fragment;
      }
    }
    return null;
  }

  @Test
  public void aDialogRecreatedWithoutItsTransferIsNotShown() {
    ActivityController<FragmentActivity> controller =
        Robolectric.buildActivity(FragmentActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();

    CancelMenuAction.CancelMenuActionDialog dialog =
        CancelMenuAction.CancelMenuActionDialog.newInstance(fakeTransfer(), false, false, null);
    dialog.show(controller.get().getSupportFragmentManager());
    ShadowLooper.idleMainLooper();
    assertNotNull("the dialog shows for a live transfer", cancelDialogIn(controller.get()));

    controller.recreate();
    ShadowLooper.idleMainLooper();

    assertNull(
        "a re-created dialog has no transfer and must not stay up",
        cancelDialogIn(controller.get()));
  }

  @Test
  public void eachDialogKeepsItsOwnTransfer() throws Exception {
    Transfer first = fakeTransfer();
    Transfer second = fakeTransfer();
    CancelMenuAction.CancelMenuActionDialog a =
        CancelMenuAction.CancelMenuActionDialog.newInstance(first, false, false, null);
    CancelMenuAction.CancelMenuActionDialog.newInstance(second, false, false, null);
    java.lang.reflect.Field field =
        CancelMenuAction.CancelMenuActionDialog.class.getDeclaredField("transfer");
    field.setAccessible(true);
    assertSame("the first dialog still cancels the first transfer", first, field.get(a));
  }
}
