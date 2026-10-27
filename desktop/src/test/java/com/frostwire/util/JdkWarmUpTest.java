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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.text.html.parser.DTD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

  @Test
  void everyAppPackageToLoadHasClasses() throws Exception {
    Path build = JdkWarmUp.codeSourceOf(JdkWarmUp.class);
    assertNotNull(build);
    for (String pkg : JdkWarmUp.APP_PACKAGES_TO_LOAD) {
      // A renamed or moved package would otherwise silently drop out of the warm-up.
      assertFalse(JdkWarmUp.classNamesIn(build, List.of(pkg)).isEmpty(), pkg);
    }
  }

  @Test
  void appClassWarmUpCoversTheClassesTheEdtLoadedInIcebaseReports() throws Exception {
    List<String> classNames =
        JdkWarmUp.classNamesIn(
            JdkWarmUp.codeSourceOf(JdkWarmUp.class), JdkWarmUp.APP_PACKAGES_TO_LOAD);

    for (String className :
        List.of(
            "net.miginfocom.swing.MigLayout", // MainFrame.<init> (#311ada154787)
            "net.miginfocom.layout.UnitValue",
            "com.limegroup.gnutella.gui.menu.FileMenu", // MainFrame menus (#b5afbd896e23)
            "com.limegroup.gnutella.gui.menu.AbstractMenu", // (#067474346501)
            "com.limegroup.gnutella.gui.tables.LimeJTable", // (#e84c15a648fd)
            "com.limegroup.gnutella.gui.tables.AbstractTableMediator",
            "com.frostwire.gui.bittorrent.BTDownloadMediator", // (#3b22ab60fc43)
            "com.frostwire.gui.bittorrent.TransferActionsRenderer",
            "com.limegroup.gnutella.gui.search.SearchEngine", // (#48a6b825e96e)
            "com.limegroup.gnutella.gui.search.SearchEngine$1", // one class per engine
            "com.limegroup.gnutella.gui.search.SearchOptionsPanel",
            "com.limegroup.gnutella.gui.search.SearchResultMediator", // (#bcd3f2f6fee1)
            "com.frostwire.gui.library.LibraryMediator", // (#9ea38cf3577b)
            "com.frostwire.gui.library.LibraryFilesTableMediator")) {
      assertTrue(classNames.contains(className), className);
      assertTrue(isLoadable(className), className);
    }
  }

  @Test
  void classNamesInAJarFollowThePackageOrderAndSkipSubpackagesAndResources(@TempDir Path dir)
      throws Exception {
    Path jar = dir.resolve("app.jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
      for (String entry :
          List.of(
              "a/",
              "a/A.class",
              "a/A$1.class",
              "a/sub/Deep.class",
              "a/icon.png",
              "a/package-info.class",
              "b/B.class",
              "Top.class",
              "module-info.class")) {
        out.putNextEntry(new JarEntry(entry));
        out.closeEntry();
      }
    }

    assertEquals(List.of("b.B", "a.A", "a.A$1"), JdkWarmUp.classNamesIn(jar, List.of("b", "a")));
  }

  @Test
  void classNamesInAClassDirectoryFollowThePackageOrderAndSkipSubpackages(@TempDir Path dir)
      throws Exception {
    for (String file : List.of("a/A.class", "a/A$Inner.class", "a/sub/Deep.class", "b/B.class")) {
      Files.createDirectories(dir.resolve(file).getParent());
      Files.createFile(dir.resolve(file));
    }

    assertEquals(
        List.of("b.B", "a.A", "a.A$Inner"), JdkWarmUp.classNamesIn(dir, List.of("b", "a", "c")));
  }

  @Test
  void formatWarmUpsWorkForAnyLocale() {
    for (Locale locale : List.of(Locale.getDefault(), Locale.ENGLISH, Locale.JAPAN, Locale.ROOT)) {
      assertDoesNotThrow(() -> JdkWarmUp.warmUpNumberFormat(locale), locale::toString);
      assertDoesNotThrow(() -> JdkWarmUp.warmUpDateFormatAndCollation(locale), locale::toString);
    }
  }

  @Test
  void loadHtmlEngineParsesTheDtdOffTheEdt() throws Exception {
    AtomicBoolean onEdt = new AtomicBoolean(true);
    Thread startup =
        new Thread(
            () -> {
              onEdt.set(EventQueue.isDispatchThread());
              JdkWarmUp.loadHtmlEngine();
            });
    startup.start();
    startup.join();

    assertFalse(onEdt.get());
    // ParserDelegator fills the shared "html32" DTD once per JVM; later HTML labels reuse it.
    assertTrue(DTD.getDTD("html32").elementHash.containsKey("body"));
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
