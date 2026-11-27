/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.lang.ref.WeakReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Debug.hasContext is the leak detector used to keep Contexts out of long-lived objects. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class DebugTest {

  private static final class Holder {
    Object child;
    String name = "holder";
    int number = 3;
  }

  private static final class ContextHolder {
    Context context;
  }

  private static final class SelfReferencing {
    SelfReferencing self = this;
    Object other;
  }

  private static final class WeakHolder {
    WeakReference<Context> context;
  }

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void debugBuildsAreEnabled() {
    assertTrue(Debug.isEnabled());
  }

  @Test
  public void nullAndPlainObjectsHaveNoContext() {
    assertFalse(Debug.hasContext(null));
    assertFalse(Debug.hasContext("a string"));
    assertFalse(Debug.hasContext(42));
    assertFalse(Debug.hasContext(new Holder()));
  }

  @Test
  public void directContextFieldIsDetected() {
    ContextHolder holder = new ContextHolder();
    holder.context = new android.content.ContextWrapper(context);
    assertTrue(Debug.hasContext(holder));
  }

  @Test
  public void contextInNestedObjectIsDetected() {
    ContextHolder inner = new ContextHolder();
    inner.context = new android.content.ContextWrapper(context);
    Holder outer = new Holder();
    outer.child = new Holder();
    ((Holder) outer.child).child = inner;
    assertTrue(Debug.hasContext(outer));
  }

  @Test
  public void applicationAndWeakReferencesAreNotLeaks() {
    ContextHolder holder = new ContextHolder();
    holder.context = (Application) context;
    assertFalse(Debug.hasContext(holder));

    WeakHolder weak = new WeakHolder();
    weak.context = new WeakReference<>(new android.content.ContextWrapper(context));
    assertFalse(Debug.hasContext(weak));
  }

  @Test
  public void selfReferenceDoesNotRecurseForever() {
    SelfReferencing a = new SelfReferencing();
    assertFalse(Debug.hasContext(a));
    a.other = new Holder();
    assertFalse(Debug.hasContext(a));
  }

  @Test
  public void tooDeepObjectGraphIsReportedWithTheClassName() {
    Holder root = new Holder();
    Holder current = root;
    for (int i = 0; i < 300; i++) {
      Holder next = new Holder();
      current.child = next;
      current = next;
    }
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> Debug.hasContext(root));
    assertTrue(e.getMessage().contains("Too much recursion"));
    assertTrue(e.getMessage().contains(Holder.class.getName()));
  }
}
