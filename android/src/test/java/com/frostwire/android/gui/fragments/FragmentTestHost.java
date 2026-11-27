/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.frostwire.android.R;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;

/**
 * Hosts one fragment in a themed activity, so fragment tests run with a plain Application and never
 * boot MainApplication.
 */
public final class FragmentTestHost extends AppCompatActivity {
  public static final int CONTAINER_ID = 0x7f0f1577;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.Theme_FrostWire);
    super.onCreate(savedInstanceState);
    FrameLayout container = new FrameLayout(this);
    container.setId(CONTAINER_ID);
    setContentView(
        container,
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  /** Starts a host and resumes {@code fragment} in it. */
  public static <F extends Fragment> ActivityController<FragmentTestHost> launch(F fragment) {
    installPlatform();
    ActivityController<FragmentTestHost> controller =
        Robolectric.buildActivity(FragmentTestHost.class).setup();
    controller
        .get()
        .getSupportFragmentManager()
        .beginTransaction()
        .add(CONTAINER_ID, fragment)
        .commitNow();
    return controller;
  }

  /** The Platform the app sets at startup: only the UI thread question has an answer. */
  public static void installPlatform() {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) -> {
                  if ("isUIThread".equals(method.getName())) {
                    return Looper.myLooper() == Looper.getMainLooper();
                  }
                  Class<?> type = method.getReturnType();
                  if (type == boolean.class) {
                    return false;
                  }
                  if (type == int.class || type == long.class) {
                    return 0;
                  }
                  return null;
                }));
  }

  /** Runs the main looper until {@code done}, other threads post their results to it. */
  public static void settleUntil(BooleanSupplier done) throws InterruptedException {
    for (int i = 0; i < 250 && !done.getAsBoolean(); i++) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(20);
    }
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }
}
