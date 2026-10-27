/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import org.junit.jupiter.api.Test;

class JdkWarmUpTest {

  @Test
  void distinctFontsKeepsOneFontPerFamilyAndStyleAndSkipsOtherValues() {
    Map<Object, Object> defaults = new LinkedHashMap<>();
    defaults.put("Label.font", new Font(Font.DIALOG, Font.PLAIN, 12));
    defaults.put("Table.font", new Font(Font.DIALOG, Font.PLAIN, 13)); // same data, other size
    defaults.put("TitledBorder.font", new Font(Font.DIALOG, Font.BOLD, 12));
    defaults.put("TextArea.font", new Font(Font.MONOSPACED, Font.PLAIN, 12));
    defaults.put("Label.foreground", Color.BLACK);
    defaults.put("Button.margin", "not a font");

    List<Font> fonts = JdkWarmUp.distinctFonts(defaults);

    assertEquals(3, fonts.size());
    assertTrue(fonts.stream().allMatch(f -> f.getSize() == 12));
  }

  @Test
  void distinctFontsOfEmptyDefaultsIsEmpty() {
    assertTrue(JdkWarmUp.distinctFonts(Map.of()).isEmpty());
  }

  @Test
  void everyClassToInitializeExistsAndLoads() {
    for (String className : JdkWarmUp.CLASSES_TO_INITIALIZE) {
      assertDoesNotThrow(() -> Class.forName(className), className);
    }
  }

  @Test
  void uiDelegateClassNamesTakesSkinClassesAndDerivesSynthDelegates() {
    Map<Object, Object> defaults = new LinkedHashMap<>();
    // The delegate whose first load stalled the EDT building the File menu (GitHub #1311).
    defaults.put("PopupMenuSeparatorUI", "com.frostwire.gui.theme.SkinPopupMenuSeparatorUI");
    defaults.put("ButtonUI", "javax.swing.plaf.synth.SynthLookAndFeel");
    defaults.put("Button.font", new Font(Font.DIALOG, Font.PLAIN, 12));
    defaults.put("Button.textForegroundUI", Color.BLACK);
    defaults.put("Button.contentMargins", "not a delegate");

    assertEquals(
        Set.of(
            "com.frostwire.gui.theme.SkinPopupMenuSeparatorUI",
            "javax.swing.plaf.synth.SynthButtonUI"),
        JdkWarmUp.uiDelegateClassNames(defaults));
  }

  @Test
  void nimbusDelegatesResolveToLoadableSynthClasses() {
    Set<String> classNames = JdkWarmUp.uiDelegateClassNames(new NimbusLookAndFeel().getDefaults());

    assertTrue(classNames.contains("javax.swing.plaf.synth.SynthButtonUI"), classNames::toString);
    assertTrue(classNames.contains("javax.swing.plaf.synth.SynthTableUI"), classNames::toString);
    long loadable = classNames.stream().filter(JdkWarmUpTest::isLoadable).count();
    // A handful of keys map to differently named delegates (e.g. separators); the rest must load.
    assertTrue(loadable >= classNames.size() - 5, loadable + " of " + classNames);
  }

  @Test
  void loadWithoutInitializingLoadsButSkipsStaticInitializersAndMissingClasses() {
    String fixture = UninitializedFixture.class.getName();
    List<String> requested = new ArrayList<>();
    ClassLoader recording =
        new ClassLoader(JdkWarmUpTest.class.getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            requested.add(name);
            return super.loadClass(name, resolve);
          }
        };

    assertDoesNotThrow(
        () -> JdkWarmUp.loadWithoutInitializing(List.of("no.such.DelegateUI", fixture), recording));

    assertEquals(List.of("no.such.DelegateUI", fixture), requested.subList(0, 2));
    // Static initializers may touch Swing state, so they must keep running on the EDT.
    assertFalse(FIXTURE_INITIALIZED.get());
  }

  private static final AtomicBoolean FIXTURE_INITIALIZED = new AtomicBoolean();

  private static final class UninitializedFixture {
    static {
      FIXTURE_INITIALIZED.set(true);
    }
  }

  private static boolean isLoadable(String className) {
    try {
      Class.forName(className, false, JdkWarmUpTest.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
