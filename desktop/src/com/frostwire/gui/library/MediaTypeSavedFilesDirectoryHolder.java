/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.library;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.NamedMediaType;

import javax.swing.*;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public class MediaTypeSavedFilesDirectoryHolder implements DirectoryHolder {
    private final MediaType type;
    private final Set<File> cache;

    public MediaTypeSavedFilesDirectoryHolder(MediaType type) {
        this.type = type;
        cache = new HashSet<>();
    }

    public MediaType getMediaType() {
        return type;
    }

    public boolean accept(File file) {
        return type.matches(file.getName());
    }

    public Icon getIcon() {
        NamedMediaType nmt = NamedMediaType.getFromMediaType(type);
        return nmt.getIcon();
    }

    public String getName() {
        return NamedMediaType.getFromMediaType(type).getName();
    }

    public String getDescription() {
        return I18n.tr("Holds the Results for") + " " + type.getDescriptionKey();
    }

    public File getDirectory() {
        return null;
    }

    public File[] getFiles() {
        return new File[0];
    }

    public int size() {
        return 0;
    }

    public Set<File> getCache() {
        return cache;
    }

    public void addToCache(List<File> files) {
        cache.addAll(files);
    }

    public void clearCache() {
        cache.clear();
    }
}
