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

package com.frostwire.gui.updates;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.util.OSUtils;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * SAX Parser and more. Its responsible for creating UpdateMessages The
 * UpdateManager will ask this object if it has announcements or an update
 * message available.
 */
@SuppressWarnings("RedundantThrows")
final class UpdateMessageReader implements ContentHandler {
    private static final Logger LOG = Logger.getLogger(UpdateMessageReader.class);
    private static final String DEFAULT_UPDATE_URL = "https://update.frostwire.com";
    private HashSet<UpdateMessage> _announcements = null;
    private UpdateMessage _bufferMessage = null;
    private LinkedList<UpdateMessage> _overlays = null;
    private UpdateMessage _updateMessage = null;

    /**
     * Only ads Announcements that have not expired
     */
    private void addAnnouncement(UpdateMessage msg) {
        if (_announcements == null) {
            _announcements = new HashSet<>();
        }
        if (msg.getMessageType().equals("announcement") && !msg.hasExpired()) {
            _announcements.add(msg);
        }
    }

    /**
     * As overlay messages are interpreted, this method will add them to
     * _overlays. It will replace old intro messages for newer, and old
     * afterSearch messages for newer.
     * <p>
     * When I say 'old' I mean, a message that was received just a while ago as
     * we were parsing the document.
     * <p>
     * The idea is that update.frostwire.com defines first the more generic
     * update overlays, and further down in the document the more specific
     * overlays, so that we can ignore generic configuration if we are fit for a
     * more specific overlay
     * <p>
     * e.g. An intro for a specific frostwire version, language.
     * <p>
     * which message to keep for the client.
     *
     * @see UpdateManager::updateOverlays(HashSet<UpdateMessage>,UpdateMessageReader me)
     */
    private void addOverlay(UpdateMessage msg) {
        if (msg != null && msg.getMessageType().equals("overlay")) {
            if (_overlays == null)
                _overlays = new LinkedList<>();
            // Replace newly found intro, or after search
            // for previously added message of same nature.
            if (!_overlays.isEmpty()) {
                for (UpdateMessage m : _overlays) {
                    // Find another intro or after search, and replace it
                    if (m.isIntro() == msg.isIntro()) {
                        _overlays.remove(m);
                        _overlays.add(msg);
                        return;
                    }
                }// while to find what to replace
            }// if we can iterate
            // if I make it here, then I need to add a new overlay
            _overlays.add(msg);
        } // if we actually receive an overlay
        else {
            System.out.println("UpdateManager.addOverlay() - The message given wasn't good.");
        }
    }

    public void endDocument() throws SAXException {
    }

    /**
     * When the tag closes, if we have a _buffer message, we check what type it
     * is and set it as the Update Message (if its for this client/os) or if its
     * an announcement, we add it to the announcements collection.
     * <p>
     * This function will make use of 'isMessageForMe', to try to discard the
     * message in case its addressed to another OS different than the one where
     * this client is running on top of.
     * <p>
     * If its an update message and no update message has been spotted so far,
     * the UpdateReader sets it as the update message object available
     * <p>
     * If its an announcement we attempt adding it as another announcement. That
     * method will only add it if the message has not expired.
     */
    public void endElement(String uri, String name, String qName) throws SAXException {
        // discard buffer message if its not meant for me right away.
        if (!isMessageForMe(_bufferMessage)) {
            // System.out.println("Discarding message - " + _bufferMessage);
            _bufferMessage = null;
            return;
        }
        if (_bufferMessage != null && name.equalsIgnoreCase("message")) {
            if (_bufferMessage.getMessageType().equalsIgnoreCase("update")) {
                setUpdateMessage(_bufferMessage);
            } else if (_bufferMessage.getMessageType().equalsIgnoreCase("announcement")) {
                addAnnouncement(_bufferMessage);
            } else if (_bufferMessage.getMessageType().equalsIgnoreCase("overlay")) {
                // System.out.println("UpdateMessageReader.endElement() - addOverlay");
                addOverlay(_bufferMessage);
            }
            _bufferMessage = null;
        }
    } // endElement

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
    }

    public void endPrefixMapping(String arg0) throws SAXException {
    }

    HashSet<UpdateMessage> getAnnouncements() {
        return _announcements;
    }

    UpdateMessage getUpdateMessage() {
        return _updateMessage;
    }

    /**
     * Sets only the first update message it finds. Make sure to put only a
     * single update message every time on the server, If you plan to leave old
     * messages there, keep the newest one at the beginning of the file.
     */
    private void setUpdateMessage(UpdateMessage msg) {
        if (_updateMessage == null && msg != null && msg.getMessageType().equals("update")) {
            _updateMessage = msg;
        }
    }

    boolean hasAnnouncements() {
        return _announcements != null && _announcements.size() > 0;
    }

    boolean hasUpdateMessage() {
        return _updateMessage != null;
    }

    public void ignorableWhitespace(char[] arg0, int arg1, int arg2) throws SAXException {
    }

    /**
     * Checks if we should show this message for the language the user is using.
     * If no language is specified in the message, then it returns true right
     * away the message is meant for everyone, unless there's a more specific
     * message ahead.
     * <p>
     * <p>
     * If you want a full blown validation use isMessageForMe()
     */
    private boolean isMessageEligibleForMyLang(UpdateMessage msg) {
        String langInMsg = msg.getLanguage(); // current language in message
        if (langInMsg == null || langInMsg.equals("*"))
            return true;
        String langinapp = ApplicationSettings.getLanguage().toLowerCase();
        if (langInMsg.length() == 2)
            return langinapp.toLowerCase().startsWith(langInMsg.toLowerCase());
        if (langInMsg.endsWith("*")) {
            langinapp = ApplicationSettings.getLanguage().substring(0, 2);
            langInMsg = langInMsg.substring(0, langInMsg.indexOf("*")); // removes
            // last
            // char
        }
        return langInMsg.equalsIgnoreCase(langinapp);
    }

    /**
     * Checks if this message should be shown for the OS on which this FrostWire
     * is running on.
     * <p>
     * If you want a full blown validation use isMessageForMe()
     */
    private boolean isMessageEligibleForMyOs(UpdateMessage msg) {
        if (msg.getOs() == null)
            return true;
        boolean im_mac_msg_for_me = msg.getOs().equals("mac") && OSUtils.isMacOSX();
        boolean im_windows_msg_for_me = msg.getOs().equals("windows") && (OSUtils.isWindows() || OSUtils.isWindowsXP() || OSUtils.isWindowsNT() || OSUtils.isWindows98() || OSUtils.isWindows95() || OSUtils.isWindowsMe() || OSUtils.isWindowsVista());
        boolean im_linux_msg_for_me = msg.getOs().equals("linux") && OSUtils.isLinux();
        return im_mac_msg_for_me || im_windows_msg_for_me || im_linux_msg_for_me;
    }

    /**
     * Checks if FrostWire isn't too old for this message. If the message has no
     * version info, it doesn't matter then, it should be eligible for all
     * versions.
     * <p>
     * When it's an update message: If the message is an update message then it
     * doesn't matter what version it's sent, it should be valid because we need
     * to use the version in it to see if we have to update or not.
     * <p>
     * If you want a full blown validation use isMessageForMe()
     */
    private boolean isMessageEligibleForMyVersion(UpdateMessage msg) {
        return msg.getVersion() == null ||
                msg.getMessageType().equalsIgnoreCase("update") ||
                !UpdateManager.isFrostWireOld(msg);
    }

    /**
     * Tells me if I'm supposed to keep the given update message. Compares the
     * message's OS string against the current operating system.
     * <p>
     * Compares: - version (If message matches all versions before me [not exact
     * match])
     * <p>
     * If the message is an announcement, it cares about the version number not
     * being outdated.
     */
    private boolean isMessageForMe(UpdateMessage msg) {
        if (msg == null) {
            // System.out.println("UpdateManager.isMessageForMe() - Message was null");
            return false;
        }

        /*
         * System.out.println("UpdateManager.isMessageForMe() - isMessageEligibleForMyOs - "
         * + isMessageEligibleForMyOs(msg));System.out.println(
         * "UpdateManager.isMessageForMe() - isMessageEligibleForMyLang - " +
         * isMessageEligibleForMyLang(msg));System.out.println(
         * "UpdateManager.isMessageForMe() - isMessageEligibleForMyVersion - " +
         * isMessageEligibleForMyVersion(msg));
         */
        return isMessageEligibleForMyOs(msg) && isMessageEligibleForMyLang(msg) && isMessageEligibleForMyVersion(msg);
    } // isMessageForMe

    public void processingInstruction(String arg0, String arg1) throws SAXException {
    }

    void readUpdateFile() {
        HttpURLConnection connection = null;
        InputSource src;
        try {
            String userAgent = "FrostWire/" + OSUtils.getOS() + "-" + OSUtils.getArchitecture() + "/" + FrostWireUtils.getFrostWireVersion() + "/build-" + FrostWireUtils.getBuildNumber();
            connection = (HttpURLConnection) (new URL(DEFAULT_UPDATE_URL)).openConnection();

            LOG.info("Reading update file from " + DEFAULT_UPDATE_URL);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Connection", "close");
            connection.setReadTimeout(10000); // 10 secs timeout
            final int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                // invalid URL for sure
                LOG.error("readUpdateFile(): Could not read update file, error code " + responseCode);
                connection.disconnect();
                return;
            }
            src = new InputSource(connection.getInputStream());
            //XMLReader rdr = SAXParserFactory.newDefaultInstance().newSAXParser().getXMLReader();
            XMLReader rdr = XMLReaderFactory.createXMLReader("com.sun.org.apache.xerces.internal.parsers.SAXParser");
            rdr.setContentHandler(this);
            LOG.info("readUpdateFile(): got update file, about to parse");
            rdr.parse(src);
            LOG.info("readUpdateFile(): finished parsing");
        } catch (Throwable t) {
            System.err.println("UpdateMessageReader.readUpdateFile() " + t.getClass().getName() + " " + t.toString());
            t.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.getInputStream().close();
                    connection.disconnect();
                } catch (Throwable ignored) {
                    // close quietly
                }
            }
        }
    }

    public void setDocumentLocator(Locator arg0) {
    }

    public void skippedEntity(String arg0) throws SAXException {
    }

    public void startDocument() throws SAXException {
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        // deal with the opening tag
        //LOG.info("startElement " + localName);
        if (localName.equalsIgnoreCase("update")) {
            UpdateManager.getInstance().setServerTime(atts.getValue("time"));
            if (atts.getValue("torrentDetailsUrl") != null && atts.getValue("torrentDetailsUrl").length() > 0) {
                String torrentDetailsUrl = atts.getValue("torrentDetailsUrl");
                List<SearchEngine> searchEngines = SearchEngine.getEngines();
                for (SearchEngine searchEngine : searchEngines) {
                    searchEngine.setRedirectUrl(torrentDetailsUrl);
                }
            }
        } else if (localName.equalsIgnoreCase("message")) {
            String type = atts.getValue("type");
            String message = atts.getValue("value");
            String url = atts.getValue("url");
            String torrent = atts.getValue("torrent");
            String installerUrl = atts.getValue("installerUrl");
            String os = atts.getValue("os");
            String showOnce = atts.getValue("showOnce");
            String version = atts.getValue("version");
            String build = atts.getValue("build");
            String src = atts.getValue("src");
            String saveAs = atts.getValue("saveAs");
            _bufferMessage = new UpdateMessage(type, message);
            _bufferMessage.setUrl(url);
            _bufferMessage.setTorrent(torrent);
            _bufferMessage.setInstallerUrl(installerUrl);
            _bufferMessage.setOs(os);
            _bufferMessage.setShowOnce(showOnce);
            _bufferMessage.setVersion(version);
            _bufferMessage.setBuild(build);
            _bufferMessage.setSaveAs(saveAs);
            if (atts.getValue("md5") != null) {
                _bufferMessage.setRemoteMD5(atts.getValue("md5"));
                //LOG.debug("UpdateMessageReader.startElement overlay md5=" + atts.getValue("md5"));
            }
            // language properties available only inside overlay
            if (atts.getValue("language") != null) {
                _bufferMessage.setLanguage(atts.getValue("language"));
                // System.out.println("UpdateMessageReader.startElement overlay language="
                // + atts.getValue("lang"));
            }
            if (atts.getValue("valueInstallerReady") != null) {
                _bufferMessage.setMessageInstallerReady(atts.getValue("valueInstallerReady"));
                // System.out.println("UpdateMessageReader.startElement overlay md5="
                // + atts.getValue("md5"));
            }
            if (_bufferMessage.getMessageType().equalsIgnoreCase("announcement")) {
                _bufferMessage.setExpiration(atts.getValue("expiration"));
            }
            // deal with overlay messages specific properties
            if (_bufferMessage.getMessageType().equalsIgnoreCase("overlay")) {
                // System.out.println("UpdateMessageReader.startElement overlay msg found");
                _bufferMessage.setSrc(src);
                if (atts.getValue("intro") != null && (atts.getValue("intro").equals("1") || atts.getValue("intro").equalsIgnoreCase("true") || atts.getValue("intro").equalsIgnoreCase("yes"))) {
                    _bufferMessage.setIntro(true);
                    // System.out.println("UpdateMessageReader.startElement overlay intro=true");
                } else {
                    _bufferMessage.setIntro(false);
                    // System.out.println("UpdateMessageReader.startElement overlay intro=false");
                }
            } // overlays
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }
}
