package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.settings.UISettings;
import org.apache.commons.io.IOUtils;

import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;

class ButtonIconController {
    /**
     * A mapping of user-friendly names to the file name
     * of the icon.
     */
    private final Properties /* String -> String */ BUTTON_NAMES =
            loadButtonNameMap();
    /**
     * A mapping of the file name of the icon to the icon itself,
     * so we don't load the resource multiple times.
     */
    private final Map<String, Icon> BUTTON_CACHE = new HashMap<>();
    private final Icon NULL = new ImageIcon();

    private static Properties loadButtonNameMap() {
        Properties p = new Properties();
        URL url = ResourceManager.getURLResource("icon_mapping.properties");
        InputStream is = null;
        try {
            if (url != null) {
                is = new BufferedInputStream(url.openStream());
                p.load(is);
            }
        } catch (IOException ignored) {
        } finally {
            IOUtils.closeQuietly(is);
        }
        return p;
    }

    /**
     * Wipes out the button icon cache, so we can switch from large to small
     * icons (or vice versa).
     */
    public void wipeButtonIconCache() {
        BUTTON_CACHE.clear();
    }

    /**
     * Retrieves the icon for the specified button name.
     */
    public Icon getIconForButton(String buttonName) {
        if (buttonName == null) {
            return null;
        }
        String fileName = BUTTON_NAMES.getProperty(buttonName);
        if (fileName == null)
            return null;
        Icon icon = BUTTON_CACHE.get(fileName);
        if (icon == NULL)
            return null;
        if (icon != null)
            return icon;
        try {
            String retrieveName;
            if (UISettings.SMALL_ICONS.getValue())
                retrieveName = fileName + "_small";
            else
                retrieveName = fileName + "_large";
            icon = ResourceManager.getThemeImage(retrieveName);
            BUTTON_CACHE.put(fileName, icon);
        } catch (MissingResourceException mre) {
            // if neither small nor large existed, try once as exact
            try {
                icon = ResourceManager.getThemeImage(fileName);
                BUTTON_CACHE.put(fileName, icon);
            } catch (MissingResourceException mre2) {
                BUTTON_CACHE.put(fileName, NULL);
            }
        }
        return icon;
    }

    public Icon getSmallIconForButton(String buttonName) {
        String fileName = BUTTON_NAMES.getProperty(buttonName);
        if (fileName == null)
            return null;
        Icon icon = BUTTON_CACHE.get(fileName);
        if (icon == NULL)
            return null;
        if (icon != null)
            return icon;
        try {
            icon = ResourceManager.getThemeImage(fileName + "_small");
            BUTTON_CACHE.put(fileName, icon);
        } catch (MissingResourceException mre) {
            // if neither small nor large existed, try once as exact
            try {
                icon = ResourceManager.getThemeImage(fileName);
                BUTTON_CACHE.put(fileName, icon);
            } catch (MissingResourceException mre2) {
                BUTTON_CACHE.put(fileName, NULL);
            }
        }
        return icon;
    }

    /**
     * Retrieves the rollover image for the specified button name.
     */
    public Icon getRolloverIconForButton(String buttonName) {
        String fileName = BUTTON_NAMES.getProperty(buttonName);
        if (fileName == null)
            return null;
        // See if we've already cached a brighter icon.
        String rolloverName = fileName + "_rollover";
        Icon rollover = BUTTON_CACHE.get(rolloverName);
        if (rollover == NULL)
            return null;
        if (rollover != null)
            return rollover;
        // Retrieve the initial icon, so we can brighten it.
        Icon icon = BUTTON_CACHE.get(fileName);
        // no icon?  no brightened icon.
        if (icon == NULL || icon == null) {
            BUTTON_CACHE.put(rolloverName, NULL);
            return null;
        }
        // Make a brighter version of the icon, and cache it.
        rollover = ImageManipulator.brighten(icon);
        if (rollover == null)
            BUTTON_CACHE.put(rolloverName, NULL);
        else
            BUTTON_CACHE.put(rolloverName, rollover);
        return rollover;
    }
}
