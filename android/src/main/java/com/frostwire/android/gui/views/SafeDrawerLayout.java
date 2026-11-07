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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.VisibleForTesting;
import androidx.drawerlayout.widget.DrawerLayout;
import com.frostwire.util.Logger;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A DrawerLayout that survives an exception from its own measure pass instead of crashing the app
 * (frostwire/frostwire#1384, "did not set the measured dimension").
 *
 * <p>It used to only log what it caught, which also hid any other bug that throws while the drawer
 * measures. It now records the first one per process as a Crashlytics non-fatal ({@code
 * DrawerMeasureFailed}), so whether this guard still catches anything shows up in Crashlytics, and
 * it can be removed once releases report none (frostwire/frostwire#1405).
 */
public class SafeDrawerLayout extends DrawerLayout {
  private static final Logger LOG = Logger.getLogger(SafeDrawerLayout.class);

  /** Only the first failure in a process is reported: a failing measure repeats every frame. */
  private static final AtomicBoolean REPORTED = new AtomicBoolean();

  private static volatile Consumer<RuntimeException> reporter =
      failure -> {
        try {
          FirebaseCrashlytics.getInstance().recordException(new DrawerMeasureFailed(failure));
        } catch (RuntimeException e) {
          LOG.warn("SafeDrawerLayout: could not record the measure failure", e);
        }
      };

  public SafeDrawerLayout(Context context) {
    super(context);
  }

  public SafeDrawerLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SafeDrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = View.getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    int height = View.getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);

    try {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } catch (RuntimeException e) {
      LOG.error("DrawerLayout.onMeasure() failed; falling back to the imposed measure specs", e);
      if (REPORTED.compareAndSet(false, true)) {
        reporter.accept(e);
      }
    } finally {
      // Android verifies after onMeasure() returns that this method called
      // setMeasuredDimension(). Do it explicitly even when DrawerLayout
      // returns without throwing on newer framework versions.
      setMeasuredDimension(width, height);
    }
  }

  @VisibleForTesting
  public static Consumer<RuntimeException> reporterForTesting() {
    return reporter;
  }

  @VisibleForTesting
  public static void setReporterForTesting(Consumer<RuntimeException> testReporter) {
    reporter = testReporter;
  }

  @VisibleForTesting
  public static void resetReported() {
    REPORTED.set(false);
  }

  /**
   * The non-fatal Crashlytics groups these failures under; its cause is what DrawerLayout threw.
   */
  static final class DrawerMeasureFailed extends Exception {
    DrawerMeasureFailed(Throwable cause) {
      super("DrawerLayout.onMeasure() threw; SafeDrawerLayout kept the app alive", cause);
    }
  }
}
