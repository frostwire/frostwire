package com.limegroup.gnutella.settings;

import java.io.File;

import org.limewire.setting.BooleanSetting;
import org.limewire.util.CommonUtils;

/**
 * Settings for messages
 */
public class UpdateSettings extends LimeProps {

    private UpdateSettings() {
    }

    /**
     * Wether or not it should download updates automatically. This does not mean it will install the update,
     * it'll just download the installer for the user and then let the user know next time he/she restarts
     * FrostWire.
     */
    public static final BooleanSetting AUTOMATIC_INSTALLER_DOWNLOAD = FACTORY.createBooleanSetting("AUTOMATIC_INSTALLER_DOWNLOAD", true);

    public static final File UPDATES_DIR = new File(CommonUtils.getUserSettingsDir(), "updates");
}
