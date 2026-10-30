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
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.text.html.parser.DTD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkWarmUpTest {

  @Test
  void distinctFontsKeepsOneFontPerFamilyAndStyleAndSkipsOtherValues() {
    UIDefaults defaults = new UIDefaults();
    defaults.put("Label.font", new Font(Font.DIALOG, Font.PLAIN, 12));
    defaults.put("Table.font", new Font(Font.DIALOG, Font.PLAIN, 13)); // same data, other size
    defaults.put("TitledBorder.font", new Font(Font.DIALOG, Font.BOLD, 12));
    defaults.put("TextArea.font", new Font(Font.MONOSPACED, Font.PLAIN, 12));
    defaults.put("Label.foreground", Color.BLACK);
    defaults.put("Button.margin", "not a font");

    List<Font> fonts = JdkWarmUp.distinctFonts(defaults);

    assertEquals(
        Set.of("Dialog/0", "Dialog/1", "Monospaced/0"),
        fonts.stream().map(f -> f.getName() + "/" + f.getStyle()).collect(Collectors.toSet()));
    assertEquals(3, fonts.size());
  }

  @Test
  void distinctFontsOfEmptyDefaultsIsEmpty() {
    assertTrue(JdkWarmUp.distinctFonts(new UIDefaults()).isEmpty());
  }

  /**
   * The installed look and feel's own fonts must be warmed, not just the ones the app put in
   * UIManager. Warming only those left the composite "SansSerif" font every Nimbus label paints
   * with untouched, and the setup wizard's first HTML label initialized its fallback slots on the
   * EDT (Icebase #c9979532ec80, GitHub #1329).
   */
  @Test
  void distinctFontsOfInstalledLookAndFeelIncludeItsComponentFonts() throws Exception {
    LookAndFeel previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new NimbusLookAndFeel());
    try {
      UIManager.put("Table.font.bold", new FontUIResource(Font.SANS_SERIF, Font.BOLD, 12));
      Font label = UIManager.getFont("Label.font");

      List<Font> fonts = JdkWarmUp.distinctFonts(UIManager.getDefaults());

      assertTrue(
          fonts.stream()
              .anyMatch(f -> f.getName().equals(label.getName()) && f.getStyle() == Font.PLAIN),
          fonts::toString);
      assertTrue(fonts.stream().anyMatch(f -> f.getStyle() == Font.BOLD), fonts::toString);
    } finally {
      UIManager.put("Table.font.bold", null);
      UIManager.setLookAndFeel(previous);
    }
  }

  /**
   * MigLayout's static setup (PlatformDefaults → UnitValue → Introspector.getBeanInfo) ran on the
   * EDT when the main window built its first MigLayout (Icebase #45e2b9508e6d).
   */
  @Test
  void migLayoutDefaultsAreInitializedByTheWarmUp() {
    assertTrue(JdkWarmUp.CLASSES_TO_INITIALIZE.contains("net.miginfocom.layout.PlatformDefaults"));
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
  void everySplashPackageToLoadHasClasses() throws Exception {
    Path build = JdkWarmUp.codeSourceOf(JdkWarmUp.class);
    assertNotNull(build);
    for (String pkg : JdkWarmUp.SPLASH_PACKAGES_TO_LOAD) {
      assertFalse(JdkWarmUp.classNamesIn(build, List.of(pkg)).isEmpty(), pkg);
    }
  }

  /**
   * The splash window's progress bar paints with the skin's SkinProgressBarPainter, whose first
   * paint loaded ShapeGenerator from the jar on the EDT (Icebase #75b997df1b51, GitHub #1341).
   */
  @Test
  void splashClassWarmUpCoversTheSkinPainterClassesAndRunsBeforeTheSplashWait() throws Exception {
    List<String> classNames =
        JdkWarmUp.classNamesIn(
            JdkWarmUp.codeSourceOf(JdkWarmUp.class), JdkWarmUp.SPLASH_PACKAGES_TO_LOAD);
    for (String className :
        List.of(
            "com.frostwire.gui.theme.SkinProgressBarPainter",
            "com.frostwire.gui.theme.ShapeGenerator",
            "com.frostwire.gui.theme.ShapeGenerator$CornerStyle")) {
      assertTrue(classNames.contains(className), className);
      assertTrue(isLoadable(className), className);
    }

    String source = warmUpSource();
    int splashClasses = source.indexOf("SPLASH_PACKAGES_TO_LOAD");
    assertTrue(splashClasses >= 0, "splash classes not loaded by warmUp");
    assertTrue(
        splashClasses < source.indexOf("awaitSplashShown(SPLASH_SHOWN"),
        "splash classes wait for the splash they are needed by");
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
            // MainFrame.buildTabs (#5a45d8b5bd73) and the rest of MainFrame.<init>.
            "com.frostwire.gui.tabs.SearchTab",
            "com.frostwire.gui.tabs.LibraryTab",
            "com.frostwire.gui.tabs.AbstractTab",
            "com.limegroup.gnutella.gui.MainFrame",
            "com.limegroup.gnutella.gui.ApplicationHeader",
            "com.limegroup.gnutella.gui.StatusLine",
            "com.frostwire.gui.searchfield.GoogleSearchField",
            // First-run setup wizard.
            "com.limegroup.gnutella.gui.init.SetupManager",
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
            "com.frostwire.gui.library.LibraryFilesTableMediator",
            // First Options open (#ea3f1006d1d3): the dialog's listeners and every pane item
            // class the options tree names.
            "com.limegroup.gnutella.gui.options.OptionsConstructor$1",
            "com.limegroup.gnutella.gui.options.OptionsTreeManager",
            "com.limegroup.gnutella.gui.options.panes.AbstractPaneItem",
            "com.limegroup.gnutella.gui.options.panes.TorrentSaveFolderPaneItem",
            "com.limegroup.gnutella.gui.options.panes.SharedTorrentsDatabasePaneItem")) {
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
  void rasterizeGlyphsWorksWithAndWithoutDesktopHints() {
    Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
    Map<RenderingHints.Key, Object> lcd =
        Map.of(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

    assertDoesNotThrow(() -> JdkWarmUp.rasterizeGlyphs(font, null));
    assertDoesNotThrow(() -> JdkWarmUp.rasterizeGlyphs(font.deriveFont(Font.BOLD), lcd));
  }

  @Test
  void formatWarmUpsWorkForAnyLocale() {
    for (Locale locale : List.of(Locale.getDefault(), Locale.ENGLISH, Locale.JAPAN, Locale.ROOT)) {
      assertDoesNotThrow(() -> JdkWarmUp.warmUpNumberFormat(locale), locale::toString);
      assertDoesNotThrow(() -> JdkWarmUp.warmUpDateFormatAndCollation(locale), locale::toString);
    }
  }

  @Test
  void createPaintersBuildsOnlyThePrefixedLazyPaintersOnTheCallingThread() throws Exception {
    List<String> built = new ArrayList<>();
    List<Boolean> builtOnEdt = new ArrayList<>();
    UIDefaults defaults = new UIDefaults();
    for (String key :
        List.of(
            "ProgressBar[Enabled].foregroundPainter",
            "ProgressBar[Disabled].backgroundPainter",
            "Button[Enabled].backgroundPainter",
            "ProgressBar[Enabled].cycleTime")) {
      defaults.put(key, recordingLazyValue(key, built, builtOnEdt));
    }
    defaults.put("ProgressBar[Enabled+Finished].foregroundPainter", "not lazy");

    int[] created = new int[1];
    Thread warmUp =
        new Thread(
            () ->
                created[0] =
                    JdkWarmUp.createPainters(defaults, JdkWarmUp.SPLASH_PAINTER_KEY_PREFIX));
    warmUp.start();
    warmUp.join();

    assertEquals(2, created[0]);
    assertEquals(
        Set.of("ProgressBar[Enabled].foregroundPainter", "ProgressBar[Disabled].backgroundPainter"),
        Set.copyOf(built));
    assertEquals(List.of(false, false), builtOnEdt);
    // The table keeps its lazy values: the warm-up does not change what the EDT later resolves.
    assertTrue(
        Proxy.isProxyClass(
            defaults.entrySet().stream()
                .filter(e -> "ProgressBar[Enabled].foregroundPainter".equals(e.getKey()))
                .findFirst()
                .orElseThrow()
                .getValue()
                .getClass()));
  }

  /**
   * The first tooltip built Nimbus's ToolTip painters by reflection on the EDT (Icebase
   * #68ba98469e57, GitHub #1341).
   */
  @Test
  void warmUpCreatesNimbusToolTipPainters() throws Exception {
    assertTrue(
        JdkWarmUp.createPainters(
                new NimbusLookAndFeel().getDefaults(), JdkWarmUp.TOOLTIP_PAINTER_KEY_PREFIX)
            > 0);
    assertTrue(
        warmUpSource()
            .replaceAll("\\s+", "")
            .contains("createPainters(UIManager.getDefaults(),TOOLTIP_PAINTER_KEY_PREFIX)"),
        "not called by warmUp");
  }

  /**
   * The first search built Nimbus's Slider painters by reflection on the EDT, for the search
   * options panel's range sliders (Icebase #8a8cc233ad87, GitHub #1419). The same panel shows a
   * checkbox per search engine.
   */
  @Test
  void warmUpCreatesTheSearchOptionsPanelsNimbusPainters() throws Exception {
    javax.swing.UIDefaults nimbus = new NimbusLookAndFeel().getDefaults();
    for (String prefix : JdkWarmUp.SEARCH_OPTIONS_PAINTER_KEY_PREFIXES) {
      assertTrue(JdkWarmUp.createPainters(nimbus, prefix) > 0, prefix);
    }
    assertTrue(JdkWarmUp.SEARCH_OPTIONS_PAINTER_KEY_PREFIXES.contains("Slider"));
    assertTrue(JdkWarmUp.SEARCH_OPTIONS_PAINTER_KEY_PREFIXES.contains("CheckBox["));
    assertTrue(
        warmUpSource()
            .replaceAll("\\s+", "")
            .contains("createPainters(UIManager.getDefaults(),prefix)"),
        "not called by warmUp");
  }

  /**
   * The header's tab buttons paint with PlatformFonts.sansSerif(BOLD, 10), a strike of its own
   * whose glyphs the main window's first paint rasterized on the EDT (Icebase #a06500b144a5, GitHub
   * #1341).
   */
  @Test
  void warmUpRasterizesTheMainWindowPlatformFonts() throws Exception {
    List<Font> fonts = JdkWarmUp.mainWindowPlatformFonts();
    assertTrue(
        fonts.stream().anyMatch(f -> f.getStyle() == Font.BOLD && f.getSize() == 10),
        fonts::toString);
    assertTrue(
        warmUpSource().replaceAll("\\s+", "").contains("(Fontfont:mainWindowPlatformFonts())"),
        "not rasterized by warmUp");
  }

  /**
   * Search suggestions in the UI language and file names in any script are drawn from fallback slot
   * fonts, whose first glyph read the font file on the EDT (Icebase #61abd02df47d, #475a8918f3b8,
   * #81509d7f5ce6, GitHub #1346).
   */
  @Test
  void scriptGlyphSampleCoversTheScriptsDrawnFromFallbackFonts() {
    Set<Character.UnicodeScript> scripts = new HashSet<>();
    boolean latinExtended = false;
    for (int cp : JdkWarmUp.SCRIPT_GLYPH_SAMPLE.codePoints().toArray()) {
      if (Character.isLetter(cp)) {
        scripts.add(Character.UnicodeScript.of(cp));
        latinExtended |=
            cp >= 0x100 && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN;
      }
    }
    assertTrue(latinExtended, "no Latin letters past Latin-1");
    for (Character.UnicodeScript script :
        List.of(
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.ARABIC,
            Character.UnicodeScript.HEBREW,
            Character.UnicodeScript.DEVANAGARI,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HAN)) {
      assertTrue(scripts.contains(script), "missing " + script);
    }
    Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
    assertDoesNotThrow(() -> JdkWarmUp.rasterizeGlyphs(font, null, JdkWarmUp.SCRIPT_GLYPH_SAMPLE));
  }

  /**
   * Table cells paint with Table.font, its own strike when the user picked a table font size, and
   * the search suggestions on Windows with a plain 12 pt Dialog: both are warmed, besides the label
   * font, and each strike once.
   */
  @Test
  void textFontsAreTheLabelTheTableAndTheSuggestionsFonts() throws Exception {
    UIDefaults defaults = new UIDefaults();
    Font label = new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 13);
    defaults.put("Label.font", label);
    defaults.put("Table.font", new FontUIResource(label.deriveFont(16f)));
    Set<Font> fonts = JdkWarmUp.textFonts(defaults);
    assertTrue(fonts.contains(label), fonts::toString);
    assertTrue(fonts.stream().anyMatch(f -> f.getSize() == 16), fonts::toString);
    assertTrue(fonts.contains(new Font(Font.DIALOG, Font.PLAIN, 12)), fonts::toString);

    defaults.put("Table.font", label);
    assertEquals(2, JdkWarmUp.textFonts(defaults).size(), "the same strike twice");

    String warmUp = warmUpSource().replaceAll("\\s+", "");
    assertTrue(
        warmUp.contains("(Fontfont:textFonts(UIManager.getDefaults()))")
            && warmUp.contains("rasterizeGlyphs(font,desktopHints,SCRIPT_GLYPH_SAMPLE)"),
        "script glyphs not rasterized by warmUp");
  }

  /**
   * Clipping a table cell's complex text measured it with no font, so the JDK ran the bidi
   * algorithm and asked every installed font for the characters the default font lacks, reading
   * each font's character map from disk, on the EDT (Icebase #3e80aae69c6e, #624cbfa1c9da,
   * #09b427973a88, #14587f62871d, GitHub #1341). Fonts and the bidi data load once per JVM, so each
   * case runs in a fresh one.
   */
  @Test
  void warmUpTextLayoutReadsEveryFontsCharacterMapAndLoadsBidi() throws Exception {
    String before = runTextLayoutProbe(TextLayoutProbe.INITIALIZE_ONLY);
    String after = runTextLayoutProbe(TextLayoutProbe.LAYOUT);

    assertFalse(before.lines().anyMatch("unread=0"::equals), before);
    assertFalse(before.contains(BIDI_DATA_CLASS), "probe itself runs bidi");
    assertTrue(after.lines().anyMatch("unread=0"::equals), after);
    assertTrue(after.contains(BIDI_DATA_CLASS), "bidi data not loaded");
    assertTrue(warmUpSource().contains("warmUpTextLayout();"), "not called by warmUp");
  }

  private static final String BIDI_DATA_CLASS = "jdk.internal.icu.impl.UBiDiProps ";

  /**
   * The first multi-line tooltip wraps its text with a line BreakIterator, which loaded the JDK's
   * line-break rules on the EDT (Icebase #7c1eec7b21f2). They load once per JVM, so each case runs
   * in a fresh one.
   */
  @Test
  void warmUpLineBreaksLoadsTheLineBreakRules() throws Exception {
    String before = runLineBreakProbe(false);
    String after = runLineBreakProbe(true);
    assertFalse(before.contains(LINE_BREAK_RULES_CLASS), "loaded before the warm-up");
    assertTrue(after.contains(LINE_BREAK_RULES_CLASS), "line-break rules not loaded");
    assertTrue(warmUpSource().contains("warmUpLineBreaks();"), "not called by warmUp");
  }

  private static final String LINE_BREAK_RULES_CLASS = "sun.text.RuleBasedBreakIterator ";

  private static String runLineBreakProbe(boolean warmUp) throws IOException, InterruptedException {
    Process probe =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:class+load=info",
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                LineBreakProbe.class.getName(),
                String.valueOf(warmUp))
            .redirectErrorStream(true)
            .start();
    String log = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(probe.waitFor(120, TimeUnit.SECONDS), "probe timed out");
    assertEquals(0, probe.exitValue(), log);
    return log;
  }

  /** Runs in a fresh JVM: initializes JdkWarmUp and, if asked, runs the line-break warm-up. */
  static final class LineBreakProbe {
    public static void main(String[] args) throws Exception {
      Class.forName(JdkWarmUp.class.getName());
      if (Boolean.parseBoolean(args[0])) {
        JdkWarmUp.warmUpLineBreaks();
      }
    }
  }

  /**
   * The main window's first DropTarget looked up the JDK's data-transfer service on the EDT
   * (frostwire/frostwire#1370): a ServiceLoader scan that loads the provider class. It loads once
   * per JVM, so each case runs in a fresh one.
   */
  @Test
  void warmUpDataTransferLoadsTheDataTransferService() throws Exception {
    String before = runDataTransferProbe(false);
    String after = runDataTransferProbe(true);
    assertFalse(before.contains(DATA_TRANSFER_SERVICE_CLASS), "loaded before the warm-up");
    assertTrue(after.contains(DATA_TRANSFER_SERVICE_CLASS), "data-transfer service not loaded");
    assertTrue(warmUpSource().contains("warmUpDataTransfer();"), "not called by warmUp");
  }

  private static final String DATA_TRANSFER_SERVICE_CLASS =
      "sun.awt.datatransfer.DesktopDatatransferServiceImpl ";

  private static String runDataTransferProbe(boolean warmUp)
      throws IOException, InterruptedException {
    Process probe =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:class+load=info",
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                DataTransferProbe.class.getName(),
                String.valueOf(warmUp))
            .redirectErrorStream(true)
            .start();
    String log = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(probe.waitFor(120, TimeUnit.SECONDS), "probe timed out");
    assertEquals(0, probe.exitValue(), log);
    return log;
  }

  /** Runs in a fresh JVM: initializes JdkWarmUp and, if asked, runs the data-transfer warm-up. */
  static final class DataTransferProbe {
    public static void main(String[] args) throws Exception {
      Class.forName(JdkWarmUp.class.getName());
      if (Boolean.parseBoolean(args[0])) {
        JdkWarmUp.warmUpDataTransfer();
      }
    }
  }

  private static String runTextLayoutProbe(String mode) throws IOException, InterruptedException {
    Process probe =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:class+load=info",
                "-Djava.awt.headless=true",
                "--add-opens",
                "java.desktop/sun.font=ALL-UNNAMED",
                "-cp",
                System.getProperty("java.class.path"),
                TextLayoutProbe.class.getName(),
                mode)
            .redirectErrorStream(true)
            .start();
    String log = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(probe.waitFor(120, TimeUnit.SECONDS), "probe timed out");
    assertEquals(0, probe.exitValue(), log);
    return log;
  }

  /**
   * Runs in a fresh JVM and prints how many installed fonts have not read their character map yet.
   */
  static final class TextLayoutProbe {
    static final String INITIALIZE_ONLY = "initialize";
    static final String LAYOUT = "layout";

    public static void main(String[] args) throws Exception {
      Class.forName(JdkWarmUp.class.getName());
      if (LAYOUT.equals(args[0])) {
        JdkWarmUp.warmUpTextLayout();
      }
      Field mapper = Class.forName("sun.font.Font2D").getDeclaredField("mapper");
      mapper.setAccessible(true);
      Method font2D = Class.forName("sun.font.FontUtilities").getMethod("getFont2D", Font.class);
      int unread = 0;
      for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
        if (mapper.get(font2D.invoke(null, font)) == null) {
          unread++;
        }
      }
      System.out.println("unread=" + unread);
    }
  }

  @Test
  void nimbusHasProgressBarPaintersToCreate() {
    assertTrue(
        JdkWarmUp.createPainters(
                new NimbusLookAndFeel().getDefaults(), JdkWarmUp.SPLASH_PAINTER_KEY_PREFIX)
            > 0);
  }

  private static UIDefaults.LazyValue recordingLazyValue(
      String key, List<String> built, List<Boolean> builtOnEdt) {
    return (UIDefaults.LazyValue)
        Proxy.newProxyInstance(
            JdkWarmUpTest.class.getClassLoader(),
            new Class<?>[] {UIDefaults.LazyValue.class},
            (proxy, method, args) -> {
              if (method.getName().equals("createValue")) {
                built.add(key);
                builtOnEdt.add(EventQueue.isDispatchThread());
                return new Object();
              }
              return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> key;
              };
            });
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

  /**
   * The JVM's first string concatenation that needs a generated class loads the class-file API;
   * StatusLine.updateBandwidth paid that on the EDT (Icebase #ce4392e7bb9a, GitHub #1328). This JVM
   * linked its own concatenations long ago, so each case runs in a fresh one.
   */
  @Test
  void linkStringConcatGeneratesTheFirstConcatClassOfAFreshJvm() throws Exception {
    assertFalse(
        loadsConcatClassGenerator(ConcatProbe.INITIALIZE_ONLY), "probe itself concatenates");
    assertTrue(
        loadsConcatClassGenerator(ConcatProbe.LINK), "concatenation folded or not generated");
  }

  @Test
  void warmUpLinksStringConcat() throws Exception {
    String source =
        Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/frostwire/util/JdkWarmUp.java"));
    int warmUp = source.indexOf("private static void warmUp() {");
    int end = source.indexOf("\n  }\n", warmUp);

    assertTrue(warmUp >= 0 && end > warmUp, "warmUp() not found");
    assertTrue(source.substring(warmUp, end).contains("linkStringConcat("), "not called by warmUp");
  }

  /**
   * Bulk app class loading competed with the EDT building the splash on 2-CPU machines (GitHub
   * #1323), so it waits until the splash is showing.
   */
  @Test
  void awaitSplashShownBlocksUntilTheSplashIsShowing() throws Exception {
    CountDownLatch splashShown = new CountDownLatch(1);
    CountDownLatch returned = new CountDownLatch(1);
    Thread waiter =
        new Thread(
            () -> {
              try {
                JdkWarmUp.awaitSplashShown(splashShown, TimeUnit.MINUTES.toMillis(1));
                returned.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    waiter.start();

    assertFalse(returned.await(300, TimeUnit.MILLISECONDS), "returned before the splash showed");
    splashShown.countDown();
    assertTrue(returned.await(5, TimeUnit.SECONDS), "still waiting after the splash showed");
    waiter.join();
  }

  @Test
  void awaitSplashShownGivesUpWhenNoSplashShows() throws Exception {
    long start = System.nanoTime();
    JdkWarmUp.awaitSplashShown(new CountDownLatch(1), 50);
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 50);
  }

  @Test
  void warmUpLoadsAppClassesOnlyAfterTheSplashSteps() throws Exception {
    String source = warmUpSource();
    int painters = source.indexOf("createPainters(");
    int glyphs = source.indexOf("rasterizeGlyphs(");
    int await = source.indexOf("awaitSplashShown(SPLASH_SHOWN");
    int appClasses = source.indexOf("APP_PACKAGES_TO_LOAD");

    assertTrue(painters >= 0 && glyphs >= 0, "splash steps not found");
    assertTrue(await > painters && await > glyphs, "splash steps wait for the splash");
    assertTrue(appClasses > await, "app classes load before the splash shows");
  }

  @Test
  void switchSplashesReleasesTheWarmUpEvenIfTheSplashFails() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/limegroup/gnutella/gui/Initializer.java"))
            .replaceAll("\\s+", "");
    int switchSplashes = source.indexOf("privatevoidswitchSplashes(");
    int end = source.indexOf("privatevoid", switchSplashes + 1);
    String body = source.substring(switchSplashes, end);

    assertTrue(switchSplashes >= 0, "switchSplashes() not found");
    assertTrue(body.contains("}finally{") && body.contains("JdkWarmUp.splashShown();"), body);
    assertTrue(
        body.indexOf("JdkWarmUp.splashShown();") > body.indexOf("}finally{"),
        "not released in a finally block");
  }

  private static String warmUpSource() throws IOException {
    String source =
        Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/frostwire/util/JdkWarmUp.java"));
    int warmUp = source.indexOf("private static void warmUp() {");
    int end = source.indexOf("\n  }\n", warmUp);
    assertTrue(warmUp >= 0 && end > warmUp, "warmUp() not found");
    return source.substring(warmUp, end);
  }

  private static boolean loadsConcatClassGenerator(String mode)
      throws IOException, InterruptedException {
    Process probe =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:class+load=info",
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                ConcatProbe.class.getName(),
                mode)
            .redirectErrorStream(true)
            .start();
    String log = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(probe.waitFor(60, TimeUnit.SECONDS), "probe timed out");
    assertEquals(0, probe.exitValue(), log);
    return log.contains("java.lang.invoke.StringConcatFactory$InlineHiddenClassStrategy ");
  }

  /** Runs in a fresh JVM; must not concatenate strings itself. */
  static final class ConcatProbe {
    static final String INITIALIZE_ONLY = "initialize";
    static final String LINK = "link";

    public static void main(String[] args) throws Exception {
      Class.forName(JdkWarmUp.class.getName());
      if (LINK.equals(args[0])) {
        JdkWarmUp.linkStringConcat(args.length, args[0]);
      }
    }
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
