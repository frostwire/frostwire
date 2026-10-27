/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
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
 *   <li>Painters: Nimbus builds the splash window's progress bar painters by reflection, see {@link
 *       #createPainters}.
 *   <li>Shaping: the first complex-text layout initializes HarfBuzz ({@code HBShaper.<clinit>},
 *       which builds a Foreign Function linker).
 *   <li>Fallback fonts: a composite UI font maps characters missing from its primary font through
 *       fallback "slot" fonts, and each slot reads its character map from disk the first time it is
 *       consulted (several MB for CJK fonts).
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
   * JDK classes whose static initializers are slow and would otherwise run on the EDT: DragSource
   * decodes the drag cursors (first mouse press in a drag-enabled table), SunGraphicsCallback
   * brings up java.util.logging through its PlatformLogger (first container paint).
   */
  static final List<String> CLASSES_TO_INITIALIZE =
      List.of("java.awt.dnd.DragSource", "sun.awt.SunGraphicsCallback");

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
          "com.limegroup.gnutella.gui.options.panes");

  /** Synth-based looks (Nimbus) map every delegate key to this class, which creates "Synth"+key. */
  private static final String SYNTH_LOOK_AND_FEEL = "javax.swing.plaf.synth.SynthLookAndFeel";

  private static final String SYNTH_UI_PREFIX = "javax.swing.plaf.synth.Synth";

  private static final String CLASS_SUFFIX = ".class";

  /** Painters of the splash window's progress bar, the first painted component Nimbus builds. */
  static final String SPLASH_PAINTER_KEY_PREFIX = "ProgressBar[";

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
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty(DESKTOP_FONT_HINTS);
        Map<?, ?> desktopHints = hints instanceof Map<?, ?> map ? map : null;
        rasterizeGlyphs(label, desktopHints);
        rasterizeGlyphs(label.deriveFont(Font.BOLD), desktopHints);
      }
    } catch (Throwable t) {
      LOG.warn("Glyph warm-up failed: " + t.getMessage(), t);
    }
    try {
      warmUpNumberFormat(Locale.getDefault());
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
      warmUpDateFormatAndCollation(Locale.getDefault());
    } catch (Throwable t) {
      LOG.warn("Date format and collation warm-up failed: " + t.getMessage(), t);
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
    BufferedImage image = new BufferedImage(GLYPH_IMAGE_WIDTH, 32, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      if (desktopHints != null) {
        g.addRenderingHints(desktopHints);
      }
      g.setFont(font);
      g.getFontMetrics().stringWidth(GLYPH_SAMPLE);
      g.drawString(GLYPH_SAMPLE, 0, 24);
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
  static void warmUpDateFormatAndCollation(Locale locale) {
    new SimpleDateFormat("EEE, MMM. d, yyyy h:mm a", locale).format(new Date(0));
    Collator collator = Collator.getInstance(locale);
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
}
