/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.gui;

import com.frostwire.util.InMemoryImageIO;
import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.notify.NotifyUserProxy;
import com.limegroup.gnutella.settings.ApplicationSettings;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import org.limewire.collection.FixedsizeForgetfulHashMap;
import org.limewire.util.StringUtils;

/**
 * Manages application resources, including the custom `LookAndFeel`, the locale-specific `String`
 * instances, and any `Icon` instances needed by the application.
 */
public final class ResourceManager {
  /** Constant for the relative path of the gui directory. */
  private static final String GUI_PATH = "org/limewire/gui/";

  /** Constant for the relative path of the resources directory. */
  private static final String RESOURCES_PATH = GUI_PATH + "resources/";

  /** Constant for the relative path of the images directory. */
  private static final String IMAGES_PATH = GUI_PATH + "images/";

  /**
   * Cache of theme images (name as String -> image as ImageIcon). Limited to 200 entries to prevent
   * unbounded growth. Uses LRU eviction policy via FixedsizeForgetfulHashMap.
   */
  private static final Logger LOG = Logger.getLogger(ResourceManager.class);

  private static final Map<String, ImageIcon> THEME_IMAGES = new FixedsizeForgetfulHashMap<>(200);

  /** Instance of this `ResourceManager`, following singleton. */
  private static ResourceManager _instance;

  /**
   * Boolean status that controls whever the shared `Locale` instance needs to be loaded, and
   * locale-specific options need to be setup.
   */
  private static boolean _localeOptionsSet;

  /** Static variable for the loaded `Locale` instance. */
  private static Locale _locale;

  /** Marked true in the event of an error in the load/save of any settings file */
  private static boolean loadFailureEncountered = false;

  static {
    resetLocaleOptions();
  }

  /**
   * Private constructor to ensure that a `ResourceManager` cannot be constructed from outside this
   * class.
   */
  private ResourceManager() {}

  static void resetLocaleOptions() {
    _localeOptionsSet = false;
    setLocaleOptions();
  }

  private static void setLocaleOptions() {
    if (!_localeOptionsSet) {
      if (ApplicationSettings.LANGUAGE.getValue().equals(""))
        ApplicationSettings.LANGUAGE.setValue("en");
      _locale =
          new Locale.Builder()
              .setLanguage(ApplicationSettings.LANGUAGE.getValue())
              .setRegion(ApplicationSettings.COUNTRY.getValue())
              .setVariant(ApplicationSettings.LOCALE_VARIANT.getValue())
              .build();
      StringUtils.setLocale(_locale);
      I18n.setLocale(_locale);
      _localeOptionsSet = true;
    }
  }

  /**
   * Returns the `Locale` instance currently in use.
   *
   * @return the `Locale` instance currently in use
   */
  static Locale getLocale() {
    return _locale;
  }

  /** Indicated if a failure has occurred for delayed reporting */
  static boolean hasLoadFailure() {
    return loadFailureEncountered;
  }

  /** Resets the failure flag */
  static void resetLoadFailure() {
    loadFailureEncountered = false;
  }

  /**
   * The theme images that exist only as GIFs, which load through Toolkit and make the ImageIcon
   * constructor wait for the image fetcher thread. Built on the EDT with the Transfers tab, that
   * wait froze the window (Icebase #c6d108968951, frostwire/frostwire#1470).
   */
  static final List<String> ANIMATED_THEME_IMAGES =
      List.of("indeterminate_small_progress", "downloading");

  /**
   * The status bar's icons. StatusLine and VPNStatusButton set them from refreshes on the EDT, and
   * the first time each state showed, its icon was read from the jar and decoded there (Icebase
   * #f6b720eb10bf, frostwire/frostwire#1481).
   */
  static final List<String> STATUS_BAR_THEME_IMAGES =
      List.of(
          "vpn_on",
          "vpn_off",
          "seeding_small",
          "not_seeding_small",
          "connect_small_0",
          "connect_small_6",
          "downloading_small",
          "uploading_small",
          "firewall",
          "firewall_no");

  /**
   * The buttons of the search results' button row (SearchButtons). The first search built them on
   * the EDT, and each icon was read from the jar and decoded there (Icebase #f071da08eef4,
   * frostwire/frostwire#1486).
   */
  static final List<String> SEARCH_BUTTON_ICONS =
      List.of("SEARCH_DOWNLOAD", "TORRENT_DETAILS", "SEARCH_STOP");

  /**
   * The buttons of the Library's file table (LibraryFilesTableMediator: Launch, Open in Folder,
   * Send to Friend, Delete), built at startup with the Library tab; their icons were read from the
   * jar and decoded on the EDT (Icebase #cfa1bcdf5328, frostwire/frostwire#1503).
   */
  static final List<String> LIBRARY_BUTTON_ICONS =
      List.of("LIBRARY_LAUNCH", "LIBRARY_EXPLORE", "LIBRARY_SEND", "LIBRARY_DELETE");

  /**
   * The buttons of the Transfers table (BTDownloadMediator.getActions: Resume, Pause, Retry, Show
   * in Library, Explore, Remove, Clear Inactive), built at startup with the Transfers tab
   * (frostwire/frostwire#1503).
   */
  static final List<String> TRANSFER_BUTTON_ICONS =
      List.of(
          "DOWNLOAD_FILE_MORE_SOURCES",
          "DOWNLOAD_PAUSE",
          "MAGNET",
          "DOWNLOAD_SHOW_IN_LIBRARY",
          "LIBRARY_EXPLORE",
          "DOWNLOAD_KILL",
          "DOWNLOAD_CLEAR_INACTIVE");

  /**
   * Loads {@link #ANIMATED_THEME_IMAGES}, {@link #STATUS_BAR_THEME_IMAGES} and the theme images
   * behind {@link #SEARCH_BUTTON_ICONS}, {@link #LIBRARY_BUTTON_ICONS} and {@link
   * #TRANSFER_BUTTON_ICONS} into the theme-image cache. Call it off the EDT, at startup before the
   * main window is built, so the EDT finds them cached. Only this synchronized cache is filled; the
   * button icon cache stays EDT-only.
   */
  static void preloadStartupThemeImages() {
    for (List<String> names : List.of(ANIMATED_THEME_IMAGES, STATUS_BAR_THEME_IMAGES)) {
      for (String name : names) {
        try {
          getThemeImage(name);
        } catch (RuntimeException e) {
          LOG.warn("Could not preload theme image " + name, e);
        }
      }
    }
    for (List<String> buttons :
        List.of(SEARCH_BUTTON_ICONS, LIBRARY_BUTTON_ICONS, TRANSFER_BUTTON_ICONS)) {
      preloadButtonThemeImages(buttons);
    }
  }

  /**
   * Loads the theme images these buttons ({@code icon_mapping.properties} names) resolve to into
   * the theme-image cache, in both icon sizes: a handful of small images, and no dependence on the
   * UI settings here. Call it off the EDT.
   */
  public static void preloadButtonThemeImages(List<String> buttonNames) {
    for (boolean small : new boolean[] {true, false}) {
      for (List<String> candidates :
          ButtonIconController.themeImageCandidates(buttonNames, small)) {
        preloadFirstThemeImage(candidates);
      }
    }
  }

  /** Loads the first of {@code names} that exists, as the button icon lookup would. */
  private static void preloadFirstThemeImage(List<String> names) {
    for (String name : names) {
      try {
        getThemeImage(name);
        return;
      } catch (MissingResourceException e) {
        // ButtonIconController falls back to the next name too.
      } catch (RuntimeException e) {
        LOG.warn("Could not preload theme image " + name, e);
        return;
      }
    }
  }

  /**
   * Serves as a single point of access for any icons that should be accessed directly from the file
   * system for themes.
   *
   * @param name The name of the image (excluding the extension) to locate.
   * @return a new `ImageIcon` instance for the specified file, or `null` if the resource could not
   *     be loaded
   */
  static ImageIcon getThemeImage(final String name) {
    if (name == null) throw new NullPointerException("null image name");
    ImageIcon icon;
    // First try to get theme image from cache (thread-safe access)
    synchronized (THEME_IMAGES) {
      icon = THEME_IMAGES.get(name);
      if (icon != null) return icon;
    }
    // Then try to get from org/limewire/gui/images resources
    icon = getImageFromURL(IMAGES_PATH + name, false);
    if (icon != null && icon.getImage() != null) {
      synchronized (THEME_IMAGES) {
        THEME_IMAGES.put(name, icon);
      }
      return icon;
    }
    // no resource? error.
    throw new MissingResourceException("image: " + name + " doesn't exist.", null, null);
  }

  /** Whether the theme image {@code name} is in the cache, without loading it. */
  static boolean isThemeImageCached(String name) {
    synchronized (THEME_IMAGES) {
      return THEME_IMAGES.containsKey(name);
    }
  }

  /** Drops the theme image {@code name} from the cache; tests use it to start from a cold cache. */
  static void forgetThemeImage(String name) {
    synchronized (THEME_IMAGES) {
      THEME_IMAGES.remove(name);
    }
  }

  /** Retrieves an icon from the specified path in the filesystem. */
  static ImageIcon getImageFromResourcePath(String loc) {
    return getImageFromURL(loc, false);
  }

  /**
   * Retrieves an icon from a URL-style path.
   *
   * <p>If 'file' is true, location is treated as a file, otherwise it is treated as a resource.
   *
   * <p>This tries, in order, the exact location, the location as a png, and the location as a gif.
   *
   * <p>Static images decode eagerly so every paint is a direct blit; animated GIFs keep deferred
   * Toolkit loading so the animation is preserved.
   */
  private static ImageIcon getImageFromURL(String location, boolean file) {
    // try exact filename first.
    URL img = toURL(location, file);
    if (img != null) return createImageIcon(img);
    // try with png second
    img = toURL(location + ".png", file);
    if (img != null) return createImageIcon(img);
    // try with gif third (async load preserves animation)
    img = toURL(location + ".gif", file);
    if (img != null) return createDeferredImageIcon(img);
    return null;
  }

  /**
   * Eagerly decodes a static image into a BufferedImage-backed icon, so icon paints never pay the
   * per-paint Toolkit conversion plus software blit that freezes the EDT on large paints. Animated
   * GIFs and unreadable images fall back to deferred Toolkit loading; image loading never fails
   * here.
   */
  private static ImageIcon createImageIcon(URL url) {
    if (!url.getPath().toLowerCase(Locale.ENGLISH).endsWith(".gif")) {
      try {
        BufferedImage image = InMemoryImageIO.read(url);
        if (image != null) {
          return new ImageIcon(image);
        }
      } catch (IOException | RuntimeException ignored) {
        // fall through to deferred loading
      }
    }
    return createDeferredImageIcon(url);
  }

  /**
   * Creates an ImageIcon from a Toolkit image, which keeps a GIF's animation. The ImageIcon
   * constructor still waits (MediaTracker) until the image fetcher thread has decoded the image, so
   * the theme images that load this way are preloaded off the EDT: {@link
   * #preloadAnimatedThemeImages()}.
   */
  private static ImageIcon createDeferredImageIcon(URL url) {
    Image image = Toolkit.getDefaultToolkit().getImage(url);
    return new ImageIcon(image);
  }

  /** Makes a URL out of a location, as either a file or a resource. */
  private static URL toURL(String location, boolean file) {
    if (file) {
      File f = new File(location);
      if (f.exists()) {
        try {
          return f.toURI().toURL();
        } catch (MalformedURLException murl) {
          return null;
        }
      } else {
        return null;
      }
    } else {
      return getURL(location);
    }
  }

  /**
   * Returns a new `URL` instance for the specified file in the "resources" directory.
   *
   * @param FILE_NAME the name of the resource file
   * @return a new `URL` instance for the desired file, or `null` if the `URL` could not be loaded
   */
  public static URL getURLResource(final String FILE_NAME) {
    return ResourceManager.getURL(RESOURCES_PATH + FILE_NAME);
  }

  /**
   * Returns a new `URL` instance for the resource at the specified local path. The path should be
   * the full path within the jar file, such as:
   *
   * <p>
   *
   * <p>org/limewire/gui/images/searching.gif
   *
   * <p>
   *
   * @param PATH the path to the resource file within the jar
   * @return a new `URL` instance for the desired file, or `null` if the `URL` could not be loaded
   */
  private static URL getURL(final String PATH) {
    ClassLoader cl = ResourceManager.class.getClassLoader();
    if (cl == null) {
      return ClassLoader.getSystemResource(PATH);
    }
    URL url = cl.getResource(PATH);
    if (url == null) {
      return ClassLoader.getSystemResource(PATH);
    }
    return url;
  }

  /** Instance accessor following singleton. */
  public static synchronized ResourceManager instance() {
    if (_instance == null) _instance = new ResourceManager();
    return _instance;
  }

  /** Alters all Fonts in UIManager to use Dialog, to correctly display foreign strings. */
  @SuppressWarnings("unused")
  private static boolean checkUIFonts(String newFont, String testString) {
    String[] comps =
        new String[] {
          "TextField.font",
          "PasswordField.font",
          "TextArea.font",
          "TextPane.font",
          "EditorPane.font",
          "FormattedTextField.font",
          "Button.font",
          "CheckBox.font",
          "RadioButton.font",
          "ToggleButton.font",
          "ProgressBar.font",
          "ComboBox.font",
          "InternalFrame.titleFont",
          "DesktopIcon.font",
          "TitledBorder.font",
          "Label.font",
          "List.font",
          "TabbedPane.font",
          "Table.font",
          "TableHeader.font",
          "MenuBar.font",
          "Menu.font",
          "Menu.acceleratorFont",
          "MenuItem.font",
          "MenuItem.acceleratorFont",
          "PopupMenu.font",
          "CheckBoxMenuItem.font",
          "CheckBoxMenuItem.acceleratorFont",
          "RadioButtonMenuItem.font",
          "RadioButtonMenuItem.acceleratorFont",
          "Spinner.font",
          "Tree.font",
          "ToolBar.font",
          "OptionPane.messageFont",
          "OptionPane.buttonFont",
          "ToolTip.font",
        };
    boolean displayable = false;
    for (String comp : comps) displayable |= checkFont(comp, newFont, testString, false);
    // Then do it the automagic way.
    // note that this could work all the time (without requiring the above)
    // if Java 1.4 didn't introduce Locales, and it could even still work
    // if they offered a way to get all the keys of possible resources.
    for (Map.Entry<Object, Object> next : UIManager.getDefaults().entrySet()) {
      if (next.getValue() instanceof Font) {
        Font f = (Font) next.getValue();
        if (f != null && !newFont.equalsIgnoreCase(f.getName())) {
          if (!GUIUtils.canDisplay(f, testString)) {
            f = new Font(newFont, f.getStyle(), f.getSize());
            if (GUIUtils.canDisplay(f, testString)) {
              next.setValue(f);
              displayable = true;
            }
          }
        }
      }
    }
    return displayable;
  }

  /** Updates the font of a given fontName to be newName. */
  private static boolean checkFont(
      String fontName, String newName, String testString, boolean force) {
    boolean displayable = true;
    Font f = UIManager.getFont(fontName);
    if (f != null && !newName.equalsIgnoreCase(f.getName())) {
      if (!GUIUtils.canDisplay(f, testString) || force) {
        f = new Font(newName, f.getStyle(), f.getSize());
        if (GUIUtils.canDisplay(f, testString)) UIManager.put(fontName, f);
        else displayable = false;
      }
    } else if (f != null) {
      displayable = GUIUtils.canDisplay(f, testString);
    } else {
      displayable = false;
    }
    return displayable;
  }

  /** Updates the component to use the native UI resource. */
  static ComponentUI getNativeUI(JComponent c) {
    ComponentUI ret = null;
    String name = UIManager.getSystemLookAndFeelClassName();
    if (name != null) {
      try {
        Class<?> clazz = Class.forName(name);
        LookAndFeel lf = (LookAndFeel) clazz.getDeclaredConstructor().newInstance();
        lf.initialize();
        UIDefaults def = lf.getDefaults();
        ret = def.getUI(c);
      } catch (NoSuchMethodException
          | ClassCastException
          | SecurityException
          | InstantiationException
          | IllegalAccessException
          | LinkageError
          | ClassNotFoundException
          | InvocationTargetException e) {
        e.printStackTrace();
      }
    }
    // if any of those failed, default to the current UI.
    if (ret == null) ret = UIManager.getUI(c);
    return ret;
  }

  /** Determines if a system tray icon is available. */
  public boolean isTrayIconAvailable() {
    return (OSUtils.isWindows() || OSUtils.isLinux())
        && NotifyUserProxy.instance().supportsSystemTray();
  }
}
