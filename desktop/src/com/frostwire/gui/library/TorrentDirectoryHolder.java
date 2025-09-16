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
