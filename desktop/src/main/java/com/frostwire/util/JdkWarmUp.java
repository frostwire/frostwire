/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import com.frostwire.gui.theme.PlatformFonts;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.text.Collator;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.IntStream;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Pays the JDK's one-time initialization costs on a background thread at startup, so the first
 * paint or click that needs them does not stall the EDT.
 *
 * <ul>
 *   <li>Painters: Nimbus builds the splash window's progress bar painters and the tooltip painters
 *       by reflection, see {@link #createPainters}.
 *   <li>Shaping: the first complex-text layout initializes HarfBuzz ({@code HBShaper.<clinit>},
 *       which builds a Foreign Function linker).
 *   <li>Fallback fonts: a composite UI font maps characters missing from its primary font through
 *       fallback "slot" fonts, and each slot reads its character map from disk the first time it is
 *       consulted (several MB for CJK fonts).
 *   <li>Font resolution and bidi: clipping complex text in a cell measures it with no font, so the
 *       JDK looks for a font per character among all installed fonts, see {@link
 *       #warmUpTextLayout}.
 *   <li>Class loading: building the main window creates a UI delegate per component type, and each
 *       delegate class (FrostWire's skin classes and their Synth/Basic superclasses) is read from
 *       the jar or the JDK's module image and defined the first time, on the EDT.
 *   <li>Slow static initializers of JDK classes first used while painting or clicking, see {@link
 *       #CLASSES_TO_INITIALIZE}.
 *   <li>Glyph rasterization: each glyph of a font strike is rasterized the first time it is
 *       measured or painted.
 *   <li>Locale data and tables behind the first number format, date format and collator.
 *   <li>String concatenation: the first {@code +} on strings that needs a generated class loads the
 *       class-file API, see {@link #linkStringConcat}.
 *   <li>FrostWire's own classes for the splash window's skin painters, see {@link
 *       #SPLASH_PACKAGES_TO_LOAD}, and for the setup wizard, the main window, the big panes built
 *       right after it and the Options dialog, see {@link #APP_PACKAGES_TO_LOAD}: loaded, not
 *       initialized, since their static initializers may create Swing objects.
 * </ul>
 *
 * Steps run in the order the EDT first needs them: the splash window's progress bar, text and
 * label, then the main window, then tables.
 *
 * <p>Call after the look and feel is installed, so the UI fonts are the ones the app will paint
 * with.
 */
public final class JdkWarmUp {
  private static final Logger LOG = Logger.getLogger(JdkWarmUp.class);

  /** Complex script text: layoutGlyphVector always goes through GlyphLayout and the shaper. */
  private static final String SHAPING_SAMPLE =
      "FrostWire " + new String(new int[] {0x0915, 0x094D, 0x0937}, 0, 3);

  /** Printable ASCII, the glyphs nearly every label measures and paints. */
  private static final String GLYPH_SAMPLE =
      IntStream.rangeClosed(' ', '~')
          .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
          .toString();

  private static final int GLYPH_IMAGE_WIDTH = 1024;

  /** Toolkit property with the text antialiasing hints the desktop uses for UI text. */
  private static final String DESKTOP_FONT_HINTS = "awt.font.desktophints";

  /** Unassigned code point no font maps, so the lookup walks (and initializes) every slot. */
  private static final int UNMAPPED_CODE_POINT = 0x0378;

  /**
   * Text that needs complex layout: Arabic and Hebrew (right to left, so the JDK runs its bidi
   * algorithm), Devanagari, Thai, CJK and Cyrillic, then {@link #UNMAPPED_CODE_POINT}, which no
   * font maps, so resolving its font consults every installed font.
   */
  static final String TEXT_LAYOUT_SAMPLE =
      "FrostWire \u0627\u0644\u0639\u0631\u0628\u064a\u0629 \u05e2\u05d1\u05e8\u05d9\u05ea"
          + " \u0915\u094d\u0937 \u0e44\u0e17\u0e22 \u4e2d\u6587 \u0420\u0443\u0441 "
          + new String(new int[] {UNMAPPED_CODE_POINT}, 0, 1);

  /**
   * Letters of scripts the UI fonts draw from fallback slot fonts: Latin Extended, Greek, Cyrillic,
   * Arabic, Hebrew, Devanagari, Thai, Hangul, kana and CJK. Search suggestions come in the UI
   * language and file names in any script; the first glyph of each slot font read that font's file
   * on the EDT (Icebase #61abd02df47d, #475a8918f3b8, #81509d7f5ce6, GitHub #1346).
   */
  static final String SCRIPT_GLYPH_SAMPLE =
      "\u0105\u010d\u0119\u0142\u0144\u0161\u017e\u0151\u0171 "
          + "\u1ea1\u1ec7 "
          + "\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac "
          + "\u0420\u0443\u0441\u0441\u043a\u0438\u0439 "
          + "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 "
          + "\u05e2\u05d1\u05e8\u05d9\u05ea "
          + "\u0939\u093f\u0928\u094d\u0926\u0940 \u0e44\u0e17\u0e22 "
          + "\ud55c\uad6d\uc5b4 \u3072\u3089\u304c\u306a "
          + "\u30ab\u30bf\u30ab\u30ca \u4e2d\u6587\u6f22\u5b57";

  /**
   * Classes whose static initializers are slow and would otherwise run on the EDT: DragSource
   * decodes the drag cursors (first mouse press in a drag-enabled table), SunGraphicsCallback
   * brings up java.util.logging through its PlatformLogger (first container paint), and MigLayout's
   * PlatformDefaults initializes UnitValue, which runs JavaBeans introspection
   * (Introspector.getBeanInfo) when the main window builds its first MigLayout (Icebase
   * #45e2b9508e6d).
   */
  static final List<String> CLASSES_TO_INITIALIZE =
      List.of(
          "java.awt.dnd.DragSource",
          "sun.awt.SunGraphicsCallback",
          "net.miginfocom.layout.PlatformDefaults");

  /**
   * FrostWire packages whose classes the splash window paints with, loaded before it shows like the
   * other splash steps: the skin's progress bar painter (SkinProgressBarPainter) builds its shape
   * with ShapeGenerator, which the splash's first paint read from the jar on the EDT.
   */
  static final List<String> SPLASH_PACKAGES_TO_LOAD = List.of("com.frostwire.gui.theme");

  /**
   * FrostWire packages whose classes the EDT reads from the jar and defines in bulk while it shows
   * the first-run setup wizard, builds the main window and, right after startup, the Transfers,
   * Search and Library panes, and then on the first Options click. Every class directly in each
   * package (subpackages are listed separately) is loaded without initialization, in this order,
   * which is the order the EDT first needs them.
   */
  static final List<String> APP_PACKAGES_TO_LOAD =
      List.of(
          // First-run setup wizard, shown before the main window: SetupManager and its pages.
          "com.limegroup.gnutella.gui.init",
          // Layout of every MigLayout container, starting with MainFrame's content pane.
          "net.miginfocom.layout",
          "net.miginfocom.swing",
          // MainFrame itself and what its constructor builds from this package: ApplicationHeader,
          // StatusLine and their listeners.
          "com.limegroup.gnutella.gui",
          // The tab objects MainFrame.buildTabs creates first (SearchTab, LibraryTab).
          "com.frostwire.gui.tabs",
          // ApplicationHeader's search box and its update button.
          "com.frostwire.gui.searchfield",
          "com.frostwire.gui.updates",
          // MainFrame's menu bar and the actions behind its items.
          "com.limegroup.gnutella.gui.menu",
          "com.limegroup.gnutella.gui.actions",
          // Table mediators, LimeJTable, renderers and editors shared by every table pane.
          "com.limegroup.gnutella.gui.tables",
          // Transfers tab, built just after the main window (BTDownloadMediator,
          // TransferActionsRenderer), and its detail panes.
          "com.frostwire.gui.bittorrent",
          "com.frostwire.gui.components.transfers",
          // Search tab: SearchResultMediator, SearchOptionsPanel, SearchEngine and the anonymous
          // class of each engine.
          "com.limegroup.gnutella.gui.search",
          // Library tab: LibraryMediator, its tree and its table mediators.
          "com.frostwire.gui.library",
          // Options dialog, built on the first click: OptionsConstructor and the tree manager,
          // and every pane item class the tree names (it reads their label constants).
          "com.limegroup.gnutella.gui.options",
          "com.limegroup.gnutella.gui.options.panes",
          // Drag and drop, built on the first drag out of a table: LimeTransferHandler, the drag
          // gesture listener and FileTransferable (Icebase #3315b9f6e6e7).
          "com.limegroup.gnutella.gui.dnd");

  /** Synth-based looks (Nimbus) map every delegate key to this class, which creates "Synth"+key. */
  private static final String SYNTH_LOOK_AND_FEEL = "javax.swing.plaf.synth.SynthLookAndFeel";

  private static final String SYNTH_UI_PREFIX = "javax.swing.plaf.synth.Synth";

  private static final String CLASS_SUFFIX = ".class";

  /** Painters of the splash window's progress bar, the first painted component Nimbus builds. */
  static final String SPLASH_PAINTER_KEY_PREFIX = "ProgressBar[";

  /** Painters of tooltips, built the first time the pointer rests on a component with one. */
  static final String TOOLTIP_PAINTER_KEY_PREFIX = "ToolTip[";

  /**
   * Painters of the search options panel, built on the first search: its range sliders (the slider,
   * its thumb and its track) and a checkbox per search engine.
   */
  static final List<String> SEARCH_OPTIONS_PAINTER_KEY_PREFIXES = List.of("Slider", "CheckBox[");

  private static final String PAINTER_KEY_SUFFIX = "Painter";

  /**
   * Upper bound on how long app class loading waits for the splash window. Startup without a splash
   * (system startup) still releases the wait, so this only matters if splash creation fails.
   */
  static final long SPLASH_WAIT_MS = 15_000;

  /** Released once the Swing splash window is showing, or when no splash will be shown. */
  private static final CountDownLatch SPLASH_SHOWN = new CountDownLatch(1);

  private JdkWarmUp() {}

  /**
   * Lets the warm-up thread move on to loading app classes. Until then it does only the steps the
   * splash window itself needs, so on 2-CPU machines bulk class loading does not compete with the
   * EDT building the splash.
   */
  public static void splashShown() {
    SPLASH_SHOWN.countDown();
  }

  public static void startInBackground() {
    Thread warmUp = new Thread(JdkWarmUp::warmUp, "JdkWarmUp");
    warmUp.setDaemon(true);
    warmUp.start();
  }

  private static void warmUp() {
    try {
      createPainters(UIManager.getDefaults(), SPLASH_PAINTER_KEY_PREFIX);
    } catch (Throwable t) {
      LOG.warn("Painter warm-up failed: " + t.getMessage(), t);
    }
    try {
      char[] text = SHAPING_SAMPLE.toCharArray();
      new Font(Font.DIALOG, Font.PLAIN, 12)
          .layoutGlyphVector(
              new FontRenderContext(null, true, true),
              text,
              0,
              text.length,
              Font.LAYOUT_LEFT_TO_RIGHT);
      for (Font font : distinctFonts(UIManager.getDefaults())) {
        font.canDisplay(UNMAPPED_CODE_POINT);
      }
    } catch (Throwable t) {
      // Best effort: the first on-EDT use pays these costs instead.
      LOG.warn("Text warm-up failed: " + t.getMessage(), t);
    }
    try {
      Font label = UIManager.getFont("Label.font");
      if (label != null) {
        Map<?, ?> desktopHints = desktopFontHints();
        rasterizeGlyphs(label, desktopHints);
        rasterizeGlyphs(label.deriveFont(Font.BOLD), desktopHints);
      }
    } catch (Throwable t) {
      LOG.warn("Glyph warm-up failed: " + t.getMessage(), t);
    }
    try {
      warmUpNumberFormat(formatLocale());
    } catch (Throwable t) {
      LOG.warn("Number format warm-up failed: " + t.getMessage(), t);
    }
    try {
      linkStringConcat(Runtime.getRuntime().availableProcessors(), GLYPH_SAMPLE);
    } catch (Throwable t) {
      LOG.warn("String concatenation warm-up failed: " + t.getMessage(), t);
    }
    try {
      loadWithoutInitializing(
          uiDelegateClassNames(UIManager.getDefaults()), JdkWarmUp.class.getClassLoader());
    } catch (Throwable t) {
      LOG.warn("UI delegate warm-up failed: " + t.getMessage(), t);
    }
    for (String className : CLASSES_TO_INITIALIZE) {
      try {
        Class.forName(className);
      } catch (Throwable t) {
        LOG.warn("Warm-up of " + className + " failed: " + t.getMessage(), t);
      }
    }
    try {
      warmUpDataTransfer();
    } catch (Throwable t) {
      LOG.warn("Data transfer warm-up failed: " + t.getMessage(), t);
    }
    Path location = codeSourceOf(JdkWarmUp.class);
    try {
      if (location != null) {
        loadWithoutInitializing(
            classNamesIn(location, SPLASH_PACKAGES_TO_LOAD), JdkWarmUp.class.getClassLoader());
      }
    } catch (Throwable t) {
      LOG.warn("Splash class warm-up failed: " + t.getMessage(), t);
    }
    try {
      awaitSplashShown(SPLASH_SHOWN, SPLASH_WAIT_MS);
      if (location != null) {
        loadWithoutInitializing(
            classNamesIn(location, APP_PACKAGES_TO_LOAD), JdkWarmUp.class.getClassLoader());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    } catch (Throwable t) {
      LOG.warn("App class warm-up failed: " + t.getMessage(), t);
    }
    try {
      Map<?, ?> desktopHints = desktopFontHints();
      for (Font font : mainWindowPlatformFonts()) {
        rasterizeGlyphs(font, desktopHints);
      }
    } catch (Throwable t) {
      LOG.warn("Main window glyph warm-up failed: " + t.getMessage(), t);
    }
    try {
      Map<?, ?> desktopHints = desktopFontHints();
      for (Font font : textFonts(UIManager.getDefaults())) {
        rasterizeGlyphs(font, desktopHints, GLYPH_SAMPLE);
        rasterizeGlyphs(font, desktopHints, SCRIPT_GLYPH_SAMPLE);
      }
    } catch (Throwable t) {
      LOG.warn("Script glyph warm-up failed: " + t.getMessage(), t);
    }
    try {
      warmUpDateFormatAndCollation(formatLocale(), Locale.getDefault());
    } catch (Throwable t) {
      LOG.warn("Date format and collation warm-up failed: " + t.getMessage(), t);
    }
    try {
      createPainters(UIManager.getDefaults(), TOOLTIP_PAINTER_KEY_PREFIX);
    } catch (Throwable t) {
      LOG.warn("Tooltip painter warm-up failed: " + t.getMessage(), t);
    }
    try {
      for (String prefix : SEARCH_OPTIONS_PAINTER_KEY_PREFIXES) {
        createPainters(UIManager.getDefaults(), prefix);
      }
    } catch (Throwable t) {
      LOG.warn("Search options painter warm-up failed: " + t.getMessage(), t);
    }
    try {
      warmUpLineBreaks();
    } catch (Throwable t) {
      LOG.warn("Line break warm-up failed: " + t.getMessage(), t);
    }
    try {
      warmUpTextLayout();
    } catch (Throwable t) {
      LOG.warn("Text layout warm-up failed: " + t.getMessage(), t);
    }
  }

  /** The desktop's text antialiasing hints, or null when the toolkit has none. */
  private static Map<?, ?> desktopFontHints() {
    Object hints = Toolkit.getDefaultToolkit().getDesktopProperty(DESKTOP_FONT_HINTS);
    return hints instanceof Map<?, ?> map ? map : null;
  }

  /**
   * The {@link PlatformFonts} the main window paints with as soon as it shows: the header's tab
   * buttons (ApplicationHeader) and the Transfers tab's filter field and buttons (TransfersTab).
   * Each family, style and size is its own strike, so their glyphs are not the label font's.
   */
  static List<Font> mainWindowPlatformFonts() {
    return List.of(PlatformFonts.sansSerif(Font.BOLD, 10), PlatformFonts.sansSerif(Font.PLAIN, 12));
  }

  /**
   * The fonts that show text in any script: labels, table cells (Table.font, a strike of its own
   * when the user picked a table font size) and the search suggestions on Windows (a plain 12 pt
   * Dialog, ThemeMediator.DIALOG_FONT).
   */
  static Set<Font> textFonts(UIDefaults defaults) {
    Set<Font> fonts = new LinkedHashSet<>();
    for (String key : new String[] {"Label.font", "Table.font"}) {
      Font font = defaults.getFont(key);
      if (font != null) {
        fonts.add(font);
      }
    }
    fonts.add(new Font(Font.DIALOG, Font.PLAIN, 12));
    return fonts;
  }

  /**
   * Clips {@link #TEXT_LAYOUT_SAMPLE} the way Swing clips a cell's complex text that does not fit
   * (SwingUtilities2.clipString): a LineBreakMeasurer over text with no font attribute. That runs
   * the bidi algorithm (loading the ICU bidi data) and resolves a font per character through
   * FontResolver, which asks every installed font in turn whether it can display a character the
   * default font lacks, reading each font's character map from disk the first time. A table cell
   * with such text paid that on the EDT.
   */
  static void warmUpTextLayout() {
    new LineBreakMeasurer(
            new AttributedString(TEXT_LAYOUT_SAMPLE).getIterator(),
            BreakIterator.getCharacterInstance(),
            new FontRenderContext(null, true, true))
        .nextOffset(50);
  }

  /**
   * Breaks {@link #GLYPH_SAMPLE} into lines the way a wrapped multi-line tooltip does
   * (WrappedPlainView through javax.swing.text.Utilities.getBreakLocation). The first line
   * BreakIterator loads the JDK's line-break rules (RuleBasedBreakIterator and its resource data),
   * which the character BreakIterator of {@link #warmUpTextLayout} does not; the first multi-line
   * tooltip paid that on the EDT (Icebase #7c1eec7b21f2).
   */
  /**
   * Looks up the JDK's data-transfer service and builds the default flavor map, as the main
   * window's first {@code new DropTarget(...)} does: the lookup is a ServiceLoader scan of the
   * module graph that loaded the provider from the boot class path on the EDT
   * (frostwire/frostwire#1370, Icebase #c412f1f06e67). The provider builds the map under its own
   * lock.
   */
  static void warmUpDataTransfer() {
    SystemFlavorMap.getDefaultFlavorMap();
  }

  static void warmUpLineBreaks() {
    BreakIterator lines = BreakIterator.getLineInstance();
    lines.setText(GLYPH_SAMPLE);
    while (lines.next() != BreakIterator.DONE) {
      // Walking the text applies the rules, not just loads them.
    }
  }

  /**
   * Returns once the splash is showing, or after the timeout so a splash that never shows cannot
   * keep the warm-up from finishing.
   */
  static void awaitSplashShown(CountDownLatch splashShown, long timeoutMs)
      throws InterruptedException {
    if (!splashShown.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      LOG.info("Splash not shown after " + timeoutMs + " ms, loading app classes anyway");
    }
  }

  /**
   * Measures and draws the printable ASCII characters in the font into an offscreen image, with the
   * desktop's text antialiasing hints so the glyph cache entries (one per font, size, style and
   * hints) are the ones labels use. The first measurement of each glyph rasterizes it (on Windows
   * through GDI), which the splash window and the setup wizard otherwise pay on the EDT.
   */
  static void rasterizeGlyphs(Font font, Map<?, ?> desktopHints) {
    rasterizeGlyphs(font, desktopHints, GLYPH_SAMPLE);
  }

  /** Measures and draws {@code text} in the font, see {@link #rasterizeGlyphs(Font, Map)}. */
  static void rasterizeGlyphs(Font font, Map<?, ?> desktopHints, String text) {
    BufferedImage image = new BufferedImage(GLYPH_IMAGE_WIDTH, 32, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      if (desktopHints != null) {
        g.addRenderingHints(desktopHints);
      }
      g.setFont(font);
      g.getFontMetrics().stringWidth(text);
      g.drawString(text, 0, 24);
    } finally {
      g.dispose();
    }
  }

  /** The jar or class directory a class was loaded from, or null when unknown. */
  static Path codeSourceOf(Class<?> type) {
    try {
      CodeSource source = type.getProtectionDomain().getCodeSource();
      return source == null || source.getLocation() == null
          ? null
          : Path.of(source.getLocation().toURI());
    } catch (URISyntaxException | RuntimeException e) {
      return null;
    }
  }

  /**
   * Names of the classes (nested and anonymous ones included) directly in each package, package by
   * package in the given order, found in a jar or a class directory.
   */
  static List<String> classNamesIn(Path location, List<String> packages) throws IOException {
    Map<String, List<String>> byPackage = new LinkedHashMap<>();
    for (String pkg : packages) {
      byPackage.put(pkg, new ArrayList<>());
    }
    if (Files.isDirectory(location)) {
      for (String pkg : packages) {
        Path dir = location.resolve(pkg.replace('.', '/'));
        if (Files.isDirectory(dir)) {
          try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + CLASS_SUFFIX)) {
            for (Path file : files) {
              addClassName(byPackage, pkg.replace('.', '/') + "/" + file.getFileName());
            }
          }
        }
      }
    } else {
      try (JarFile jar = new JarFile(location.toFile())) {
        for (JarEntry entry : Collections.list(jar.entries())) {
          addClassName(byPackage, entry.getName());
        }
      }
    }
    List<String> classNames = new ArrayList<>();
    for (List<String> names : byPackage.values()) {
      Collections.sort(names);
      classNames.addAll(names);
    }
    return classNames;
  }

  private static void addClassName(Map<String, List<String>> byPackage, String path) {
    int slash = path.lastIndexOf('/');
    if (slash < 0 || !path.endsWith(CLASS_SUFFIX) || path.contains("-")) {
      return; // default package, resources, module-info and package-info
    }
    List<String> names = byPackage.get(path.substring(0, slash).replace('/', '.'));
    if (names != null) {
      names.add(path.substring(0, path.length() - CLASS_SUFFIX.length()).replace('/', '.'));
    }
  }

  /**
   * The first NumberFormat in the JVM loads the locale provider and its number data. The splash
   * window's progress percentage and GUIUtils' size and speed formats need one right away.
   */
  /**
   * The locale of every formatter made without one ({@code new DecimalFormat()}, {@code
   * NumberFormat.getInstance()}, {@code new SimpleDateFormat(pattern)}): the FORMAT category, which
   * differs from {@link Locale#getDefault()} when the system's region formats differ from its
   * display language (English Windows with Spanish number formats, say). Warming the other locale
   * left the first transfer menu's DecimalFormat loading its number data on the EDT (Icebase
   * #83c5be368599).
   */
  static Locale formatLocale() {
    return Locale.getDefault(Locale.Category.FORMAT);
  }

  static void warmUpNumberFormat(Locale locale) {
    NumberFormat.getNumberInstance(locale).format(1234.5);
  }

  /**
   * Links one string concatenation of the shape {@code int + "constant" + Object}, the status
   * line's "3 @ 1.2 MB/s". javac compiles {@code +} on non-constant operands to an invokedynamic,
   * linked by StringConcatFactory the first time it runs. Any shape other than a plain join of
   * references gets a hidden class generated with the class-file API, and the first one in the JVM
   * loads and initializes that API, which stalled the EDT for seconds in
   * StatusLine.updateBandwidth. Later call sites only define one small class per new shape, and
   * this shape's class is cached. The operands are parameters so javac cannot fold the expression
   * into a constant.
   */
  static String linkStringConcat(int count, String text) {
    return count + " @ " + text;
  }

  /**
   * Formatters needed later, once tables show up: the first SimpleDateFormat loads the calendar and
   * date format data (date cells), and the first Collator builds the JDK's Unicode decomposition
   * tables ({@code ComposedCharIter.<clinit>}) before its own rule tables (sorting string columns).
   * Both are built lazily by FrostWire, on the EDT.
   */
  static void warmUpDateFormatAndCollation(Locale formatLocale, Locale collationLocale) {
    new SimpleDateFormat("EEE, MMM. d, yyyy h:mm a", formatLocale).format(new Date(0));
    Collator collator = Collator.getInstance(collationLocale);
    collator.setDecomposition(Collator.FULL_DECOMPOSITION);
    collator.compare("FrostWire", "frostwire");
  }

  /**
   * Loads Swing's HTML support by parsing a tiny document into a model: the HTML 3.2 DTD that
   * {@code ParserDelegator} reads and parses once per JVM, the default style sheet, and the
   * document, reader and parser classes. No component is created, so the startup thread calls this
   * instead of building a throwaway HTML label on the EDT, where the DTD parse stalled it.
   */
  public static void loadHtmlEngine() {
    try {
      HTMLEditorKit kit = new HTMLEditorKit();
      Document document = kit.createDefaultDocument();
      kit.read(new StringReader("<html>.</html>"), document, 0);
    } catch (Throwable t) {
      // Best effort: the first HTML label on the EDT pays this instead.
      LOG.warn("HTML engine warm-up failed: " + t.getMessage(), t);
    }
  }

  /**
   * Creates and discards the lazily built painters whose keys start with the prefix
   * ("ProgressBar[Enabled].foregroundPainter"). Nimbus constructs each painter by reflection when a
   * component's style first needs it, and the first such construction builds the painter class's
   * constructor accessor and its method handle adapters; the splash window's progress bar paid that
   * on the EDT. Painters are plain objects, and the table keeps its lazy values. Returns how many
   * painters were created.
   */
  static int createPainters(UIDefaults defaults, String keyPrefix) {
    int created = 0;
    for (Map.Entry<?, ?> entry : List.copyOf(defaults.entrySet())) {
      if (entry.getKey() instanceof String key
          && key.startsWith(keyPrefix)
          && key.endsWith(PAINTER_KEY_SUFFIX)
          && entry.getValue() instanceof UIDefaults.LazyValue painter) {
        painter.createValue(defaults);
        created++;
      }
    }
    return created;
  }

  /**
   * One font per family and style among the fonts of the defaults; sizes share the same font data.
   * Fonts are looked up by key, not read from {@code values()}: {@code UIManager.getDefaults()}
   * answers {@code values()} with only the fonts the app put there itself, not the look and feel's,
   * and look-and-feel fonts are often computed on lookup (Nimbus derives "Label.font" and the other
   * component fonts from "defaultFont"). Missing the look and feel's plain font left the fallback
   * slots of the font every label paints with to the first HTML label on the EDT.
   */
  static List<Font> distinctFonts(UIDefaults defaults) {
    Set<String> seen = new HashSet<>();
    List<Font> fonts = new ArrayList<>();
    for (Object key : List.copyOf(defaults.keySet())) {
      if (key instanceof String name && name.toLowerCase(Locale.ROOT).contains("font")) {
        Font font = defaults.getFont(name);
        if (font != null && seen.add(font.getFontName() + "/" + font.getStyle())) {
          fonts.add(font);
        }
      }
    }
    return fonts;
  }

  /**
   * Class names of the look and feel's component UI delegates, the String values of its "*UI" keys
   * ("PopupMenuSeparatorUI" to "com.frostwire.gui.theme.SkinPopupMenuSeparatorUI"). Keys a
   * Synth-based look maps to {@code SynthLookAndFeel} yield the Synth delegate it creates for them
   * ("ButtonUI" to "javax.swing.plaf.synth.SynthButtonUI"); the few it names differently are
   * skipped when loaded.
   */
  static Set<String> uiDelegateClassNames(Map<?, ?> defaults) {
    Set<String> classNames = new LinkedHashSet<>();
    for (Map.Entry<?, ?> entry : List.copyOf(defaults.entrySet())) {
      if (entry.getKey() instanceof String key
          && key.endsWith("UI")
          && entry.getValue() instanceof String className) {
        classNames.add(SYNTH_LOOK_AND_FEEL.equals(className) ? SYNTH_UI_PREFIX + key : className);
      }
    }
    return classNames;
  }

  /**
   * Loads each class (and so its superclasses and interfaces) without running static initializers,
   * which stay on the thread that first uses the class. Missing classes are skipped.
   */
  static void loadWithoutInitializing(Collection<String> classNames, ClassLoader loader) {
    for (String className : classNames) {
      try {
        Class.forName(className, false, loader);
      } catch (ClassNotFoundException | LinkageError e) {
        // Not on this look and feel or platform; the EDT loads (or fails) it as it would have.
      }
    }
  }

  /**
   * Loads, without initializing, every class of the given packages from the jar or class directory
   * {@code anchor} comes from. For a look and feel switched to later, whose UI delegate classes
   * {@code updateComponentTreeUI} would otherwise read from the jar on the EDT.
   */
  public static void loadPackagesOf(Class<?> anchor, List<String> packages) throws IOException {
    Path location = codeSourceOf(anchor);
    if (location != null) {
      loadWithoutInitializing(classNamesIn(location, packages), anchor.getClassLoader());
    }
  }
}
