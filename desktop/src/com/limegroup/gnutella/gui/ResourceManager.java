/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.notify.NotifyUserProxy;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.limewire.util.OSUtils;
import org.limewire.util.StringUtils;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Manages application resources, including the custom <tt>LookAndFeel</tt>,
 * the locale-specific <tt>String</tt> instances, and any <tt>Icon</tt>
 * instances needed by the application.
 */
public final class ResourceManager {
    /**
     * Constant for the relative path of the gui directory.
     */
    private static final String GUI_PATH = "org/limewire/gui/";
    /**
     * Constant for the relative path of the resources directory.
     */
    private static final String RESOURCES_PATH = GUI_PATH + "resources/";
    /**
     * Constant for the relative path of the images directory.
     */
    private static final String IMAGES_PATH = GUI_PATH + "images/";
    /**
     * Cache of theme images (name as String -> image as ImageIcon)
     */
    private static final Map<String, ImageIcon> THEME_IMAGES = new HashMap<>();
    /**
     * Instance of this <tt>ResourceManager</tt>, following singleton.
     */
    private static ResourceManager _instance;
    /**
     * Boolean status that controls whever the shared <tt>Locale</tt> instance
     * needs to be loaded, and locale-specific options need to be setup.
     */
    private static boolean _localeOptionsSet;
    /**
     * Static variable for the loaded <tt>Locale</tt> instance.
     */
    private static Locale _locale;
    /**
     * Marked true in the event of an error in the load/save of any settings file
     */
    private static boolean loadFailureEncountered = false;

    static {
        resetLocaleOptions();
    }

    /**
     * Private constructor to ensure that a <tt>ResourceManager</tt> cannot be
     * constructed from outside this class.
     */
    private ResourceManager() {
    }

    static void resetLocaleOptions() {
        _localeOptionsSet = false;
        setLocaleOptions();
    }

    private static void setLocaleOptions() {
        if (!_localeOptionsSet) {
            if (ApplicationSettings.LANGUAGE.getValue().equals(""))
                ApplicationSettings.LANGUAGE.setValue("en");
            _locale = new Locale(ApplicationSettings.LANGUAGE.getValue(),
                    ApplicationSettings.COUNTRY.getValue(),
                    ApplicationSettings.LOCALE_VARIANT.getValue());
            StringUtils.setLocale(_locale);
            I18n.setLocale(_locale);
            _localeOptionsSet = true;
        }
    }

    /**
     * Returns the <tt>Locale</tt> instance currently in use.
     *
     * @return the <tt>Locale</tt> instance currently in use
     */
    static Locale getLocale() {
        return _locale;
    }

    /**
     * Indicated if a failure has occurred for delayed reporting
     */
    static boolean hasLoadFailure() {
        return loadFailureEncountered;
    }

    /**
     * Resets the failure flag
     */
    static void resetLoadFailure() {
        loadFailureEncountered = false;
    }

    /**
     * Serves as a single point of access for any icons that should be accessed
     * directly from the file system for themes.
     *
     * @param name The name of the image (excluding the extension) to locate.
     * @return a new <tt>ImageIcon</tt> instance for the specified file, or
     * <tt>null</tt> if the resource could not be loaded
     */
    static ImageIcon getThemeImage(final String name) {
        if (name == null)
            throw new NullPointerException("null image name");
        ImageIcon icon;
        // First try to get theme image from cache
        icon = THEME_IMAGES.get(name);
        if (icon != null)
            return icon;
        // Then try to get from org/limewire/gui/images resources
        icon = getImageFromURL(IMAGES_PATH + name, false);
        if (icon != null && icon.getImage() != null) {
            THEME_IMAGES.put(name, icon);
            return icon;
        }
        // no resource? error.
        throw new MissingResourceException(
                "image: " + name + " doesn't exist.", null, null);
    }

    /**
     * Retrieves an icon from the specified path in the filesystem.
     */
    static ImageIcon getImageFromResourcePath(String loc) {
        return getImageFromURL(loc, false);
    }

    /**
     * Retrieves an icon from a URL-style path.
     * <p>
     * If 'file' is true, location is treated as a file, otherwise it is treated
     * as a resource.
     * <p>
     * This tries, in order, the exact location, the location as a png, and the
     * location as a gif.
     */
    private static ImageIcon getImageFromURL(String location, boolean file) {
        // try exact filename first.
        URL img = toURL(location, file);
        if (img != null)
            return new ImageIcon(img);
        // try with png second
        img = toURL(location + ".png", file);
        if (img != null)
            return new ImageIcon(img);
        // try with gif third
        img = toURL(location + ".gif", file);
        if (img != null)
            return new ImageIcon(img);
        return null;
    }

    /**
     * Makes a URL out of a location, as either a file or a resource.
     */
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
     * Returns a new <tt>URL</tt> instance for the specified file in the
     * "resources" directory.
     *
     * @param FILE_NAME the name of the resource file
     * @return a new <tt>URL</tt> instance for the desired file, or
     * <tt>null</tt> if the <tt>URL</tt> could not be loaded
     */
    public static URL getURLResource(final String FILE_NAME) {
        return ResourceManager.getURL(RESOURCES_PATH + FILE_NAME);
    }

    /**
     * Returns a new <tt>URL</tt> instance for the resource at the specified
     * local path. The path should be the full path within the jar file, such
     * as:
     * <p>
     * <p>
     * org/limewire/gui/images/searching.gif
     * <p>
     *
     * @param PATH the path to the resource file within the jar
     * @return a new <tt>URL</tt> instance for the desired file, or
     * <tt>null</tt> if the <tt>URL</tt> could not be loaded
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

    /**
     * Instance accessor following singleton.
     */
    public static synchronized ResourceManager instance() {
        if (_instance == null)
            _instance = new ResourceManager();
        return _instance;
    }

    /**
     * Alters all Fonts in UIManager to use Dialog, to correctly display foreign
     * strings.
     */
    @SuppressWarnings("unused")
    private static boolean checkUIFonts(String newFont, String testString) {
        String[] comps = new String[]{"TextField.font", "PasswordField.font",
                "TextArea.font", "TextPane.font", "EditorPane.font",
                "FormattedTextField.font", "Button.font", "CheckBox.font",
                "RadioButton.font", "ToggleButton.font", "ProgressBar.font",
                "ComboBox.font", "InternalFrame.titleFont", "DesktopIcon.font",
                "TitledBorder.font", "Label.font", "List.font",
                "TabbedPane.font", "Table.font", "TableHeader.font",
                "MenuBar.font", "Menu.font", "Menu.acceleratorFont",
                "MenuItem.font", "MenuItem.acceleratorFont", "PopupMenu.font",
                "CheckBoxMenuItem.font", "CheckBoxMenuItem.acceleratorFont",
                "RadioButtonMenuItem.font",
                "RadioButtonMenuItem.acceleratorFont", "Spinner.font",
                "Tree.font", "ToolBar.font", "OptionPane.messageFont",
                "OptionPane.buttonFont", "ToolTip.font",};
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

    /**
     * Updates the font of a given fontName to be newName.
     */
    private static boolean checkFont(String fontName, String newName,
                                     String testString, boolean force) {
        boolean displayable = true;
        Font f = UIManager.getFont(fontName);
        if (f != null && !newName.equalsIgnoreCase(f.getName())) {
            if (!GUIUtils.canDisplay(f, testString) || force) {
                f = new Font(newName, f.getStyle(), f.getSize());
                if (GUIUtils.canDisplay(f, testString))
                    UIManager.put(fontName, f);
                else
                    displayable = false;
            }
        } else if (f != null) {
            displayable = GUIUtils.canDisplay(f, testString);
        } else {
            displayable = false;
        }
        return displayable;
    }

    /**
     * Updates the component to use the native UI resource.
     */
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
            } catch (NoSuchMethodException | ClassCastException | SecurityException | InstantiationException | IllegalAccessException | LinkageError | ClassNotFoundException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        // if any of those failed, default to the current UI.
        if (ret == null)
            ret = UIManager.getUI(c);
        return ret;
    }

    /**
     * Determines if a system tray icon is available.
     */
    public boolean isTrayIconAvailable() {
        return (OSUtils.isWindows() || OSUtils.isLinux()) && NotifyUserProxy.instance().supportsSystemTray();
    }
}
