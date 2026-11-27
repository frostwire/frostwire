/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.util.picasso.NetworkBroadcastReceiver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

@SuppressWarnings("deprecation")
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class SafeContextWrapperTest {

  private static final String CONNECTIVITY = "android.net.conn.CONNECTIVITY_CHANGE";

  private final Context base = ApplicationProvider.getApplicationContext();
  private final SafeContextWrapper wrapper = new SafeContextWrapper(base);
  private final Handler handler = new Handler(Looper.getMainLooper());

  private boolean isRegistered(BroadcastReceiver receiver) {
    for (ShadowApplication.Wrapper w :
        Shadows.shadowOf((Application) base).getRegisteredReceivers()) {
      if (w.getBroadcastReceiver() == receiver) {
        return true;
      }
    }
    return false;
  }

  private static BroadcastReceiver plainReceiver() {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {}
    };
  }

  @Test
  public void picassoConnectivityRegistrationIsBlockedInEveryOverload() {
    BroadcastReceiver picasso = new NetworkBroadcastReceiver();
    IntentFilter filter = new IntentFilter(CONNECTIVITY);
    assertNull(wrapper.registerReceiver(picasso, filter));
    assertNull(wrapper.registerReceiver(picasso, filter, Context.RECEIVER_EXPORTED));
    assertNull(wrapper.registerReceiver(picasso, filter, null, handler));
    assertNull(wrapper.registerReceiver(picasso, filter, null, handler, Context.RECEIVER_EXPORTED));
    assertFalse(isRegistered(picasso));
  }

  @Test
  public void picassoReceiverForOtherActionsIsAllowed() {
    BroadcastReceiver picasso = new NetworkBroadcastReceiver();
    IntentFilter filter = new IntentFilter("some.other.ACTION");
    wrapper.registerReceiver(picasso, filter, Context.RECEIVER_EXPORTED);
    assertTrue(isRegistered(picasso));
  }

  @Test
  public void otherReceiversAreRegisteredNormally() {
    BroadcastReceiver receiver = plainReceiver();
    IntentFilter filter = new IntentFilter(CONNECTIVITY);
    wrapper.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
    assertTrue(isRegistered(receiver));

    BroadcastReceiver second = plainReceiver();
    wrapper.registerReceiver(second, filter, null, handler, Context.RECEIVER_EXPORTED);
    assertTrue(isRegistered(second));
  }
}
