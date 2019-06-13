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

package com.limegroup.gnutella.gui.shell;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;
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
