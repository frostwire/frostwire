/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;

/** What the view tests need to run with a plain Application instead of MainApplication. */
public final class ViewTestSupport {
  private ViewTestSupport() {}

  /** Platform whose only working call is isUIThread, which the UI code asserts on. */
  public static void installUiThreadPlatform() {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
  }

  /** The preferences singleton, created once per JVM no matter how many tests ask for it. */
  public static ConfigurationManager configuration() {
    try {
      ConfigurationManager.create(ApplicationProvider.getApplicationContext());
    } catch (RuntimeException alreadyCreated) {
      // created by an earlier test
    }
    return ConfigurationManager.instance();
  }

  /** The app's theme over the test application, so styled attributes resolve. */
  public static Context themedContext() {
    return new ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(), R.style.Theme_FrostWire);
  }
}
