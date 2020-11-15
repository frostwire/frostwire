package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;

/**
 * Settings for Status Bar
 */
public class StatusBarSettings extends LimeProps {
    /**
     * Whether or not connection quality status should be displayed.
     */
    public static final BooleanSetting CONNECTION_QUALITY_DISPLAY_ENABLED =
            FACTORY.createBooleanSetting("CONNECTION_QUALITY_DISPLAY_ENABLED", true);
    /**
     * Whether or not language status should be displayed when not using English.
     */
    public static final BooleanSetting LANGUAGE_DISPLAY_ENABLED =
            FACTORY.createBooleanSetting("LANGUAGE_DISPLAY_ENABLED", true);
    /**
     * Whether or not language status should be displayed when using English.
     */
    public static final BooleanSetting LANGUAGE_DISPLAY_ENGLISH_ENABLED =
            FACTORY.createBooleanSetting("LANGUAGE_DISPLAY_ENGLISH_ENABLED", true);
    /**
     * Whether or not firewall status should be displayed.
     */
    public static final BooleanSetting FIREWALL_DISPLAY_ENABLED =
            FACTORY.createBooleanSetting("FIREWALL_DISPLAY_ENABLED", true);
    /**
     * Whether or not firewall status should be displayed.
     */
    public static final BooleanSetting VPN_DISPLAY_ENABLED =
            FACTORY.createBooleanSetting("VPN_DISPLAY_ENABLED", true);
    /**
     * Whether or not bandwidth consumption should be displayed.
     */
    public static final BooleanSetting BANDWIDTH_DISPLAY_ENABLED =
            FACTORY.createBooleanSetting("BANDWIDTH_DISPLAY_ENABLED", true);
    public static final BooleanSetting DONATION_BUTTONS_DISPLAY_ENABLED =
            FACTORY.createBooleanSetting("DONATION_BUTTONS_DISPLAY_ENABLED", true);

    private StatusBarSettings() {
    }
}
