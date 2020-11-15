package com.limegroup.gnutella.settings;

import org.limewire.setting.StringSetting;

/**
 * Settings for programs LimeWire should open to view files on unix.
 */
public final class URLHandlerSettings extends LimeProps {
    /**
     * Setting for which browser to use
     */
    public static final StringSetting BROWSER =
            FACTORY.createStringSetting("BROWSER", "firefox $URL$");
    /**
     * Setting for which movie player to use
     */
    public static final StringSetting VIDEO_PLAYER =
            FACTORY.createStringSetting("VIDEO_PLAYER", "vlc $URL$");
    /**
     * Setting for which image viewer to use
     */
    public static final StringSetting IMAGE_VIEWER =
            FACTORY.createStringSetting("IMAGE_VIEWER", "firefox $URL$");
    /**
     * Setting for which audio player to use
     */
    public static final StringSetting AUDIO_PLAYER =
            FACTORY.createStringSetting("AUDIO_PLAYER", "vlc $URL$");

    private URLHandlerSettings() {
    }
}

