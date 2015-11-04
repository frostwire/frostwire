package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.SettingsFactory;
import org.limewire.setting.StringSetting;



/**
 * Controls all 'Do not ask this again' or 'Always use this answer' questions.
 */
public class QuestionsHandler extends LimeWireSettings {

    private static final QuestionsHandler INSTANCE =
        new QuestionsHandler();
    private static final SettingsFactory FACTORY =
        INSTANCE.getFactory();

    private QuestionsHandler() {
        super("questions.props", "FrostWire questions file");
    }

    public static QuestionsHandler instance() {
        return INSTANCE;
    }

    //////////// The actual questions ///////////////

    /**
    * Setting for whether or not to allow multiple instances of LimeWire.
    */
    public static final BooleanSetting MONITOR_VIEW =
        FACTORY.createBooleanSetting("MONITOR_VIEW", false);

    /**
     * Setting for whether or not to ask about discarding corrupt downloads
     */
    public static final IntSetting CORRUPT_DOWNLOAD =
        FACTORY.createIntSetting("CORRUPT_DOWNLOAD", 0);

    /**
     * Setting for whether or not to display a browse host failed
     */
    public static final BooleanSetting BROWSE_HOST_FAILED =
        FACTORY.createBooleanSetting("BROWSE_HOST_FAILED", false);

    /**
     * Setting for unsharing directory
     */
    public static final IntSetting UNSHARE_DIRECTORY =
        FACTORY.createIntSetting("UNSHARE_DIRECTORY", 0);

    /**
     * Setting for the theme changed message
     */
    public static final BooleanSetting THEME_CHANGED =
        FACTORY.createBooleanSetting("THEME_CHANGED", false);

    /**
     * Setting for already downloading message
     */
    public static final BooleanSetting ALREADY_DOWNLOADING =
        FACTORY.createBooleanSetting("ALREADY_DOWNLOADING", false);

    /**
     * Setting for removing the last column
     */
    public static final BooleanSetting REMOVE_LAST_COLUMN =
        FACTORY.createBooleanSetting("REMOVE_LAST_COLUMN", false);

    /**
     * Setting for being unable to resume an incomplete file
     */
    public static final BooleanSetting CANT_RESUME =
        FACTORY.createBooleanSetting("CANT_RESUME", false);
        
	/**
     * Setting for whether or not program should ignore prompting
     * for incomplete files.
     */
    public static final IntSetting PROMPT_FOR_EXE =
        FACTORY.createIntSetting("PROMPT_FOR_EXE", 0);
        
    /**
     * Settings for whether or not to apply a new theme after
     * downloading it
     */
    public static final IntSetting THEME_DOWNLOADED =
        FACTORY.createIntSetting("THEME_DOWNLOADED", 0);
        
    /**
     * Settings for whether or not to display a message that no
     * internet connection is detected.
     */
    public static final BooleanSetting NO_INTERNET =
        FACTORY.createBooleanSetting("NO_INTERNET", false);

    /**
     * Settings for whether or not to display a message that no
     * internet connection is detected and the user has been notified that 
     * LimeWire will automatically keep trying to connect.
     */
    public static final BooleanSetting NO_INTERNET_RETRYING =
        FACTORY.createBooleanSetting("NO_INTERNET_RETRYING ", false);

    /**
     * Settings for whether or not to display a message that a failed preview
     * should be ignored.
     */
    public static final BooleanSetting NO_PREVIEW_REPORT =
        FACTORY.createBooleanSetting("NO_PREVIEW_REPORT ", false);

    /**
     * Settings for whether or not to display a message if searching
     * while not connected.
     */
    public static final BooleanSetting NO_NOT_CONNECTED =
        FACTORY.createBooleanSetting("NO_NOT_CONNECTED", false);

    /**
     * Settings for whether or not to display a message if searching
     * while still connecting.
     */
    public static final BooleanSetting NO_STILL_CONNECTING =
        FACTORY.createBooleanSetting("NO_STILL_CONNECTING", false);
	
	/**
	 * Setting for whether or not to display a warning message if one of the
	 * created magnet links contains a firewalled address.
	 */
	public static final BooleanSetting FIREWALLED_MAGNET_LINK = 
		FACTORY.createBooleanSetting("FIREWALLED_MAGNET_LINK", false);

    /**
     * Initial warning for first download.
     */
    public static final IntSetting SKIP_FIRST_DOWNLOAD_WARNING =
        FACTORY.createIntSetting("SHOW_FIRST_DOWNLOAD_WARNING", 0);
    
    /**
	 * Default action for situations when trying to download existing file.
	 * 0 - ask - default
	 * 1 - append (#)
	 * 2 - save as
	 */
    public static final IntSetting DEFAULT_ACTION_FILE_EXISTS =
        FACTORY.createIntSetting("DEFAULT_ACTION_FILE_EXISTS", 0);
    
    /**
     * Whether we should always grab associations 
     */
    public static IntSetting GRAB_ASSOCIATIONS =
    	FACTORY.createIntSetting("GRAB_ASSOCIATIONS",0);
    
    /**
     * Setting for whether or not to display a message that a .torrent file 
     * could not be opened 
     */
    public static final BooleanSetting TORRENT_OPEN_FAILURE =
        FACTORY.createBooleanSetting("TORRENT_OPEN_FAILURE ", false);

    /**
     * Setting for whether or not to display a message that a .torrent file 
     * could not be downloaded 
     */
    public static final BooleanSetting TORRENT_DOWNLOAD_FAILURE =
        FACTORY.createBooleanSetting("TORRENT_DOWNLOAD_FAILURE ", false);
    
    /**
     * Setting for whether or not to display a message that cancelling a 
     * torrent upload will kill its corresponding download
     */
    public static final IntSetting TORRENT_STOP_UPLOAD =
    	FACTORY.createIntSetting("TORRENT_STOP_UPLOAD", 0);
    
    /**
     * Setting for whether or not to display a message that the user
     * should let a seeding torrent reach 1:1 ratio.
     */
    public static final IntSetting TORRENT_SEED_MORE =
    	FACTORY.createIntSetting("TORRENT_SEED_MORE", 0);
	
	/**
	 * Setting for whether or to display a message when the user dropped files
	 * on limewire to share them, did not select any but still clicked OK.
	 */
    public static final BooleanSetting HIDE_EMPTY_DROPPED_SHARE_DIALOG =
    	FACTORY.createBooleanSetting("HIDE_EMPTY_DROPPED_SHARE_DIALOG", false);
    
    /** Setting for whether or not LimeWire should display a warning if the user
     * chooses to use custom settings for Bittorrent.
     */
    public static final BooleanSetting BITTORRENT_CUSTOM_SETTINGS =
        FACTORY.createBooleanSetting("BITTORRENT_CUSTOM_SETTINGS", false);
    
    /**
     *  Setting for whether or not LimeWire should display a warning if the user
     *  chooses to save to a non-home location on Windows Vista.
     */
    public static final IntSetting VISTA_SAVE_LOCATION =
        FACTORY.createIntSetting("VISTA_SAVE_LOCATION", 0);
    
    /**
     * Stores the last Java version that an upgrade recommendation was displayed
     * for.
     */
    public static final StringSetting LAST_CHECKED_JAVA_VERSION =
        FACTORY.createStringSetting("LAST_CHECKED_JAVA_VERSION", "");
    
    /**
     * Stores whether the user wants to overwrite or append to songs in the
     * playlist
     */
    public static final IntSetting PLAYLIST_OVERWRITE_OK =
        FACTORY.createIntSetting("PLAYLIST_OVERWRITE_OK", 0);


    /**
     * Stores whether the user wants to be warned about large numbers
     *  of files being shared
     */
    public static final BooleanSetting DONT_WARN_SHARING_NUMBER =
        FACTORY.createBooleanSetting("DONT_WARN_SHARING_NUMBER", false);
    
    /**
     * Stores whether the user wants to be warned about folders being shared
     *  to an excessive depth
     */
    public static final BooleanSetting DONT_WARN_SHARING_DEPTH =
        FACTORY.createBooleanSetting("DONT_WARN_SHARING_DEPTH", false);
    

}


