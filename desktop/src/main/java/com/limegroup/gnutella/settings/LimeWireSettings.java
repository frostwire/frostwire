package com.limegroup.gnutella.settings;

import java.io.File;
import org.limewire.setting.BasicSettingsGroup;
import org.limewire.util.CommonUtils;

class LimeWireSettings extends BasicSettingsGroup {
  LimeWireSettings(String filename, String header) {
    super(new File(CommonUtils.getUserSettingsDir(), filename), header);
  }
}
