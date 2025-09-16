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

package com.limegroup.gnutella.gui.shell;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.limewire.util.CommonUtils;
import com.frostwire.util.OSUtils;
import org.limewire.util.SystemUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class FrostAssociations {
    /**
     * A constant for the current associations "level"
     * increment this when adding new associations
     */
    public static final int CURRENT_ASSOCIATIONS = 2;
    private static final String PROGRAM;
    private static final String UNSUPPORTED_PLATFORM = "";
    private static Collection<LimeAssociationOption> options;

    static {
        if (OSUtils.isWindows())
            PROGRAM = "FrostWire";
        else if (OSUtils.isUnix())
            PROGRAM = System.getProperty("unix.executable", UNSUPPORTED_PLATFORM);
        else
            PROGRAM = UNSUPPORTED_PLATFORM;
    }

    public synchronized static Collection<LimeAssociationOption> getSupportedAssociations() {
        if (options == null)
            options = getSupportedAssociationsImpl();
        return options;
    }

    public synchronized static boolean anyAssociationsSupported() {
        return !getSupportedAssociations().isEmpty();
    }

    private static Collection<LimeAssociationOption> getSupportedAssociationsImpl() {
        if (CommonUtils.isPortable()) {
            return Collections.emptyList();
        }
        Collection<LimeAssociationOption> ret = new ArrayList<>();
        // strings that the shell will understand
        String fileOpener = null;
        String fileIcon = null;
        String protocolOpener = null;
        if (OSUtils.isWindows()) {
            String runningPath = SystemUtils.getRunningPath();
            // only to test associations
            if (CommonUtils.isDebugMode()) {
                runningPath = PROGRAM + ".exe";
            }
            if (runningPath != null && runningPath.endsWith(PROGRAM + ".exe")) {
                protocolOpener = runningPath;
                fileOpener = "\"" + runningPath + "\" \"%1\"";
                fileIcon = runningPath.replace(".exe", ".ico");
                //fileIcon = runningPath+",1";
            }
        }
        // if we have a string that opens a file, register torrents
        if (fileOpener != null) {
            if (OSUtils.isWindows()) { // Windows users
                ShellAssociation tor = new FileTypeAssociation("torrent",
                        "application/x-bittorrent",
                        fileOpener, "open",
                        I18n.tr("FrostWire Torrent"),
                        fileIcon);
                LimeAssociationOption torrentwin =
                        new LimeAssociationOption(tor,
                                ApplicationSettings.HANDLE_TORRENTS,
                                ".torrent",
                                I18n.tr("\".torrent\" files"));
                ret.add(torrentwin);
            } else //Mac, Linux
            {
                ShellAssociation file = new FileTypeAssociation("torrent",
                        "Application/x-bittorrent", fileOpener, "open",
                        I18n.tr("FrostWire Torrent"), fileIcon);
                LimeAssociationOption torrent = new LimeAssociationOption(
                        file,
                        ApplicationSettings.HANDLE_TORRENTS,
                        ".torrent",
                        I18n.tr("\".torrent\" files"));
                ret.add(torrent);
            }
        }
        // if we have a string that opens a protocol, register magnets
        if (protocolOpener != null) {
            // Note: MagnetAssociation will only work on windows
            MagnetAssociation mag = new MagnetAssociation(PROGRAM, protocolOpener);
            LimeAssociationOption magOption = new LimeAssociationOption(
                    mag,
                    ApplicationSettings.HANDLE_MAGNETS,
                    "magnet:", I18n.tr("\"magnet:\" links"));
            ret.add(magOption);
        }
        return Collections.unmodifiableCollection(ret);
    }
}
