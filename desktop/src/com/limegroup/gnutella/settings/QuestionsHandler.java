package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.SettingsFactory;

/**
 * Controls all 'Do not ask this again' or 'Always use this answer' questions.
 */
public class QuestionsHandler extends LimeWireSettings {
    private static final QuestionsHandler INSTANCE =
            new QuestionsHandler();
    private static final SettingsFactory FACTORY =
            INSTANCE.getFactory();
    /**
     * Setting for removing the last column
     */
    public static final BooleanSetting REMOVE_LAST_COLUMN =
            FACTORY.createBooleanSetting("REMOVE_LAST_COLUMN", false);
    /**
     * Settings for whether or not to display a message that no
     * internet connection is detected and the user has been notified that
     * LimeWire will automatically keep trying to connect.
     */
    public static final BooleanSetting NO_INTERNET_RETRYING =
            FACTORY.createBooleanSetting("NO_INTERNET_RETRYING ", false);
    //////////// The actual questions ///////////////
    /**
     * Initial warning for first download.
     */
    public static final IntSetting SKIP_FIRST_DOWNLOAD_WARNING =
            FACTORY.createIntSetting("SHOW_FIRST_DOWNLOAD_WARNING", 0);
    /**
     * Setting for whether or not to display a message that a .torrent file
     * could not be opened
     */
    public static final BooleanSetting TORRENT_OPEN_FAILURE =
            FACTORY.createBooleanSetting("TORRENT_OPEN_FAILURE ", false);
    /**
     * Stores whether the user wants to overwrite or append to songs in the
     * playlist
     */
    public static final IntSetting PLAYLIST_OVERWRITE_OK =
            FACTORY.createIntSetting("PLAYLIST_OVERWRITE_OK", 0);
    /**
     * Whether we should always grab associations
     */
    public static final IntSetting GRAB_ASSOCIATIONS =
            FACTORY.createIntSetting("GRAB_ASSOCIATIONS", 0);

    private QuestionsHandler() {
        super("questions.props", "FrostWire questions file");
    }

    public static QuestionsHandler instance() {
        return INSTANCE;
    }
}


