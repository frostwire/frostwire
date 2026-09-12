package com.limegroup.gnutella.settings;

import java.io.File;
import org.limewire.setting.BooleanSetting;
import org.limewire.setting.FileSetting;
import org.limewire.setting.IntSetting;
import org.limewire.util.CommonUtils;

/** Settings to deal with bugs */
public class BugSettings extends LimeProps {
  /**
   * Setting for whether or not bugs should be logged locally. Developers can easily change this if
   * they wish to see all bugs logged to disk for future review.
   */
  public static final BooleanSetting LOG_BUGS_LOCALLY =
      FACTORY.createBooleanSetting("LOG_BUGS_LOCALLY", true);

  /** Setting for the filename of the local bugfile log. */
  public static final FileSetting BUG_LOG_FILE =
      FACTORY.createFileSetting(
          "BUG_LOG_FILE", new File(CommonUtils.getUserSettingsDir(), "bugs.log"));

  /** Setting for the maximum filesize of the buglog. */
  public static final IntSetting MAX_BUGFILE_SIZE =
      FACTORY.createIntSetting("MAX_BUGFILE_SIZE", 1024 * 1024); // 1MB

  /** Setting for whether or not deadlock bugs should be sent. */
  public static final BooleanSetting SEND_DEADLOCK_BUGS =
      FACTORY.createBooleanSetting("SEND_DEADLOCK_BUGS", true);

  private BugSettings() {}
}
