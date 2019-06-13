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
import com.limegroup.gnutella.settings.SharingSettings;

import javax.swing.*;
import java.io.File;

public class TorrentDirectoryHolder extends FileSettingDirectoryHolder {
    private final MediaType type;

    public TorrentDirectoryHolder() {
        super(SharingSettings.TORRENTS_DIR_SETTING, I18n.tr("Torrents"));
        type = MediaType.getTorrentMediaType();
    }

    public Icon getIcon() {
        NamedMediaType nmt = NamedMediaType.getFromMediaType(type);
        return nmt.getIcon();
    }

    public boolean accept(File file) {
        return super.accept(file) && type.matches(file.getName());
    }
}
