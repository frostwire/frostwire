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

package com.limegroup.gnutella.settings;

import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.BooleanSetting;
import org.limewire.setting.FileSetting;
import org.limewire.util.CommonUtils;

import java.io.File;

/**
 * Settings for sharing
 */
public class SharingSettings extends LimeProps {
    public static final BooleanSetting SEED_FINISHED_TORRENTS = FACTORY.createBooleanSetting("SEED_FINISHED_TORRENTS", true);
    public static final BooleanSetting ENABLE_DISTRIBUTED_HASH_TABLE = FACTORY.createBooleanSetting("ENABLE_DISTRIBUTED_HASH_TABLE", true);
    public static final File IMAGE_CACHE_DIR = new File(CommonUtils.getUserSettingsDir(), "image_cache");
    /**
     * Setting for whether or not to allow partial files to be shared.
     */
    public static final BooleanSetting ALLOW_PARTIAL_SHARING = FACTORY.createBooleanSetting("ALLOW_PARTIAL_SHARING", false);
    private static final File PORTABLE_ROOT_FOLDER = CommonUtils.getPortableRootFolder();
    public static final File DEFAULT_TORRENTS_DIR = new File((PORTABLE_ROOT_FOLDER == null) ? FrostWireUtils.getFrostWireRootFolder() : PORTABLE_ROOT_FOLDER, "Torrents");
    public static final FileSetting TORRENTS_DIR_SETTING = FACTORY.createFileSetting("TORRENTS_DIR_SETTING", DEFAULT_TORRENTS_DIR).setAlwaysSave(true);
    /**
     * The default folder where Torrent Data will be saved. This folder CANNOT BE SHARED
     * to avoid sharing inconsistencies.
     * <p/>
     * In the case of FrostWire Portable, we'll name the default torrent data folder "Downloads"
     * In regular frostwire it's "Torrent Data"
     */
    public static final File DEFAULT_TORRENT_DATA_DIR = (PORTABLE_ROOT_FOLDER == null) ? new File(FrostWireUtils.getFrostWireRootFolder(), "Torrent Data") : new File(PORTABLE_ROOT_FOLDER, "Downloads");
    /**
     * The folder value where Torrent Data will be saved. This folder CANNOT BE SHARED
     * to avoid sharing inconsistencies.
     */
    public static final FileSetting TORRENT_DATA_DIR_SETTING = FACTORY.createFileSetting("DEFAULT_TORRENT_DATA_DIR_SETTING", DEFAULT_TORRENT_DATA_DIR).setAlwaysSave(true);

    private SharingSettings() {
    }

    public static File getImageCacheDirectory() {
        if (!IMAGE_CACHE_DIR.exists()) {
            IMAGE_CACHE_DIR.mkdirs();
        }
        return IMAGE_CACHE_DIR;
    }

    public static void initTorrentDataDirSetting() {
        if (CommonUtils.isPortable()) {
            SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(SharingSettings.DEFAULT_TORRENT_DATA_DIR);
        }
    }

    public static void initTorrentsDirSetting() {
        //in case we changed locations, always reset.
        if (CommonUtils.isPortable()) {
            SharingSettings.TORRENTS_DIR_SETTING.setValue(SharingSettings.DEFAULT_TORRENTS_DIR);
        }
        //in case it's first time
        if (!SharingSettings.TORRENTS_DIR_SETTING.getValue().exists()) {
            SharingSettings.TORRENTS_DIR_SETTING.getValue().mkdirs();
        }
    }
}
