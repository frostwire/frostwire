package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.StringArraySetting;
import org.limewire.setting.StringSetting;
import org.limewire.util.CommonUtils;

/**
 * Settings for iTunes
 */
public class iTunesSettings extends LimeProps {

    private iTunesSettings() {
    }

    /**
     * Whether or not player should be enabled.
     */
    public static BooleanSetting ITUNES_SUPPORT_ENABLED = FACTORY.createBooleanSetting("ITUNES_SUPPORT_ENABLED", !CommonUtils.isPortable());

    /**
     * The name of the Playlist where songs shall be imported
     */
    public static StringSetting ITUNES_PLAYLIST = FACTORY.createStringSetting("ITUNES_PLAYLIST", "FrostWire");

    /**
     * Supported file types
     */
    public static StringArraySetting ITUNES_SUPPORTED_FILE_TYPES = FACTORY.createStringArraySetting("ITUNES_SUPPORTED_FILE_TYPES", new String[] { "mp3", "aif",
            "aiff", "wav", "mp2", "mp4", "aac", "mid", "m4a", "m4p", "ogg" });
}
