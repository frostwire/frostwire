/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import com.formdev.flatlaf.FlatLaf;
import com.frostwire.gui.theme.PlatformFonts;
import com.frostwire.gui.theme.SkinMenu;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorTable;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.geom.AffineTransform;
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
import java.text.Normalizer;
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
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.IntStream;
import javax.swing.ActionMap;
import javax.swing.JPopupMenu;
import javax.swing.LookAndFeel;
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
 *       consulted (several MB for CJK fonts), under the JDK's global font lock. Before the splash
 *       only the slots of the first windows' text load ({@link #initializeSlotsFor}); every other
 *       slot loads once the main window is showing, at low priority ({@link
 *       #startFallbackFontSweep}).
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

  /** Printable ASCII, the glyphs nearly every label measures and paints. */
  static final String GLYPH_SAMPLE =
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
          // DragSource.startDrag creates the first DragSourceContext, whose static initializer
          // ran on the EDT as the first drag from a table began (Icebase #e1c372ba0f4b).
          "java.awt.dnd.DragSourceContext",
          "sun.awt.SunGraphicsCallback",
          "net.miginfocom.layout.PlatformDefaults",
          // The first component added to a MigLayout container initializes its wrapper, whose
          // static initializer looks methods up by reflection (Class.getDeclaredMethod). It ran
          // on the EDT in ApplicationHeader's constructor: the package is loaded in the
          // background, but loading does not initialize (Icebase #f0a59fd34e60).
          "net.miginfocom.swing.SwingComponentWrapper",
          // ToolTipManager.showTipWindow initialized it on the EDT for the first tooltip (Icebase
          // #5749af9dee1c).
          "javax.swing.PopupFactory",
          // The dark theme brightens icons with a RescaleOp, whose first use loads the native
          // mlib_image library here; it did so on the EDT as the Library opened (Icebase
          // #7150ddfe3582, frostwire/frostwire#1475).
          "sun.awt.image.ImagingLib",
          // The main window's first text field (the File menu's Open Magnet field) ran its static
          // initializer on the EDT: a text cursor, DefaultEditorKit and its ~50 editor actions, and
          // the text TransferHandler, none of them a component (Icebase #a105a117d60a,
          // frostwire/frostwire#1480).
          "javax.swing.plaf.basic.BasicTextUI",
          // The same text field's highlighter: its first instance loads DefaultHighlighter and
          // its nested SafeDamager on the EDT (Icebase #aeedf97b8f8a, frostwire/frostwire#1480).
          "javax.swing.text.DefaultHighlighter",
          // The search field's suggestions list is the main window's first JList: its UI's static
          // initializer ran on the EDT as the first suggestions arrived (Icebase #c0e323953897,
          // frostwire/frostwire#1501).
          "javax.swing.plaf.basic.BasicListUI",
          // The setup wizard's MultiLineLabel is the first JTextArea: setLineWrap(true) made its UI
          // build a WrappedPlainView, both initialized on the EDT as the wizard opened (Icebase
          // #394182e705b5, frostwire/frostwire#1505).
          "javax.swing.plaf.basic.BasicTextAreaUI",
          "javax.swing.text.WrappedPlainView",
          // The Library's LibraryIconTree is the main window's first JTree: its UI and default cell
          // renderer (SynthTreeUI's nested SynthTreeCellRenderer, a DefaultTreeCellRenderer) were
          // initialized on the EDT (Icebase #8e40e9608fb0, #e20e6e4fca6d,
          // frostwire/frostwire#1506).
          "javax.swing.plaf.basic.BasicTreeUI",
          "javax.swing.plaf.synth.SynthTreeUI",
          "javax.swing.tree.DefaultTreeCellRenderer",
          // The status bar is the first user of GridBagLayout: StatusLine's constructor builds its
          // panel with one and its cells with GridBagConstraints, inside MainFrame's constructor
          // on the EDT. The JDK read them from its module image there on a 1-CPU computer, 5
          // minutes after the warm-up had finished (Icebase #5309675dbc2c,
          // frostwire/frostwire#1538).
          "java.awt.GridBagLayout",
          "java.awt.GridBagConstraints");

  /**
   * Classes initialized like {@link #CLASSES_TO_INITIALIZE}, but only once the splash is showing,
   * so that on 1- and 2-CPU machines they don't compete with the EDT building it
   * (frostwire/frostwire#1534). BasicTableUI's static initializer builds the TransferHandler every
   * table shares; it ran on the EDT in LimeJTable's constructor as the Library's file table was
   * built, on a 4-CPU machine with the warm-up finished (Icebase #750e1c9c3dc7,
   * frostwire/frostwire#1563). TableSettings' static initializer reads the tables' settings file
   * from disk; the first table built, whichever it is, ran it on the EDT (Icebase #1811854a508b,
   * frostwire/frostwire#1577). ShapeSpanIterator's static initializer is a native field-ID lookup
   * that ran on the EDT the first time a skin painted a shape (Icebase #a8c1a29e4560,
   * frostwire/frostwire#1563).
   */
  static final List<String> MAIN_WINDOW_CLASSES_TO_INITIALIZE =
      List.of(
          "javax.swing.plaf.basic.BasicTableUI",
          "com.limegroup.gnutella.gui.tables.TableSettings",
          "sun.java2d.pipe.ShapeSpanIterator",
          // First table header, slider, checkbox, panel and option pane UIs: their static
          // initializers and nested handlers ran on the EDT as LimeJTable, the range sliders and
          // the first skin-painted components were built (Icebase #038ff4bbd94a, #5ee5baad9fef,
          // #c138dc2807da, #0fe9d0c88973, #5c9893e5c9b2, frostwire/frostwire#1563).
          "javax.swing.plaf.basic.BasicTableHeaderUI",
          "javax.swing.plaf.synth.SynthTableUI",
          "javax.swing.plaf.basic.BasicSliderUI",
          "javax.swing.plaf.synth.SynthSliderUI",
          "javax.swing.plaf.synth.SynthCheckBoxUI",
          "javax.swing.plaf.synth.SynthPanelUI",
          "javax.swing.plaf.synth.SynthOptionPaneUI",
          // LimeJTable and LimeTextField construction put the first client property, whose key
          // enum is initialized on first use (Icebase #e250f20360df, #37b7cd4d814c).
          "javax.swing.ClientPropertyKey",
          // The menus: AbstractMenu subclasses (File, Tools...) build the first menu items, check
          // box items and popup listeners, and the menu bar asks for its UI class (Icebase
          // #44c8a7f53248, #60e1d151c7b1, #34282a047132, #e9a42b3db5e5, #7a620bcd7ff9,
          // #1ac3727ce86b, #ecaed150006d, #863ff8e60cce). BasicPopupMenuUI's nested
          // MenuKeyboardHelper is loaded with it (initializeClasses loads nested classes).
          "javax.swing.JMenuBar",
          "javax.swing.JMenuItem",
          "javax.swing.JCheckBoxMenuItem",
          "javax.swing.plaf.basic.BasicPopupMenuUI",
          "javax.swing.ToolTipManager",
          // LanguagePanel's first viewport and the tool tip manager's delay queue (Icebase
          // #ce64a2783f63, #cb5a61cb7e48).
          "java.util.concurrent.DelayQueue",
          "javax.swing.JViewport",
          // The range slider (JSlider's change listener) and the skin's gradients (Icebase
          // #411e1dbc4e52, #9801898b03f2, build 338).
          "javax.swing.JSlider",
          "java.awt.MultipleGradientPaint",
          "java.awt.LinearGradientPaint",
          // Borders, tree selection, Swing timers and the HTML text views a first dialog lays out
          // (Icebase #c9c36d0281fb, #ac2423d55bd3, #29498b835d5d, #0fc77957dde0, #12548d75e971,
          // #337810905345).
          "javax.swing.border.MatteBorder",
          "javax.swing.tree.DefaultTreeSelectionModel",
          "javax.swing.Timer",
          "javax.swing.text.GlyphView",
          "javax.swing.text.AbstractDocument",
          // Java2D shapes the skins paint: area flattening and Path2D bounds (Icebase
          // #e620bd61432a, #0ee55ff55bd4).
          "java.awt.geom.Area",
          "java.awt.geom.Path2D",
          // The ranking of the first search result batch loaded PerformersHelper (common's jar)
          // on the EDT (Icebase #b3732fb24421). Its static state is a stopword set and a pattern.
          "com.frostwire.search.PerformersHelper");

  /**
   * JDK-internal classes whose names change between JDK releases, initialized after {@link
   * #MAIN_WINDOW_CLASSES_TO_INITIALIZE} and skipped without a warning when this JDK lacks them.
   * Marlin's RendererContext is created per thread the first time the thread paints an antialiased
   * shape (Icebase #9bbf9bfe7c49, frostwire/frostwire#1563): the instance is a per-thread context
   * and can't be warmed from this thread, but its classes and the rendering engine's can.
   */
  static final List<String> OPTIONAL_JDK_CLASSES_TO_INITIALIZE =
      List.of(
          "sun.java2d.pipe.RenderingEngine",
          "sun.java2d.marlin.MarlinProperties",
          "sun.java2d.marlin.RendererContext",
          "sun.java2d.marlin.DMarlinRenderingEngine",
          "sun.java2d.marlin.MarlinRenderingEngine",
          // The reflection helper behind the shutdown window's first dialog (Icebase
          // #b2975f5ce266, ShutdownWindow.new -> MethodUtil.invoke); not exported, skipped if
          // absent.
          "sun.reflect.misc.MethodUtil",
          // Direct3D's paint registry, initialized the first time the EDT paints a gradient with
          // the D3D pipeline (Icebase #8a8c42b77f5a, ProgressBarPainter -> D3DPaints.<clinit>);
          // Windows only, skipped on other systems.
          "sun.java2d.d3d.D3DPaints");

  /**
   * Windows-only JDK classes initialized like {@link #CLASSES_TO_INITIALIZE}. The first right-click
   * on the tray icon created the AWT popup menu's peer, and WPopupMenuPeer's superclass
   * WMenuItemPeer looked up AWT's localization bundle in its static initializer on the EDT (Icebase
   * #4be674f95823, frostwire/frostwire#1469). Initializing the class initializes its superclasses.
   */
  static final List<String> WINDOWS_CLASSES_TO_INITIALIZE =
      List.of("sun.awt.windows.WPopupMenuPeer");

  /**
   * Classes the Swing splash window initializes on the EDT, initialized beforehand by the startup
   * thread that decodes the splash image ({@link #initializeSplashClasses}): the warm-up thread
   * runs alongside the splash and can't be relied on to get there first. ImageIcon's static
   * initializer builds its shared MediaTracker and component, which a cold JVM loaded on the EDT
   * while building the splash (Icebase #e45e1beac54a, frostwire/frostwire#1323). The splash is also
   * the first native window: its screen surface registers Java2D's rendering loops, so
   * GraphicsPrimitiveMgr's static initializer ran on the EDT in the splash's pack() (Icebase
   * #e3e91ab8b37d). ImageIcon comes first: its Toolkit loads the native library the loops need.
   * Packing the splash also gives its first lightweight component a peer, which ran
   * LightweightPeerHolder's static initializer on the EDT (Icebase #16351ea60cdb).
   */
  static final List<String> SPLASH_CLASSES_TO_INITIALIZE =
      List.of(
          "javax.swing.ImageIcon",
          "sun.java2d.loops.GraphicsPrimitiveMgr",
          "sun.awt.LightweightPeerHolder",
          // Every root pane's UI creates a BasicRootPaneUI.RootPaneInputMap, and the splash's is
          // the first (Icebase #2550522ba4dc). On the background warm-up thread a 2-CPU machine
          // hadn't reached it before the splash was built (Icebase #e6bb648dd725).
          "javax.swing.plaf.basic.BasicRootPaneUI",
          // With Nimbus the root pane's UI is SynthRootPaneUI, created in SynthLookAndFeel.createUI
          // on the EDT as the splash was built (Icebase #e55c52246999).
          "javax.swing.plaf.synth.SynthRootPaneUI",
          // The splash's StatusComponent lays itself out with a BoxLayout and clears its opacity
          // with GUIUtils, whose static initializer builds the number formats and size units. On
          // a 4-CPU machine the EDT read BoxLayout from the JDK's module image and ran GUIUtils'
          // initializer while building the splash (Icebase #9910992021ac, #977ae4aaf003,
          // frostwire/frostwire#1563). Both are needed by the splash anyway, so this moves the
          // work off the EDT without adding any before the splash.
          "javax.swing.BoxLayout",
          "com.limegroup.gnutella.gui.GUIUtils",
          // The splash's JWindow is the first root pane: its UI asks UIManager for the default
          // focus traversal policy, which creates a LayoutFocusTraversalPolicy and ran
          // SortingFocusTraversalPolicy's static initializer on the EDT, waiting on the disk with
          // the system idle (Icebase #1aefbe184b7d, #4961cab0e5c8, frostwire/frostwire#1563).
          // Initializing the subclass initializes its superclass too.
          "javax.swing.LayoutFocusTraversalPolicy",
          // The splash's StatusComponent builds a JProgressBar (its Synth UI, the Basic handler
          // and the range model) and its layout a Box.Filler, on the EDT, with the warm-up not
          // finished on slow machines (Icebase #ff8c88492d2e, #feaa3907753f, #1969053f78dd,
          // frostwire/frostwire#1563). Needed by the splash anyway, so this adds no work before it.
          "javax.swing.DefaultBoundedRangeModel",
          "javax.swing.JProgressBar",
          "javax.swing.plaf.basic.BasicProgressBarUI",
          "javax.swing.plaf.synth.SynthProgressBarUI",
          "javax.swing.Box");

  /**
   * Windows-only splash classes, initialized after {@link #SPLASH_CLASSES_TO_INITIALIZE}: the first
   * window's GDI surface registers the GDI blit loops in its static initializer, which ran on the
   * EDT in the splash's pack() (Icebase #709822685cbb, #bc64bcf27594, frostwire/frostwire#1323).
   */
  static final List<String> WINDOWS_SPLASH_CLASSES_TO_INITIALIZE =
      List.of("sun.java2d.windows.GDIWindowSurfaceData");

  /** Initializes {@code classNames} in order, silently skipping those this JDK doesn't have. */
  static void initializeOptionalClasses(List<String> classNames) {
    for (String className : classNames) {
      try {
        Class.forName(className).getDeclaredClasses();
      } catch (ClassNotFoundException | LinkageError e) {
        // Not in this JDK release.
      } catch (Throwable t) {
        LOG.warn("Warm-up of " + className + " failed: " + t.getMessage(), t);
      }
    }
  }

  /** The classes {@link #initializeSplashClasses} initializes: Windows-only ones too if asked. */
  static List<String> splashClassesToInitialize(boolean windows) {
    if (!windows) {
      return SPLASH_CLASSES_TO_INITIALIZE;
    }
    List<String> classes = new ArrayList<>(SPLASH_CLASSES_TO_INITIALIZE);
    classes.addAll(WINDOWS_SPLASH_CLASSES_TO_INITIALIZE);
    return classes;
  }

  /** Initializes the splash's classes in order; call off the EDT before the splash. */
  public static void initializeSplashClasses() {
    initializeClasses(splashClassesToInitialize(OSUtils.isWindows()));
  }

  /**
   * Initializes each class, in order, and loads (without initializing) its nested classes, which
   * its first instance creates, e.g. BasicRootPaneUI's RootPaneInputMap and BasicTextUI's RootView.
   */
  static void initializeClasses(List<String> classNames) {
    for (String className : classNames) {
      try {
        Class.forName(className).getDeclaredClasses();
      } catch (Throwable t) {
        LOG.warn("Warm-up of " + className + " failed: " + t.getMessage(), t);
      }
    }
  }

  /** The classes the warm-up initializes: the Windows-only ones too when {@code windows}. */
  static List<String> classesToInitialize(boolean windows) {
    if (!windows) {
      return CLASSES_TO_INITIALIZE;
    }
    List<String> classes = new ArrayList<>(CLASSES_TO_INITIALIZE);
    classes.addAll(WINDOWS_CLASSES_TO_INITIALIZE);
    return classes;
  }

  /**
   * FlatLaf's UI packages: its component UI delegates and the borders, utilities, layout helpers
   * and icons they use. The UI delegate step loads only the delegates the defaults name, so with a
   * FlatLaf theme the rest was read from the jar on the EDT as the main window and its tabs were
   * built (Icebase #e05de8f17909, frostwire/frostwire#1488: FlatButtonBorder's helper classes for
   * the Library's buttons). A theme switch loads the same packages, see ThemeSwitcher.
   */
  public static final List<String> FLATLAF_PACKAGES =
      List.of("com.formdev.flatlaf.ui", "com.formdev.flatlaf.icons");

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
          // Drag and drop: MainFrame's constructor creates its DropTarget with these almost first
          // (Icebase #628f10bffe90), and the first drag out of a table builds LimeTransferHandler,
          // the drag gesture listener and FileTransferable (Icebase #3315b9f6e6e7).
          "com.limegroup.gnutella.gui.dnd",
          // The tab objects MainFrame.buildTabs creates first (SearchTab, LibraryTab).
          "com.frostwire.gui.tabs",
          // ApplicationHeader's search box and its update button.
          "com.frostwire.gui.searchfield",
          // The search fields' suggestion dictionary (StringTrieSet, StringTrie), built when the
          // first search field is (Icebase #1e727a1088e0, GitHub #1492).
          "org.limewire.collection",
          "com.frostwire.gui.updates",
          // MainFrame's menu bar and the actions behind its items.
          "com.limegroup.gnutella.gui.menu",
          "com.limegroup.gnutella.gui.actions",
          // Table mediators, LimeJTable, renderers and editors shared by every table pane.
          "com.limegroup.gnutella.gui.tables",
          // Search tab: SearchResultMediator, SearchOptionsPanel, SearchEngine and the anonymous
          // class of each engine. Before Transfers: MainFrame builds Search first and defers
          // Transfers (Icebase #fd2a47a7c8e4).
          "com.limegroup.gnutella.gui.search",
          // The search engines both clients share (frostwire/frostwire#1002).
          "com.frostwire.search.engines",
          // Transfers tab, built just after the main window (BTDownloadMediator,
          // TransferActionsRenderer), and its detail panes.
          "com.frostwire.gui.bittorrent",
          "com.frostwire.gui.components.transfers",
          // Library tab: LibraryMediator, its tree and its table mediators.
          "com.frostwire.gui.library",
          // Only classes directly in a listed package load, not its subpackages. The Library's
          // cover art panel names TagsReader in a method signature, and every component's
          // constructor reflects over its methods (Icebase #56dff5196bea, GitHub #1489).
          "com.frostwire.gui.library.tags",
          // Background executors and launch helpers the transfer and search panes call.
          "com.limegroup.gnutella.gui.util",
          // Search results' "Browse Shared Torrents" action (PeerCatalogWindow).
          "com.limegroup.gnutella.gui.icebridge",
          // Options dialog, built on the first click: OptionsConstructor and the tree manager,
          // and every pane item class the tree names (it reads their label constants).
          "com.limegroup.gnutella.gui.options",
          "com.limegroup.gnutella.gui.options.panes",
          // Their subpackages: the options tree's model and the IP filter pane.
          "com.limegroup.gnutella.gui.trees",
          "com.limegroup.gnutella.gui.options.panes.ipfilter",
          // Last, after everything the first windows need: classes directly in com.frostwire.gui
          // (LocaleLabel, AlphaIcon...), components.slides (MultimediaSlideshowPanel, built by the
          // Search tab) and filters, which no listed package covers (Icebase #29498b835d5d,
          // #ceec9dce6d9e, frostwire/frostwire#1563). SearchMediator, SearchResultDisplayer,
          // LibraryExplorer, UpdateManager, SearchField, DisabledIcons (gui.theme, a splash
          // package) and FixedsizeForgetfulHashMap (org.limewire.collection) are already in
          // packages above.
          "com.frostwire.gui",
          "com.frostwire.gui.components.slides",
          "com.frostwire.gui.filters");

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
   * its thumb and its track), a checkbox per search engine, and the separator next to the file type
   * buttons (Icebase #60a32635447a, frostwire/frostwire#1419).
   */
  static final List<String> SEARCH_OPTIONS_PAINTER_KEY_PREFIXES =
      List.of("Slider", "CheckBox[", "Separator[");

  /**
   * Painters of the menu bar and its menus, built with the main window: each menu item's first
   * style lookup loaded its Nimbus painter class by reflection on the EDT (Icebase #c8a58fd2e059,
   * frostwire/frostwire#1483).
   */
  static final List<String> MENU_PAINTER_KEY_PREFIXES =
      List.of(
          "MenuBar", "Menu[", "MenuItem", "RadioButtonMenuItem", "CheckBoxMenuItem", "PopupMenu");

  /**
   * Every painter key: after the ones above, which are created first because they are needed first,
   * one pass creates all the others. Each component type loaded its Nimbus painter class by
   * reflection on the EDT the first time it was styled, the menus and then the Library's scroll
   * pane among them (Icebase #a4b1ace3e68c, frostwire/frostwire#1483); Nimbus has a few dozen
   * painter classes, and loading them all takes milliseconds.
   */
  static final String ALL_PAINTER_KEYS = "";

  private static final String PAINTER_KEY_SUFFIX = "Painter";

  /**
   * Upper bound on how long app class loading waits for the splash window. Startup without a splash
   * (system startup) still releases the wait, so this only matters if splash creation fails.
   */
  static final long SPLASH_WAIT_MS = 15_000;

  /** Released once the Swing splash window is showing, or when no splash will be shown. */
  private static final CountDownLatch SPLASH_SHOWN = new CountDownLatch(1);

  /**
   * Upper bound on how long the fallback font sweep waits for the main window. A first run shows
   * the setup wizard before it, for as long as the user takes; the bound only keeps a main window
   * that never shows from leaving the fonts unloaded.
   */
  static final long MAIN_WINDOW_WAIT_MS = TimeUnit.MINUTES.toMillis(5);

  /** Released once the main window is showing, or when startup goes to the tray without it. */
  private static final CountDownLatch MAIN_WINDOW_SHOWN = new CountDownLatch(1);

  private static final WarmUpProgress PROGRESS = new WarmUpProgress();

  private JdkWarmUp() {}

  /**
   * Lets the fallback font sweep start (see {@link #startFallbackFontSweep}): call once the main
   * window is showing, or when startup leaves it hidden.
   */
  public static void mainWindowShown() {
    MAIN_WINDOW_SHOWN.countDown();
  }

  /**
   * Lets the warm-up thread move on to loading app classes. Until then it does only the steps the
   * splash window itself needs, so on 2-CPU machines bulk class loading does not compete with the
   * EDT building the splash.
   */
  public static void splashShown() {
    SPLASH_SHOWN.countDown();
  }

  /**
   * Starts the warm-up threads.
   *
   * @param earlyWindowText the text the first windows show, in the user's language: the fallback
   *     fonts it needs load before the splash (see {@link #initializeSlotsFor})
   * @param jdkStepsDone told how long the JDK steps took (ms), once they finish; they run the same
   *     whatever else the warm-up does, so the time compares from one start to the next
   * @param fontSweep whether to sweep the fallback fonts once the main window shows: not in Lite
   *     mode (frostwire/frostwire#1555), where a script's fonts load the first time it is drawn
   */
  public static void startInBackground(
      Supplier<String> earlyWindowText, LongConsumer jdkStepsDone, boolean fontSweep) {
    // The JDK steps, the app class preload and, unless skipped, the fallback font sweep.
    PROGRESS.start(warmUpThreads(), System.nanoTime());
    Thread warmUp =
        new Thread(tracked(timed(() -> warmUp(earlyWindowText), jdkStepsDone)), "JdkWarmUp");
    warmUp.setDaemon(true);
    warmUp.start();
    startAppClassPreload(SPLASH_SHOWN, SPLASH_WAIT_MS, tracked(JdkWarmUp::loadAppClasses));
    if (fontSweep) {
      startFallbackFontSweep(
          MAIN_WINDOW_SHOWN, MAIN_WINDOW_WAIT_MS, tracked(JdkWarmUp::sweepFallbackFonts));
    } else {
      // Lite: not the whole sweep, but the glyphs of the scripts the tables and labels show
      startFallbackFontSweep(
          MAIN_WINDOW_SHOWN, MAIN_WINDOW_WAIT_MS, tracked(JdkWarmUp::sweepFallbackFontsLite));
    }
  }

  /**
   * The warm-up's threads: the JDK steps, the app class preload and a font sweep (full or Lite).
   */
  static int warmUpThreads() {
    return 3;
  }

  /**
   * Whether the warm-up has finished and how long it took (or has been running), for Icebase
   * reports; null when it was never started (frostwire/frostwire#1490).
   */
  public static WarmUpProgress.Snapshot progress() {
    return PROGRESS.snapshot(System.nanoTime());
  }

  /** {@code step}, then {@code done} told how long it ran (ms) if it returned normally. */
  static Runnable timed(Runnable step, LongConsumer done) {
    return () -> {
      long start = System.nanoTime();
      step.run();
      try {
        done.accept(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      } catch (Throwable t) {
        LOG.warn("Warm-up time not recorded: " + t.getMessage(), t);
      }
    };
  }

  /** {@code step}, counted as finished in {@link #PROGRESS} however it ends. */
  static Runnable tracked(Runnable step) {
    return () -> {
      try {
        step.run();
      } finally {
        PROGRESS.stepFinished(System.nanoTime());
      }
    };
  }

  /**
   * Starts loading {@link #APP_PACKAGES_TO_LOAD} on its own thread as soon as the splash is
   * showing. It used to run on the warm-up thread after every JDK step; on a slow machine those
   * steps were still running when the EDT built the main window, so the EDT read the Search tab's
   * classes from the jar itself (Icebase #fd2a47a7c8e4, frostwire/frostwire#1489). It still waits
   * for the splash, so on 2-CPU machines it doesn't compete with the EDT building it.
   */
  static Thread startAppClassPreload(
      CountDownLatch splashShown, long waitMs, Runnable loadAppClasses) {
    Thread preload =
        new Thread(
            () -> {
              try {
                awaitSplashShown(splashShown, waitMs);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              try {
                loadAppClasses.run();
              } catch (Throwable t) {
                LOG.warn("App class warm-up failed: " + t.getMessage(), t);
              }
            },
            "JdkWarmUp-AppClasses");
    preload.setDaemon(true);
    preload.start();
    return preload;
  }

  private static void loadAppClasses() {
    Path location = codeSourceOf(JdkWarmUp.class);
    if (location == null) {
      return;
    }
    try {
      loadWithoutInitializing(
          classNamesIn(location, APP_PACKAGES_TO_LOAD), JdkWarmUp.class.getClassLoader());
    } catch (IOException e) {
      LOG.warn("App class warm-up failed: " + e.getMessage(), e);
    }
  }

  /** A text warm-up step that may fail with anything, including a linkage error. */
  @FunctionalInterface
  interface TextStep {
    void run() throws Throwable;
  }

  private static final int MAX_FAILURE_CAUSES = 5;
  private static final int MAX_FAILURE_MESSAGE_CHARS = 200;
  private static final int MAX_FAILURE_CHARS = 800;

  private static final java.util.concurrent.atomic.AtomicReference<String> FIRST_TEXT_FAILURE =
      new java.util.concurrent.atomic.AtomicReference<>();

  /**
   * The first text warm-up failure of this run, as {@code Class: message <- Class: message ...}
   * down the cause chain (bounded, home directory and user name removed), or null when the text
   * warm-up never failed. A NoClassDefFoundError for {@code sun.font.HBShaper} means its static
   * initializer failed once before, and that first failure (ExceptionInInitializerError,
   * UnsatisfiedLinkError) is what explains it; crash and StrictMode reports carry it so one report
   * is enough to diagnose (frostwire/frostwire#1587).
   */
  public static String firstTextWarmUpFailure() {
    return FIRST_TEXT_FAILURE.get();
  }

  /**
   * Runs the text warm-up. It stays best effort: a failure is logged and swallowed (the first
   * on-EDT use pays these costs instead), but the first one is kept for {@link
   * #firstTextWarmUpFailure()}. Package-private so tests can force a failure.
   */
  static void warmUpText(TextStep step) {
    try {
      step.run();
    } catch (Throwable t) {
      LOG.warn("Text warm-up failed: " + t.getMessage(), t);
      recordTextFailure(t);
    }
  }

  /**
   * Keeps the first text failure of this run for {@link #firstTextWarmUpFailure()}; never throws.
   */
  static void recordTextFailure(Throwable t) {
    try {
      FIRST_TEXT_FAILURE.compareAndSet(
          null,
          describeFailure(t, System.getProperty("user.home"), System.getProperty("user.name")));
    } catch (Throwable ignored) {
      // Recording the failure must never change the caller's behavior.
    }
  }

  /** Test seam: forgets the recorded failure. */
  static void clearFirstTextWarmUpFailure() {
    FIRST_TEXT_FAILURE.set(null);
  }

  /**
   * {@code problem}'s class and message and those of its causes (at most {@value
   * #MAX_FAILURE_CAUSES}, each message at most {@value #MAX_FAILURE_MESSAGE_CHARS} characters, all
   * at most {@value #MAX_FAILURE_CHARS}), with {@code home} and {@code user} replaced by {@code
   * <home>} and {@code <user>} so no personal path is reported.
   */
  static String describeFailure(Throwable problem, String home, String user) {
    StringBuilder text = new StringBuilder();
    Throwable current = problem;
    for (int i = 0; i < MAX_FAILURE_CAUSES && current != null; i++) {
      if (i > 0) {
        text.append(" <- ");
      }
      text.append(current.getClass().getName());
      String message = current.getMessage();
      if (message != null) {
        text.append(": ").append(truncate(scrub(message, home, user), MAX_FAILURE_MESSAGE_CHARS));
      }
      current = current.getCause() == current ? null : current.getCause();
    }
    return printableAscii(truncate(text.toString(), MAX_FAILURE_CHARS));
  }

  /**
   * Icebase accepts only printable ASCII in this field and rejects the whole report otherwise, and
   * Windows error messages are localized: every other character becomes '?'.
   */
  private static String printableAscii(String text) {
    StringBuilder ascii = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      ascii.append(c >= 0x20 && c <= 0x7e ? c : '?');
    }
    return ascii.toString();
  }

  private static String scrub(String text, String home, String user) {
    String scrubbed = text;
    if (home != null && !home.isBlank()) {
      scrubbed = scrubbed.replace(home, "<home>");
      scrubbed = scrubbed.replace(home.replace('\\', '/'), "<home>");
    }
    if (user != null && user.length() >= 3) {
      scrubbed = scrubbed.replace(user, "<user>");
    }
    return scrubbed;
  }

  private static String truncate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 3) + "...";
  }

  private static void warmUp(Supplier<String> earlyWindowText) {
    try {
      createPainters(UIManager.getDefaults(), SPLASH_PAINTER_KEY_PREFIX);
    } catch (Throwable t) {
      LOG.warn("Painter warm-up failed: " + t.getMessage(), t);
    }
    warmUpText(
        () -> {
          String text = earlyWindowText(earlyWindowText);
          // layoutGlyphVector always shapes, whatever the script, so this initializes HarfBuzz.
          char[] chars = text.toCharArray();
          PlatformFonts.dialog(Font.PLAIN, 12)
              .layoutGlyphVector(
                  new FontRenderContext(null, true, true),
                  chars,
                  0,
                  chars.length,
                  Font.LAYOUT_LEFT_TO_RIGHT);
          // Only the fallback slots the first windows' text needs. Every slot of every UI font is
          // dozens of font files, each loaded under the JDK's global font lock, which the EDT
          // building the splash, the wizard and the main window waited for (Icebase #7d83b84c37c5,
          // #0068ec7873a2, frostwire/frostwire#1544).
          for (Font font : fallbackSlotFonts(UIManager.getDefaults())) {
            initializeSlotsFor(font, text);
          }
        });
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
      // Derived fonts (bold captions, the splash status label, the slide overlay) and their faces.
      FontWarmUps.loadDerivedFaces(UIManager.getDefaults());
    } catch (Throwable t) {
      LOG.warn("Derived font warm-up failed: " + t.getMessage(), t);
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
    try {
      loadLookAndFeelPackages(UIManager.getLookAndFeel());
    } catch (Throwable t) {
      LOG.warn("Look and feel class warm-up failed: " + t.getMessage(), t);
    }
    try {
      resolveInputMaps(UIManager.getLookAndFeelDefaults());
    } catch (Throwable t) {
      LOG.warn("Key binding warm-up failed: " + t.getMessage(), t);
    }
    try {
      for (String prefix : MENU_PAINTER_KEY_PREFIXES) {
        createPainters(UIManager.getDefaults(), prefix);
      }
    } catch (Throwable t) {
      LOG.warn("Menu painter warm-up failed: " + t.getMessage(), t);
    }
    try {
      createPainters(UIManager.getDefaults(), ALL_PAINTER_KEYS);
    } catch (Throwable t) {
      LOG.warn("Painter warm-up failed: " + t.getMessage(), t);
    }
    initializeClasses(classesToInitialize(OSUtils.isWindows()));
    try {
      // The first right-click menu built a SkinMenu, whose reflective field accessor the JVM
      // generated on the EDT (GitHub #1439).
      SkinMenu.warmUpPopupMenuAccess();
    } catch (Throwable t) {
      LOG.warn("Menu accessor warm-up failed: " + t.getMessage(), t);
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
      // The steps below are for the main window: like the app classes (loaded on their own thread,
      // see startAppClassPreload), they wait until the splash is showing.
      awaitSplashShown(SPLASH_SHOWN, SPLASH_WAIT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    try {
      // After the splash, not before: this builds a JPopupMenu, which adds PopupMenu.actionMap to
      // the
      // look and feel's table. The first Nimbus style the EDT validates (the splash's first panel)
      // copies that table once, and a key added meanwhile made the copy throw
      // ConcurrentModificationException, which left Nimbus's defaults half built and failed every
      // later component, FatalBugManager's dialog included (Icebase #721dc3021809, #90e72cf67632).
      warmUpPopupMenus();
    } catch (Throwable t) {
      LOG.warn("Popup menu warm-up failed: " + t.getMessage(), t);
    }
    initializeClasses(MAIN_WINDOW_CLASSES_TO_INITIALIZE);
    initializeOptionalClasses(OPTIONAL_JDK_CLASSES_TO_INITIALIZE);
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
      }
    } catch (Throwable t) {
      LOG.warn("Text font glyph warm-up failed: " + t.getMessage(), t);
    }
    try {
      Map<?, ?> desktopHints = desktopFontHints();
      FontWarmUps.rasterizeVariants(UIManager.getDefaults(), desktopHints);
      // The setup wizard's language list, on a first run only.
      List<String> names = FontWarmUps.languageNamesOnFirstRun();
      if (!names.isEmpty()) {
        FontWarmUps.warmUpLanguageNames(
            names, FontWarmUps.languageListFonts(UIManager.getDefaults()), desktopHints);
      }
    } catch (Throwable t) {
      LOG.warn("Derived font glyph warm-up failed: " + t.getMessage(), t);
    }
    try {
      warmUpDateFormatAndCollation(formatLocale(), Locale.getDefault());
      warmUpDateToString();
      warmUpNormalizer();
      warmUpSearchText();
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
    // The fallback fonts of every other script load later, once the main window is showing, see
    // startFallbackFontSweep.
  }

  /**
   * Starts the sweep of every fallback font on its own thread, below normal priority, once the main
   * window is showing (or after {@code waitMs}). Each fallback slot is a font file loaded under the
   * JDK's global font lock; loading them all before the splash made the EDT wait for that lock
   * while it built the setup wizard and the main window (Icebase #7d83b84c37c5, #0068ec7873a2,
   * frostwire/frostwire#1544).
   */
  static Thread startFallbackFontSweep(
      CountDownLatch mainWindowShown, long waitMs, Runnable sweep) {
    Thread fonts =
        new Thread(
            () -> {
              try {
                if (!mainWindowShown.await(waitMs, TimeUnit.MILLISECONDS)) {
                  LOG.info("Main window not shown after " + waitMs + " ms, loading fonts anyway");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              try {
                sweep.run();
              } catch (Throwable t) {
                LOG.warn("Fallback font warm-up failed: " + t.getMessage(), t);
              }
            },
            "JdkWarmUp-Fonts");
    fonts.setDaemon(true);
    fonts.setPriority(BackgroundPriority.PRIORITY);
    fonts.start();
    return fonts;
  }

  /**
   * Loads every fallback slot of every UI font, for text in any script (file names, search results
   * and suggestions), creates the slots' strikes for the fonts text paints with ({@link
   * #createSlotStrikes}), then rasterizes {@link #SCRIPT_GLYPH_SAMPLE} and resolves fonts for
   * complex text ({@link #warmUpTextLayout}), which consult those slots too.
   */
  private static void sweepFallbackFonts() {
    try {
      for (Font font : fallbackSlotFonts(UIManager.getDefaults())) {
        initializeAllSlots(font, JdkWarmUp::releaseFontLock);
      }
    } catch (Throwable t) {
      LOG.warn("Fallback font warm-up failed: " + t.getMessage(), t);
    }
    try {
      Map<?, ?> desktopHints = desktopFontHints();
      int[] candidates = slotCandidateCodePoints();
      for (Font font : slotStrikeFonts(UIManager.getDefaults())) {
        initializeAllSlots(font, JdkWarmUp::releaseFontLock);
        int[] codePoints = oneCodePointPerSlot(font, candidates);
        for (AffineTransform transform : glyphTransforms(screenTransform())) {
          createSlotStrikes(font, desktopHints, transform, codePoints, JdkWarmUp::releaseFontLock);
        }
      }
    } catch (Throwable t) {
      LOG.warn("Fallback font strike warm-up failed: " + t.getMessage(), t);
    }
    try {
      Map<?, ?> desktopHints = desktopFontHints();
      for (Font font : textFonts(UIManager.getDefaults())) {
        rasterizeGlyphs(font, desktopHints, SCRIPT_GLYPH_SAMPLE);
      }
    } catch (Throwable t) {
      LOG.warn("Script glyph warm-up failed: " + t.getMessage(), t);
    }
    try {
      warmUpTextLayout();
    } catch (Throwable t) {
      LOG.warn("Text layout warm-up failed: " + t.getMessage(), t);
    }
    try {
      FontWarmUps.warmUpClipping(UIManager.getDefaults());
    } catch (Throwable t) {
      LOG.warn("Clipping warm-up failed: " + t.getMessage(), t);
    }
  }

  /**
   * What Lite does instead of {@link #sweepFallbackFonts()} (frostwire/frostwire#1562, #1555):
   * rasterizes {@link #SCRIPT_GLYPH_SAMPLE} for the table and label fonts only, so the first glyph
   * of each script the Library table and the labels show (file names, search results) no longer
   * reads that script's font file on the EDT. It leaves out the slot-by-slot loading with pauses,
   * the strikes of every slot and the text layout, the costly parts of the full sweep.
   */
  private static void sweepFallbackFontsLite() {
    try {
      Map<?, ?> desktopHints = desktopFontHints();
      int fonts = 0;
      for (Font font : textFonts(UIManager.getDefaults())) {
        if (fonts++ >= LITE_SWEEP_FONTS) {
          break;
        }
        rasterizeGlyphs(font, desktopHints, SCRIPT_GLYPH_SAMPLE);
        releaseFontLock();
      }
    } catch (Throwable t) {
      LOG.warn("Lite script glyph warm-up failed: " + t.getMessage(), t);
    }
  }

  /** How many of the text fonts (the table and label ones come first) Lite rasterizes. */
  static final int LITE_SWEEP_FONTS = 2;

  /**
   * One letter of each script of {@link #SCRIPT_GLYPH_SAMPLE}, in its order, then {@link
   * #UNMAPPED_CODE_POINT}: looking each up loads the slots up to its script's font, a few at a
   * time, and the last one the rest.
   */
  static final int[] SLOT_SWEEP_CODE_POINTS = slotSweepCodePoints();

  private static int[] slotSweepCodePoints() {
    Set<Character.UnicodeScript> scripts = new HashSet<>();
    IntStream letters =
        SCRIPT_GLYPH_SAMPLE
            .codePoints()
            .filter(cp -> Character.isLetter(cp) && scripts.add(Character.UnicodeScript.of(cp)));
    return IntStream.concat(letters, IntStream.of(UNMAPPED_CODE_POINT)).toArray();
  }

  /** Pause between lookups of the fallback font sweep. */
  private static final long FONT_LOCK_PAUSE_MS = 20;

  /**
   * Loads every fallback slot of a font a few at a time ({@link #SLOT_SWEEP_CODE_POINTS}), calling
   * {@code pause} after each lookup. The JDK's font lock is held only while one slot loads, but a
   * thread that releases a monitor and asks for it again right away often gets it back before a
   * waiting thread; the pause lets the EDT take the lock between slots.
   */
  static void initializeAllSlots(Font font, Runnable pause) {
    for (int codePoint : SLOT_SWEEP_CODE_POINTS) {
      font.canDisplay(codePoint);
      pause.run();
    }
  }

  /**
   * The fonts whose fallback slot strikes the sweep creates: the {@link #textFonts}, their bold
   * (captions and matches bolded with deriveFont: GUIUtils.setBoldText,
   * TransferDetailPieces.boldCaption, MatchHighlightingCellRenderer) and the {@link
   * #mainWindowPlatformFonts} (the header's tabs, the donation buttons).
   */
  static Set<Font> slotStrikeFonts(UIDefaults defaults) {
    Set<Font> fonts = new LinkedHashSet<>();
    for (Font font : textFonts(defaults)) {
      fonts.add(font);
      fonts.add(font.deriveFont(Font.BOLD));
    }
    fonts.addAll(mainWindowPlatformFonts());
    return fonts;
  }

  /** Code points spread over each Unicode block among {@link #slotCandidateCodePoints}. */
  private static final int CODE_POINTS_PER_BLOCK = 8;

  /**
   * Code points to find each fallback slot by: the first letter of each script and {@link
   * #CODE_POINTS_PER_BLOCK} visible code points spread over each Unicode block, about 3,000. A slot
   * font covers a script or some blocks, not always from their first code point; looking up every
   * code point instead took over a minute per font.
   */
  static int[] slotCandidateCodePoints() {
    Set<Character.UnicodeScript> scripts = new HashSet<>();
    Set<Integer> candidates = new LinkedHashSet<>();
    Map<Character.UnicodeBlock, List<Integer>> blocks = new LinkedHashMap<>();
    for (int cp = ' '; cp <= Character.MAX_CODE_POINT; cp++) {
      Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
      if (block == null || !isVisible(cp)) {
        continue;
      }
      blocks.computeIfAbsent(block, b -> new ArrayList<>()).add(cp);
      if (Character.isLetter(cp) && scripts.add(Character.UnicodeScript.of(cp))) {
        candidates.add(cp);
      }
    }
    for (List<Integer> codePoints : blocks.values()) {
      for (int i = 0; i < CODE_POINTS_PER_BLOCK; i++) {
        candidates.add(codePoints.get(i * codePoints.size() / CODE_POINTS_PER_BLOCK));
      }
    }
    return candidates.stream().mapToInt(Integer::intValue).toArray();
  }

  private static boolean isVisible(int codePoint) {
    return switch (Character.getType(codePoint)) {
      case Character.UNASSIGNED,
          Character.CONTROL,
          Character.FORMAT,
          Character.SURROGATE,
          Character.PRIVATE_USE,
          Character.SPACE_SEPARATOR,
          Character.LINE_SEPARATOR,
          Character.PARAGRAPH_SEPARATOR ->
          false;
      default -> true;
    };
  }

  /**
   * One code point the font displays from each slot that maps any of {@code candidates}, in
   * candidate order. A composite font's glyph code carries the slot that maps it in its top byte
   * (sun.font.CompositeGlyphMapper, and CCompositeGlyphMapper on macOS); any other font is one
   * slot.
   */
  static int[] oneCodePointPerSlot(Font font, int[] candidates) {
    FontRenderContext frc = new FontRenderContext(null, true, true);
    Map<Integer, Integer> bySlot = new LinkedHashMap<>();
    for (int codePoint : candidates) {
      if (font.canDisplay(codePoint)) {
        int glyph = font.createGlyphVector(frc, Character.toString(codePoint)).getGlyphCode(0);
        bySlot.putIfAbsent(glyph >>> 24, codePoint);
      }
    }
    return bySlot.values().stream().mapToInt(Integer::intValue).toArray();
  }

  /**
   * Measures and draws each code point in the font at the transform, with the desktop's text hints,
   * calling {@code pause} after each. Text in a fallback slot creates that slot font's strike for
   * the font's size, style, transform and hints, and the strike the slot font's scaler, which reads
   * the font file (FreetypeFontScaler.initNativeScaler). Loading the slot ({@link
   * #initializeAllSlots}) reads only its character map, so the first table cell, label or bold
   * caption with text in the slot's script did the rest on the EDT (Icebase #b1a05774f024,
   * #0bf97dd98611, #d9a5167fe941, frostwire/frostwire#1562).
   */
  static void createSlotStrikes(
      Font font,
      Map<?, ?> desktopHints,
      AffineTransform transform,
      int[] codePoints,
      Runnable pause) {
    int scale =
        (int) Math.ceil(Math.max(1, Math.max(transform.getScaleX(), transform.getScaleY())));
    BufferedImage image = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.transform(transform);
      if (desktopHints != null) {
        g.addRenderingHints(desktopHints);
      }
      g.setFont(font);
      for (int codePoint : codePoints) {
        String text = Character.toString(codePoint);
        g.getFontMetrics().stringWidth(text);
        g.drawString(text, 0, 48);
        pause.run();
      }
    } finally {
      g.dispose();
    }
  }

  private static void releaseFontLock() {
    try {
      Thread.sleep(FONT_LOCK_PAUSE_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * The text whose fallback slots load before the splash: the first windows' text and printable
   * ASCII (Latin letters, digits, punctuation), or ASCII alone when the first windows' text is
   * unavailable.
   */
  static String earlyWindowText(Supplier<String> earlyWindowText) {
    try {
      String text = earlyWindowText.get();
      return text == null || text.isEmpty() ? GLYPH_SAMPLE : text + " " + GLYPH_SAMPLE;
    } catch (Throwable t) {
      LOG.warn("Early window text unavailable: " + t.getMessage(), t);
      return GLYPH_SAMPLE;
    }
  }

  /**
   * Loads the font files a composite font needs to show {@code text}, and no others. A logical or
   * look-and-feel font is a composite of a primary font and fallback "slot" fonts; each slot is one
   * font file, loaded the first time it is consulted (CompositeFont.doDeferredInitialisation) under
   * the JDK's global font lock. The font's metrics merge its first slots, which measuring loads.
   * Each character is then looked up slot by slot until one maps it, loading the slots on the way:
   * Latin text loads the primary font, and text in another script the slots up to that script's
   * font. A character no slot maps loads them all, as the same text on the EDT would.
   */
  static void initializeSlotsFor(Font font, String text) {
    Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      g.getFontMetrics(font).getAscent();
    } finally {
      g.dispose();
    }
    text.codePoints().distinct().forEach(font::canDisplay);
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
    return List.of(
        PlatformFonts.sansSerif(Font.BOLD, 10),
        PlatformFonts.sansSerif(Font.PLAIN, 12),
        // The donation buttons (frostwire/frostwire#1467).
        PlatformFonts.DIALOG_BOLD,
        // The transfer details' "hide" link (frostwire/frostwire#1537).
        PlatformFonts.TRANSFER_DETAIL_LINK);
  }

  /**
   * The fonts that show text in any script: labels, table cells (Table.font, a strike of its own
   * when the user picked a table font size) and the search suggestions on Windows (a plain 12 pt
   * Dialog, ThemeMediator.DIALOG_FONT), and the search fields' larger font
   * (frostwire/frostwire#1490).
   */
  static Set<Font> textFonts(UIDefaults defaults) {
    Set<Font> fonts = new LinkedHashSet<>();
    for (String key : new String[] {"Label.font", "Table.font"}) {
      Font font = defaults.getFont(key);
      if (font != null) {
        fonts.add(font);
      }
    }
    fonts.add(PlatformFonts.dialog(Font.PLAIN, 12));
    Font textField = defaults.getFont("TextField.font");
    if (textField != null) {
      fonts.add(PlatformFonts.searchField(textField));
    }
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
   * Looks up the JDK's data-transfer service and builds the default flavor map, as the main
   * window's first {@code new DropTarget(...)} does: the lookup is a ServiceLoader scan of the
   * module graph that loaded the provider from the boot class path on the EDT
   * (frostwire/frostwire#1370, Icebase #c412f1f06e67). The provider builds the map under its own
   * lock.
   *
   * <p>Creating the map doesn't fill it: it reads its native table and builds the standard text
   * encodings on the first lookup, which the first drag out of a table made on the EDT (Icebase
   * #fe1c073c0ca2). Asking for the natives of the file-list flavor that drag exports fills it here.
   */
  static void warmUpDataTransfer() {
    if (SystemFlavorMap.getDefaultFlavorMap() instanceof FlavorTable table) {
      table.getNativesForFlavor(DataFlavor.javaFileListFlavor);
    }
  }

  /**
   * Breaks {@link #GLYPH_SAMPLE} into lines the way a wrapped multi-line tooltip does
   * (WrappedPlainView through javax.swing.text.Utilities.getBreakLocation). The first line
   * BreakIterator loads the JDK's line-break rules (RuleBasedBreakIterator and its resource data),
   * which the character BreakIterator of {@link #warmUpTextLayout} does not; the first multi-line
   * tooltip paid that on the EDT (Icebase #7c1eec7b21f2).
   */
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
      LOG.info("Splash not shown after " + timeoutMs + " ms, warming up anyway");
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

  /**
   * Measures and draws {@code text} in the font, see {@link #rasterizeGlyphs(Font, Map)}, once per
   * transform painting uses ({@link #glyphTransforms}). A font strike is keyed by its transform,
   * and on a scaled display (125%, 150%) Swing paints through the screen's scale: glyphs warmed
   * only unscaled were rasterized again on the EDT by the first paint (Icebase #c8c8e424495c,
   * frostwire/frostwire#1346).
   */
  static void rasterizeGlyphs(Font font, Map<?, ?> desktopHints, String text) {
    for (AffineTransform transform : glyphTransforms(screenTransform())) {
      rasterizeGlyphs(font, desktopHints, text, transform);
    }
  }

  static void rasterizeGlyphs(
      Font font, Map<?, ?> desktopHints, String text, AffineTransform transform) {
    int scale =
        (int) Math.ceil(Math.max(1, Math.max(transform.getScaleX(), transform.getScaleY())));
    BufferedImage image =
        new BufferedImage(GLYPH_IMAGE_WIDTH * scale, 32 * scale, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.transform(transform);
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

  /** Unscaled, and the screen's scale too when it has one. */
  static List<AffineTransform> glyphTransforms(AffineTransform screen) {
    if (screen == null || screen.isIdentity()) {
      return List.of(new AffineTransform());
    }
    return List.of(new AffineTransform(), new AffineTransform(screen));
  }

  /** The default screen's transform (its display scale), or null when there is no screen. */
  private static AffineTransform screenTransform() {
    try {
      if (GraphicsEnvironment.isHeadless()) {
        return null;
      }
      return GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice()
          .getDefaultConfiguration()
          .getDefaultTransform();
    } catch (Throwable t) {
      return null;
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
  static void warmUpDateFormatAndCollation(Locale formatLocale, Locale collationLocale) {
    new SimpleDateFormat("EEE, MMM. d, yyyy h:mm a", formatLocale).format(new Date(0));
    Collator collator = Collator.getInstance(collationLocale);
    collator.setDecomposition(Collator.FULL_DECOMPOSITION);
    collator.compare("FrostWire", "frostwire");
  }

  /**
   * Date.toString, which Library rows use for their modified-time cell. Its time zone field loads
   * the JDK's time zone display names on the first call, which the date format warm-up above
   * doesn't (no zone in its pattern); the first Library date cell did it on the EDT (Icebase
   * #b310fd108906, frostwire/frostwire#1472).
   */
  static String warmUpDateToString() {
    return new Date(0).toString();
  }

  /**
   * NFC normalization, which SafeText applies to table cell text that isn't plain ASCII. The first
   * call loads the JDK's ICU normalization data; the first such cell did it on the EDT (Icebase
   * #a2fec79d2180, frostwire/frostwire#1477).
   */
  static String warmUpNormalizer() {
    return Normalizer.normalize("cafe\u0301", Normalizer.Form.NFC);
  }

  /**
   * The normalization of search keywords: compiles {@link SearchText}'s pattern, which initializes
   * {@code Character.UnicodeBlock}, and loads the NFKD data. The first search did both on the EDT
   * (Icebase #914536b5767d, frostwire/frostwire#1521).
   */
  static String warmUpSearchText() {
    return SearchText.normalize("Cafe\u0301");
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
   * The fonts whose fallback slots for the first windows' text are resolved before the splash
   * shows, and all of whose slots load once the main window shows: the look and feel's ({@link
   * #distinctFonts}) and the {@link PlatformFonts} that components set directly, which are in no UI
   * default. A logical font's slots belong to its family and style, not its size, so the setup
   * wizard's 16 pt bold Dialog title (SetupWindow) shares Dialog bold's slots; the warm-up reached
   * those only after the splash, and the first-run wizard measured its title first, resolving them
   * on the EDT (Icebase #d5a31f6c0160, frostwire/frostwire#1329).
   */
  static List<Font> fallbackSlotFonts(UIDefaults defaults) {
    Set<String> seen = new HashSet<>();
    List<Font> fonts = new ArrayList<>();
    List<Font> candidates = new ArrayList<>(distinctFonts(defaults));
    candidates.add(PlatformFonts.dialog(Font.PLAIN, 12));
    candidates.add(PlatformFonts.dialog(Font.BOLD, 12));
    candidates.add(PlatformFonts.sansSerif(Font.PLAIN, 12));
    candidates.add(PlatformFonts.sansSerif(Font.BOLD, 12));
    for (Font font : candidates) {
      if (seen.add(font.getFontName() + "/" + font.getStyle())) {
        fonts.add(font);
      }
    }
    return fonts;
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
   * Builds a popup menu, which installs the look and feel's shared popup keyboard helper and its
   * lazy {@code PopupMenu.actionMap}, then loads the lazy action maps (see {@link
   * #resolveActionMaps}). The first right-click menu loaded that map on the EDT when it showed, and
   * the JDK built the reflective call that fills it there (Icebase #cc3d418ff162). The popup is
   * never shown; the helper and the map are shared by every popup menu, and the JDK installs the
   * helper under a lock.
   */
  static void warmUpPopupMenus() {
    new JPopupMenu();
    resolveActionMaps(UIManager.getLookAndFeelDefaults());
  }

  /**
   * Loads the look and feel's lazy action maps ({@code *.actionMap} entries). Each fills itself the
   * first time it is read, by calling its UI class's {@code loadActionMap} through reflection, and
   * the first such call builds the JDK's method accessor.
   */
  static void resolveActionMaps(UIDefaults defaults) {
    for (Map.Entry<?, ?> entry : List.copyOf(defaults.entrySet())) {
      if (entry.getValue() instanceof ActionMap map) {
        map.size();
      }
    }
  }

  /**
   * Builds the look and feel's key binding maps (its {@code *InputMap} entries, lazy until first
   * read). The main window's root pane read the first of them on the EDT, parsing each binding into
   * a KeyStroke, whose key code AWTKeyStroke looks up by reflection on KeyEvent (Icebase
   * #2aac34fbc637, frostwire/frostwire#1482). AWTKeyStroke caches each key code it looks up, so the
   * EDT's own parse no longer reflects.
   *
   * <p>Each map is built from its lazy value directly and discarded, like {@link #createPainters},
   * never through {@code defaults.get(key)}: while UIDefaults builds a lazy value it stores a
   * private PENDING marker under the key, and Nimbus copies the raw table into its per-component
   * styles the first time the EDT styles a component (the splash's, while this step runs). A copy
   * taken in that window kept the marker for good, and the component's style waited forever for a
   * value no thread would ever store: the EDT froze in UIDefaults.getFromHashtable building the
   * Library's split pane and table header and the setup wizard's text area (Icebase #ff7daaf00779,
   * #c11d7cbc82ac, #275c959430a5). Returns how many maps were built.
   */
  static int resolveInputMaps(UIDefaults defaults) {
    int built = 0;
    for (Map.Entry<?, ?> entry : List.copyOf(defaults.entrySet())) {
      if (entry.getKey() instanceof String key
          && key.endsWith("InputMap")
          && entry.getValue() instanceof UIDefaults.LazyInputMap inputMap) {
        inputMap.createValue(defaults);
        built++;
      }
    }
    return built;
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
   * With a FlatLaf look and feel installed, loads {@link #FLATLAF_PACKAGES} without initializing
   * them; other looks and feels need nothing beyond their UI delegates.
   */
  static void loadLookAndFeelPackages(LookAndFeel lookAndFeel) throws IOException {
    if (lookAndFeel instanceof FlatLaf) {
      loadPackagesOf(FlatLaf.class, FLATLAF_PACKAGES);
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
