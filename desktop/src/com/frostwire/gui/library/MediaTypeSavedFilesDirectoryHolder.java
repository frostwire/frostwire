/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
