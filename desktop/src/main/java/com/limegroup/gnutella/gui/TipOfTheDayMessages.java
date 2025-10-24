package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;

class TipOfTheDayMessages {
    private static final String FIRST_MESSAGE = I18n.tr("<a href=\"{0}\"><h2>Support FrostWire Today</h2></a><br>The FrostWire installer features no bundled offers or advertisement and you can download it for free. You can help support the continued development of FrostWire with your <a href=\"{0}\">donations</a>.<br><br>You can donate via:<ul><li><a href=\"{1}\">Paypal</a></li><li><a href=\"{2}\">BTC</a></li><li><a href=\"{3}\">ETH or ERC-20 tokens</a></li></ul><br>Thank you for your continued support.",
            "https://www.frostwire.com/?id=donate&amt=open",
            "https://www.frostwire.com/?id=donate&amt=open",
            "https://www.frostwire.com/bitcoin",
            "https://www.frostwire.com/ethereum");

    /**
     * Determines whether or not the current locale language is English. Note
     * that the user setting may be empty, defaulting to the running system
     * locale which may be other than English. Here we check the effective
     * locale seen in the MessagesBundle.
     */
    static boolean hasLocalizedMessages() {
        return GUIMediator.isEnglishLocale() || !FIRST_MESSAGE.equals(I18n.tr(FIRST_MESSAGE));
    }

    /**
     * Returns general tips that are shown on all operating systems.
     */
    public static String[] getGeneralMessages() {
        return new String[]{
                I18n.tr(FIRST_MESSAGE),
                I18n.tr("Use the search box to enter search keywords or to paste video urls so that you can backup your video content from social media posts or to archive and time-shift free and legal video content for offline use. Always make sure to respect your local copyright laws."),
                (!OSUtils.isWindowsAppStoreInstall()) ?
                        "<h2><a href=\"https://www.frostwire.com/android\">" + I18n.tr("Try FrostWire Plus for Android free today") + "</a></h2>" :
                        "<h2><a href=\"https://www.frostwire.com/give\">" + I18n.tr("Keep FrostWire Free") + "</a></h2>",
                I18n.tr("Tired of downloads stopping halfway through? It helps to pick search results with a higher number in the 'Seeds' column. The Seeds are the amount of unique places on the network that are hosting the file. The more sources, the better the chance of getting your torrent file!"),
                I18n.tr("<h2>Follow us</h2> We are on <a href=\"{0}\">Twitter</a>, <a href=\"{1}\">Instagram</a> and <a href=\"{2}\">Facebook</a>", "https://twitter.com/frostwire", "https://instagram.com/frostwire", "https://www.facebook.com/FrostwireOfficial/"),
                I18n.tr("You can change the look and feel of FrostWire by going to View &gt; Use Small Icons, Show Icon Text and Increase-Decrease the Font Size."),
                I18n.tr("<h2>Join the FrostWire Community</h2><ul><li><a href=\"{0}\">Discord Chat</a></li><li><a href=\"{1}\">Reddit</a></li></ul>", "https://www.frostwire.com/chat", "https://reddit.com/r/frostwire"),
                I18n.tr("You can sort your search results by clicking on a column. The most useful column to sort by is the 'Seeds' column if you are looking for a torrent, Seeds represents an approximate number of computers that have the entire file and are online."),
                I18n.tr("It helps the network if you keep your FrostWire running. Others will connect to the network easier and searches will perform better."),
                I18n.tr("<h2><a href=\"{0}\">Use a VPN encrypted connection</a></h2>Ensure your privacy and anonymity to prevent any outside party from monitoring what you download", "https://www.frostwire.com/vpn"),
                I18n.tr("Passionate about digital rights? Visit the <a href=\"{0}\">Electronic Frontier Foundation</a> and see what you can do to help.", "https://www.eff.org/"),
                I18n.tr("Small variations in the search title will still work. For example, if your buddy is sharing 'Frosty' but you searched for 'My Frosty', your buddy's file will still be found."),
                I18n.tr("<h2><a href=\"{0}\">Use a VPN encryption connection</a></h2>Make it harder for third parties to track your online activities and prevent them from charging you different prices and displaying intrusive advertising based on your IP address’ browsing history", "https://www.frostwire.com/vpn"),
                I18n.tr("Are you behind a firewall? At the bottom of FrostWire in the status bar, look for the globe. If there is a brick wall in front of it, your Internet connection is firewalled."),
                I18n.tr("The numbers next to the up and down arrows in the status bar at the bottom of FrostWire show how fast all of your files are downloading or uploading combined."),
                I18n.tr("You can increase the text size via <font " +
                        "color=\"185ea8\">View</font> &gt; <font " +
                        "color=\"185ea8\">Increase Font Size</font>."),
                I18n.tr("Be careful not to share sensitive information like tax documents, passwords, etc. The torrents you are seeding are accesible by everyone on the network as long as they have the .torrent or a (magnet) link to it."),
                I18n.tr("Unlike other peer-to-peer file-sharing programs, FrostWire can transfer files even if both parties are behind a firewall. You don't have to do anything extra because it happens automatically!"),
                I18n.tr("Want to share a large file? Send several hundred gigabytes with no problems at all, just make sure you leave your FrostWire running and seeding while your friend(s) are downloading. They can pause and resume the download all they want as long as you or somebody else is seeding the same content."),
                I18n.tr("Magnet links allow users to download files through FrostWire from a web page. When you put a magnet link on your web page (in the 'href' attribute of anchor tags), and a user clicks the link, a download will start in FrostWire."),
                I18n.tr("Don't want to seed? Go to Tools &gt; Options &gt; Bittorrent or select the Seeding - No Seeding icon at the bottom bar")
        };
    }

    /**
     * Returns general tips that are shown on operating systems that are <b>not</b>
     * Mac OS X. Useful for tips that reference the About Window or the
     * Tools Window, or right-clicking
     */
    public static String[] getNonMacOSXMessages() {
        return new String[]{
                I18n.tr("You can find out which version of FrostWire you are using by choosing 'About FrostWire' from the Help menu."),
                I18n.tr("Be a good network participant, don't close FrostWire if someone is uploading from you."),
                I18n.tr("You can turn autocomplete for searching on or off by choosing 'Options' from the 'Tools' menu, and selecting the option 'Text Autocompletion'."),
                I18n.tr("Want to see dialogs for questions which you previously marked 'Do not display this message again' or 'Always Use This Answer'? Go to Tools &gt; Options, and check 'Revert To Default' under View &gt; FrostWire Popups."),
                I18n.tr("You can ban certain words from appearing in your search results by choosing 'Options' from the 'Tools' menu and adding new words to those listed under Filters &gt; Keywords."),
                I18n.tr("Hate tool tips? Love tool tips? You can turn them on or off in most tables by right-clicking on the column headers and choosing 'More Options'. You can toggle other options here too, like whether or not to sort tables automatically and if you prefer the rows to be striped."),
                I18n.tr("You can sort uploads, downloads, etc..., by clicking on a column. The table keeps resorting as the information changes. You can turn this automatic-sorting behavior off by right-clicking on a column header, choosing 'More Options' and un-checking 'Sort Automatically'."),
                I18n.tr("Are you unhappy with the small number of search results you received? Right-click a search result, then choose Search More, then Get More Results."),
        };
    }

    /**
     * Returns general tips that are shown on Mac OS X.
     */
    public static String[] getMacOSXMessages() {
        return new String[]{
                I18n.tr("You can find out which version of FrostWire you are using by choosing 'About FrostWire' from the FrostWire menu."),
                I18n.tr("Be a good network participant, don't close FrostWire if someone is uploading from you."),
                I18n.tr("You can turn autocomplete for searching on or off by choosing 'Tools' from the 'FrostWire' menu, and changing the value of 'Autocomplete Text' under the 'View' option."),
                I18n.tr("Want to see dialogs for questions which you previously marked 'Do not display this message again' or 'Always Use This Answer'? Go to FrostWire &gt; Tools, Options and check 'Revert To Default' under View &gt; FrostWire Popups."),
                I18n.tr("You can ban certain words from appearing in your search results by choosing 'Tools' from the 'FrostWire' menu and adding new words to those listed under Filters &gt; Keywords."),
                I18n.tr("Hate tool tips? Love tool tips? You can turn them on or off in most tables by control-clicking on the column headers and choosing 'More Options'. You can toggle other options here too, like whether or not to sort tables automatically and if you prefer the rows to be striped."),
                I18n.tr("You can sort uploads, downloads, etc..., by clicking on a column. The table keeps resorting as the information changes. You can turn this automatic-sorting behavior off by control-clicking on a column header, choosing 'More Options' and un-checking 'Sort Automatically'."),
                I18n.tr("FrostWire's Library is a file manager, not just an MP3 Playlist. That means that when you delete a file from the Library, you have the option to either permanently delete the file from your computer or move it to the Trash."),
                I18n.tr("Want to play music in your default media player instead of in FrostWire? go to 'Tools' on FrostWire menu and select the option 'Play with native media player'."),
                I18n.tr("Make FrostWire's block more results by making the filter stricter. Adjust that and more by going to FrostWire &gt; Tools &gt; Filters &gt;"),
        };
    }

    /**
     * Returns general tips that are shown on Windows.
     */
    public static String[] getWindowsMessages() {
        return new String[]{
                I18n.tr("The icons that you see next to your search results in the '?' column are symbols of the program used to open that particular type of file. To change the program associated with a file, go to the 'Folder Options' in Windows Control Panel. This is a Windows setting, not a FrostWire setting."),
                I18n.tr("FrostWire's Library is a file manager, not just an MP3 Playlist. That means that when you delete a file from the Library, you have the option to either permanently delete the file from your computer or move it to the Recycle Bin."),
                I18n.tr("When you close FrostWire, it minimizes to the system tray. To exit, right-click the system tray icon (next to the time), and select Exit. You can change this behavior by going to Tools &gt; Options &gt; System Tray."),
        };
    }

    /**
     * Returns general tips that are shown on Linux.
     */
    public static String[] getLinuxMessages() {
        return new String[]{
                I18n.tr("Want to play music in your default media player instead of in FrostWire? go to 'Tools' on FrostWire menu and select the option 'Play with native media player'."),
        };
    }

    /**
     * Returns general tips that are shown operating systems other than Windows, Mac OS X or Linux.
     */
    public static String[] getOtherMessages() {
        return new String[]{
        };
    }

    /**
     * Returns general tips that are shown for FrostWire.
     */
    public static String[] getFrostWireMessages() {
        return new String[]{
                I18n.tr("Thank you for using FrostWire"),
                I18n.tr("Visit us at www.frostwire.com"),
        };
    }
}
