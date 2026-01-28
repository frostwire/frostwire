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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.tables.IconAndNameHolder;
import org.limewire.collection.FixedsizeForgetfulHashMap;

import javax.swing.*;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Associates a MediaType with a LimeXMLSchema.
 * <p>
 * Also contains factory methods for retrieving all media types,
 * and retrieving the media type associated with a specific TableLine.
 */
public class NamedMediaType implements IconAndNameHolder, Comparable<NamedMediaType> {
    /**
     * image resource directory.
     */
    private static final String IMAGE_RESOURCE_PATH = "org/limewire/xml/image/";
    /**
     * The cached mapping of description -> media type,
     * for easy looking up from incoming results.
     * Limited to 100 entries to prevent unbounded growth (though in practice,
     * the number of media types is naturally bounded by file extensions).
     */
    private static final Map<String, NamedMediaType> CACHED_TYPES = new FixedsizeForgetfulHashMap<>(100);
    /**
     * The MediaType this is describing.
     */
    private final MediaType _mediaType;
    /**
     * The name used to describe this MediaType/LimeXMLSchema.
     */
    private final String _name;
    /**
     * The icon used to display this mediaType/LimeXMLSchema.
     * Loaded lazily to avoid EDT blocking during initialization.
     */
    private Icon _icon;

    /**
     * Constructs a new NamedMediaType, associating the MediaType with the
     * LimeXMLSchema.
     */
    private NamedMediaType(MediaType mt) {
        if (mt == null)
            throw new NullPointerException("Null media type.");
        this._mediaType = mt;
        this._name = constructName(_mediaType);
        // Defer icon loading to avoid EDT violation
        // Icon loading triggers expensive ClassLoader resource operations (>2 second EDT block)
        this._icon = null;
    }

    /**
     * Retrieves the named media type for the specified schema uri.
     * <p>
     * This should only be used if you are positive that the media type
     * is already cached for this description OR it is not a default
     * type.
     */
    public static NamedMediaType getFromDescription(String description) {
        synchronized (CACHED_TYPES) {
            NamedMediaType type = CACHED_TYPES.get(description);
            if (type != null)
                return type;
        }
        MediaType mt = MediaType.getMediaTypeForSchema(description);
        return getFromMediaType(mt);
    }

    /**
     * Retrieves the named media type from the specified extension.
     * <p>
     * This should only be used if you are positive that the media type
     * is already cached for this extension.
     */
    public static NamedMediaType getFromExtension(String extension) {
        MediaType mt = MediaType.getMediaTypeForExtension(extension);
        if (mt == null)
            return null;
        String description = mt.getMimeType();
        return getFromDescription(description);
    }

    /**
     * Retrieves the named media type for the specified media type.
     */
    public static NamedMediaType getFromMediaType(MediaType media) {
        String description = media.getMimeType();
        synchronized (CACHED_TYPES) {
            NamedMediaType type = CACHED_TYPES.get(description);
            if (type != null)
                return type;
            type = new NamedMediaType(media);
            CACHED_TYPES.put(description, type);
            return type;
        }
    }

    /**
     * Returns the human-readable description of this MediaType/Schema.
     */
    private static String constructName(MediaType type) {
        // If we can act off the MediaType.
        String name = null;
        String key = type.getDescriptionKey();
        try {
            if (key != null)
                name = I18n.tr(key);
        } catch (MissingResourceException mre) {
            // oh well, will capitalize the mime-type
        }
        // If still no name, capitalize the mime-type.
        if (name == null) {
            name = type.getMimeType();
            name = name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
        }
        return name;
    }

    /**
     * Compares this NamedMediaType to another.
     */
    public int compareTo(NamedMediaType other) {
        return _name.compareTo(other._name);
    }

    /**
     * Returns the name of this NamedMediaType.
     */
    public String getName() {
        return _name;
    }

    /**
     * Returns the icon representing this NamedMediaType.
     * Loads the icon lazily on first access to avoid EDT blocking during initialization.
     */
    public Icon getIcon() {
        if (_icon == null) {
            _icon = loadIcon(_mediaType);
        }
        return _icon;
    }

    /**
     * Returns the description of this NamedMediaType.
     */
    public String toString() {
        return _name;
    }

    /**
     * Returns the media type this is wrapping.
     */
    public MediaType getMediaType() {
        return _mediaType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return obj instanceof NamedMediaType && _name.equals(((NamedMediaType) obj)._name);
    }

    @Override
    public int hashCode() {
        // Don't include icon in hashCode to avoid forcing icon loading
        return _mediaType.hashCode() + _name.hashCode();
    }

    /**
     * Retrieves the icon representing the MediaType/Schema.
     */
    private Icon loadIcon(MediaType type) {
        final ImageIcon icon;
        if (type == MediaType.getAnyTypeMediaType())
            icon = GUIMediator.getThemeImage("lime");
        else {
            String location = IMAGE_RESOURCE_PATH + type.getMimeType();
            icon = GUIMediator.getImageFromResourcePath(location);
            if (icon == null) {
                return new GUIUtils.EmptyIcon(getName(), 16, 16);
            }
        }
        icon.setDescription(getName());
        return icon;
    }
}
