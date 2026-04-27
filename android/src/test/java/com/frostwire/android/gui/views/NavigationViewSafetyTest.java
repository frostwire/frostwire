/*
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import android.os.DeadSystemException;
import android.view.ViewTreeObserver;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import com.frostwire.android.R;
import com.google.android.material.navigation.NavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class NavigationViewSafetyTest {

    @Test
    public void installInsetListenerGuard_wrapsAndSwallowsDeadSystemException() throws Exception {
        NavigationView navigationView = new NavigationView(themedContext());
        AtomicBoolean invoked = new AtomicBoolean(false);
        ViewTreeObserver.OnGlobalLayoutListener original = () -> {
            invoked.set(true);
            throw new RuntimeException(new DeadSystemException());
        };

        Field field = setListenerField(navigationView, original);

        NavigationViewSafety.installInsetListenerGuard(navigationView);

        ViewTreeObserver.OnGlobalLayoutListener guarded =
                (ViewTreeObserver.OnGlobalLayoutListener) field.get(navigationView);

        assertNotSame(original, guarded);
        guarded.onGlobalLayout();
        assertTrue(invoked.get());
    }

    @Test
    public void installInsetListenerGuard_rethrowsNonDeadSystemRuntimeException() throws Exception {
        NavigationView navigationView = new NavigationView(themedContext());
        IllegalStateException expected = new IllegalStateException("boom");
        ViewTreeObserver.OnGlobalLayoutListener original = () -> {
            throw expected;
        };

        Field field = setListenerField(navigationView, original);

        NavigationViewSafety.installInsetListenerGuard(navigationView);

        ViewTreeObserver.OnGlobalLayoutListener guarded =
                (ViewTreeObserver.OnGlobalLayoutListener) field.get(navigationView);

        try {
            guarded.onGlobalLayout();
            fail("Expected non-DeadSystemException runtime failure to be rethrown");
        } catch (IllegalStateException actual) {
            assertSame(expected, actual);
        }
    }

    private static Field setListenerField(
            NavigationView navigationView,
            ViewTreeObserver.OnGlobalLayoutListener listener) throws Exception {
        Field field = NavigationView.class.getDeclaredField("onGlobalLayoutListener");
        field.setAccessible(true);

        Object existing = field.get(navigationView);
        if (existing instanceof ViewTreeObserver.OnGlobalLayoutListener) {
            ViewTreeObserver viewTreeObserver = navigationView.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.removeOnGlobalLayoutListener(
                        (ViewTreeObserver.OnGlobalLayoutListener) existing);
            }
        }

        field.set(navigationView, listener);
        ViewTreeObserver viewTreeObserver = navigationView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(listener);
        }
        return field;
    }

    private static ContextThemeWrapper themedContext() {
        return new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.Theme_FrostWire);
    }
}
