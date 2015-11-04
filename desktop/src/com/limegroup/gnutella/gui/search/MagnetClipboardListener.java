/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.limewire.concurrent.ExecutorsHelper;

import com.frostwire.logging.Logger;
import com.limegroup.gnutella.MagnetOptions;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.util.QueryUtils;

/**
 *
 * This singleton class listens to window activated events and parses the clipboard to see
 * if a magnet uri is present.  If it is it asks the user whether to download the file.
 */
public class MagnetClipboardListener extends WindowAdapter {

    private static final Logger LOG = Logger.getLogger(MagnetClipboardListener.class);

    private static final MagnetClipboardListener instance = new MagnetClipboardListener();

    //the system clipboard
    private final static Clipboard CLIPBOARD = Toolkit.getDefaultToolkit().getSystemClipboard();

    //dummy clipboard content
    private final StringSelection empty = new StringSelection("");

    private volatile String copiedText;

    /**
     * a thread which parses the clipboard and launches magnet downloads.
     */
    private final ExecutorService clipboardParser = ExecutorsHelper.newProcessingQueue("clipboard parser");

    private Runnable parser = new Runnable() {
        public void run() {
            parseAndLaunch();
        }
    };

    /**
     * @return true if no errors occurred.  False if we should not try to 
     * parse the clipboard anymore.
     */
    private void parseAndLaunch() {
        String contents = extractStringContentFromClipboard(this);

        //could not extract the clipboard as text.
        if (contents == null)
            return;

        String copied = copiedText;
        if (copied != null && copied.equals(contents)) {
            // it is the magnet we just created
            return;
        }

        //check if the magnet is valid
        final MagnetOptions[] opts = MagnetOptions.parseMagnets(contents);
        if (opts.length == 0)
            return; //not a valid magnet link

        //at this point we know we have a valid magnet link in the clipboard.
        LOG.info("clipboard contains " + contents);

        //purge the clipboard at this point
        purgeClipboard();

        handleMagnets(opts, true);
    }

    private MagnetClipboardListener() {
        super();
    }

    public static MagnetClipboardListener getInstance() {
        return instance;
    }

    /**
     * Sets the text that is going to be copied to the clipboard from withing 
     * LimeWire, so that the listener can discern between our own copied magnet 
     * links and the ones pasted from the outside.
     * @param text
     */
    public void setCopiedText(String text) {
        copiedText = text;
    }

    /**
     * ask the clipboard parser to see if there is a magnet.
     */
    public void windowActivated(WindowEvent e) {
        clipboardParser.execute(parser);
    }

    /**
     * clears the clipboard from the current string  
     */
    private void purgeClipboard() {
        try {
            CLIPBOARD.setContents(empty, empty);
        } catch (IllegalStateException isx) {
            //do nothing
        }
    }

    /**
     * Handles an array of magnets:
     * The magnets that are downloadable are shown in a dialog where the 
     * user can choose which ones he would like to download.
     * 
     * Once single search is also started for a magnet that is 
     * {@link MagnetOptions#isKeywordTopicOnly()}.
     * 
     * @param showDialog if true a dialog with the magnets is shown asking
     * the user if s/he wants to download them
     */
    public static void handleMagnets(final MagnetOptions[] magnets, final boolean showDialog) {
        // get a nicer looking address from the magnet
        // turns out magnets are very liberal.. so display the whole thing
        final MagnetOptions[] downloadCandidates = extractDownloadableMagnets(magnets);

        // and fire off the download
        Runnable r = new Runnable() {
            public void run() {
                if (!showDialog) {
                    //    				for (MagnetOptions magnet : downloadCandidates) {
                    //						//TODO: DownloaderUtils.createDownloader(magnet);
                    //					}
                } else if (downloadCandidates.length > 0) {
                    //List<MagnetOptions> userChosen = showStartDownloadsDialog(downloadCandidates);
                    //					for (MagnetOptions magnet : userChosen) {
                    //					  //TODO: DownloaderUtils.createDownloader(magnet);
                    //					}
                }
                boolean oneSearchStarted = false;
                for (int i = 0; i < magnets.length; i++) {
                    if (!magnets[i].isDownloadable() && magnets[i].isKeywordTopicOnly() && !oneSearchStarted) {
                        String query = QueryUtils.createQueryString(magnets[i].getKeywordTopic());
                        SearchInformation info = SearchInformation.createKeywordSearch(query, null, MediaType.getAnyTypeMediaType());
                        if (SearchMediator.validateInfo(info) == SearchMediator.QUERY_VALID) {
                            oneSearchStarted = true;
                            SearchMediator.instance().triggerSearch(info);
                        }
                    }
                }
                GUIMediator.instance().setWindow(GUIMediator.Tabs.SEARCH);
            }
        };
        GUIMediator.safeInvokeLater(r);
    }

    public static String extractStringContentFromClipboard(Object requestor) {
        try {
            Transferable data = null;
            try {
                //check if there's anything in the clipboard
                data = CLIPBOARD.getContents(requestor);
            } catch (IllegalStateException isx) {
                //we can't use the clipboard, give up.
                return null;
            }

            //is there anything in the clipboard?
            if (data == null)
                return null;

            //then, check if the data in the clipboard is text
            if (!data.isDataFlavorSupported(DataFlavor.stringFlavor))
                return null;

            //next, extract the content into a string
            String contents = null;

            try {
                contents = (String) data.getTransferData(DataFlavor.stringFlavor);
            } catch (IOException iox) {
                LOG.info("problem occured while trying to parse clipboard, do nothing", iox);
            } catch (UnsupportedFlavorException ufx) {
                LOG.error("UnsupportedFlavor??", ufx);
            }

            return contents;
        } catch (Throwable e) {
            // X11 related error reported from bug manager
        }

        return "";
    }

    /**
     * @return A magnet link or torrent url. "" if the clipboard has something else.
     */
    public static String getMagnetOrTorrentURLFromClipboard() {
        try {
            String clipboardText = extractStringContentFromClipboard(null);

            if (clipboardText == null) {
                return "";
            }

            //if the text in the clipboard has several URLs it will only parse the first line.
            if (clipboardText.contains("\n")) {
                clipboardText = clipboardText.split("\n")[0].trim();
            }

            if (clipboardText.startsWith("magnet:?xt=urn:btih:") || clipboardText.matches("^http.*\\.torrent$") || clipboardText.matches(".*youtube.com.*") || clipboardText.matches(".*soundcloud.com.*")) {
                return clipboardText;
            }

        } catch (Throwable e) {
            // not a important error
            LOG.error("Error processing clipboard text", e);
        }

        return null;
    }

    /**
     * Extracts magnets that are not keyword topic only magnets
     * @param magnets
     * @return
     */
    private static MagnetOptions[] extractDownloadableMagnets(MagnetOptions[] magnets) {
        List<MagnetOptions> dls = new ArrayList<MagnetOptions>(magnets.length);
        for (int i = 0; i < magnets.length; i++) {
            MagnetOptions magnet = magnets[i];
            if (!magnet.isKeywordTopicOnly()) {
                dls.add(magnets[i]);
            }
        }
        // all magnets are downloadable, return original array
        if (dls.size() == magnets.length) {
            return magnets;
        } else {
            return dls.toArray(new MagnetOptions[0]);
        }
    }
}
