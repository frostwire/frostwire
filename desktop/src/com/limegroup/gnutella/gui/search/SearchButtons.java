package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.ButtonRow;
import com.limegroup.gnutella.gui.I18n;

import java.util.EventListener;

/**
 * This class contains the buttons in the download window, allowing
 * classes in this package to enable or disable buttons at specific
 * indeces in the row.
 */
final class SearchButtons {
    /**
     * The index of the WishList / Download Button.
     */
    static final int DOWNLOAD_BUTTON_INDEX = 1;
    /**
     * The index of the torrent details button in the button row.
     */
    static final int TORRENT_DETAILS_BUTTON_INDEX = 2;
    /**
     * The index of the Stop Current Search button in the button row.
     */
    static final int STOP_SEARCH_BUTTON_INDEX = 3;
    /**
     * The row of buttons for the donwload window.
     */
    private final ButtonRow BUTTONS;

    /**
     * The constructor creates the row of buttons with their associated
     * listeners.
     */
    SearchButtons(SearchResultMediator rp) {
        String[] buttonLabelKeys = {
                I18n.tr("Options"),
                I18n.tr("Download"),
                I18n.tr("Details"),
                I18n.tr("Stop"),
        };
        String[] buttonTipKeys = {
                I18n.tr("Open Options dialog"),
                I18n.tr("Download All Selected Files"),
                I18n.tr("See detail web page about the selected torrent (Contents, Comments, Seeds)"),
                I18n.tr("Stop current search")
        };
        EventListener[] buttonListeners = {
                rp.CONFIGURE_SHARING_LISTENER,
                rp.DOWNLOAD_LISTENER,
                rp.TORRENT_DETAILS_LISTENER,
                rp.STOP_SEARCH_LISTENER
        };
        String[] iconNames = {
                "LIBRARY_SHARING_OPTIONS",
                "SEARCH_DOWNLOAD",
                "TORRENT_DETAILS",
                "SEARCH_STOP"
        };
        BUTTONS = new ButtonRow(buttonLabelKeys, buttonTipKeys, buttonListeners, iconNames);
    }

    /**
     * Returns the <tt>Component</tt> instance containing all of the buttons.
     *
     * @return the <tt>Component</tt> instance containing all of the buttons
     */
    ButtonRow getComponent() {
        return BUTTONS;
    }
}
