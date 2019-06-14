package com.limegroup.gnutella.settings;

import org.limewire.setting.BasicSettingsGroup;
import org.limewire.util.CommonUtils;

import java.io.File;

class LimeWireSettings extends BasicSettingsGroup {
    LimeWireSettings(String filename, String header) {
        super(new File(CommonUtils.getUserSettingsDir(), filename), header);
    }
}
