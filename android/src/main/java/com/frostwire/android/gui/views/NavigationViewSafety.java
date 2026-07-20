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

import android.os.DeadSystemException;
import android.view.ViewTreeObserver;

import com.frostwire.util.Logger;
import com.google.android.material.navigation.NavigationView;

import java.lang.reflect.Field;

/**
 * Installs a guarded wrapper around NavigationView's private inset-scrim
 * global-layout listener so DeadSystemException fails closed instead of
 * crashing the app during layout.
 */
public final class NavigationViewSafety {
    private static final Logger LOG = Logger.getLogger(NavigationViewSafety.class);

    private NavigationViewSafety() {
    }

    public static void installInsetListenerGuard(NavigationView navigationView) {
        if (navigationView == null) {
            return;
        }

        try {
            Field field = NavigationView.class.getDeclaredField("onGlobalLayoutListener");
            field.setAccessible(true);
            Object listenerObject = field.get(navigationView);
            if (!(listenerObject instanceof ViewTreeObserver.OnGlobalLayoutListener)
                    || listenerObject instanceof GuardedInsetLayoutListener) {
                return;
            }

            ViewTreeObserver.OnGlobalLayoutListener originalListener =
                    (ViewTreeObserver.OnGlobalLayoutListener) listenerObject;

            ViewTreeObserver viewTreeObserver = navigationView.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.removeOnGlobalLayoutListener(originalListener);
            }

            GuardedInsetLayoutListener guardedListener =
                    new GuardedInsetLayoutListener(navigationView, originalListener);

            field.set(navigationView, guardedListener);

            viewTreeObserver = navigationView.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(guardedListener);
            }
        } catch (Throwable t) {
            LOG.warn("Could not wrap NavigationView inset listener safely", t);
        }
    }

    private static final class GuardedInsetLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        private final NavigationView navigationView;
        private final ViewTreeObserver.OnGlobalLayoutListener originalListener;

        GuardedInsetLayoutListener(NavigationView navigationView,
                                   ViewTreeObserver.OnGlobalLayoutListener originalListener) {
            this.navigationView = navigationView;
            this.originalListener = originalListener;
        }

        @Override
        public void onGlobalLayout() {
            try {
                originalListener.onGlobalLayout();
            } catch (RuntimeException e) {
                if (containsDeadSystemException(e)) {
                    LOG.warn("Disabling NavigationView inset listener after DeadSystemException", e);
                    ViewTreeObserver currentObserver = navigationView.getViewTreeObserver();
                    if (currentObserver.isAlive()) {
                        currentObserver.removeOnGlobalLayoutListener(this);
                    }
                    return;
                }
                throw e;
            }
        }
    }

    private static boolean containsDeadSystemException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DeadSystemException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
