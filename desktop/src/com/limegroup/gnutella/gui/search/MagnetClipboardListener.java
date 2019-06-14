/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.MagnetOptions;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.util.QueryUtils;
import org.limewire.concurrent.ExecutorsHelper;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
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
    /**
     * a thread which parses the clipboard and launches magnet downloads.
     */
    private final ExecutorService clipboardParser = ExecutorsHelper.newProcessingQueue("clipboard parser");
    private volatile String copiedText;
    private final Runnable parser = this::parseAndLaunch;

    private MagnetClipboardListener() {
        super();
    }

    public static MagnetClipboardListener getInstance() {
        return instance;
    }

    /**
     * Handles an array of magnets:
     * The magnets that are downloadable are shown in a dialog where the
     * user can choose which ones he would like to download.
     * <p>
     * Once single search is also started for a magnet that is
     * {@link MagnetOptions#isKeywordTopicOnly()}.
     */
    public static void handleMagnets(final MagnetOptions[] magnets) {
        // get a nicer looking address from the magnet
        // turns out magnets are very liberal.. so display the whole thing
        //final MagnetOptions[] downloadCandidates = extractDownloadableMagnets(magnets);
        // and fire off the download
        Runnable r = () -> {
            boolean oneSearchStarted = false;
            for (MagnetOptions magnet : magnets) {
                if (!magnet.isDownloadable() && magnet.isKeywordTopicOnly() && !oneSearchStarted) {
                    String query = QueryUtils.createQueryString(magnet.getKeywordTopic());
                    SearchInformation info = SearchInformation.createKeywordSearch(query, null, MediaType.getAnyTypeMediaType());
                    if (SearchMediator.validateInfo(info) == SearchMediator.QUERY_VALID) {
                        oneSearchStarted = true;
                        SearchMediator.instance().triggerSearch(info);
                    }
                }
            }
            GUIMediator.instance().showTransfers(TransfersTab.FilterMode.DOWNLOADING);
        };
        GUIMediator.safeInvokeLater(r);
    }

    private static String extractStringContentFromClipboard(Object requestor) {
        try {
            Transferable data;
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
            if (clipboardText.startsWith("magnet:?xt=urn:btih:") || clipboardText.matches("^http.*\\.torrent$") || clipboardText.matches(".*soundcloud.com.*")) {
                return clipboardText;
            }
        } catch (Throwable e) {
            // not a important error
            LOG.error("Error processing clipboard text", e);
        }
        return null;
    }

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
        handleMagnets(opts);
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
}
