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

package com.frostwire.gui.updates;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.frostwire.logging.Logger;
import org.limewire.util.OSUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.frostwire.uxstats.UXStats;
import com.frostwire.uxstats.UXStatsConf;
import com.limegroup.gnutella.gui.search.SearchEngine;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

/**
 * SAX Parser and more. Its responsible for creating UpdateMessages The
 * UpdateManager will ask this object if it has announcements or an update
 * message available.
 */
public final class UpdateMessageReader implements ContentHandler {

    private static final Logger LOG = Logger.getLogger(UpdateMessageReader.class);
    private static final String DEFAULT_UPDATE_URL = "http://update.frostwire.com";

    public HashSet<UpdateMessage> _announcements = null;

    public UpdateMessage _bufferMessage = null;

    public boolean _introloaded = false;

    public boolean _otherloaded = false;

    public LinkedList<UpdateMessage> _overlays = null;

    public UpdateMessage _updateMessage = null;

    // public void UpdateMessageReader() {}
    public String _updateURL = DEFAULT_UPDATE_URL;

    /**
     * Only ads Announcements that have not expired
     * 
     * @param msg
     */
    public void addAnnouncement(UpdateMessage msg) {
        if (_announcements == null) {
            _announcements = new HashSet<UpdateMessage>();
        }

        if (msg.getMessageType().equals("announcement") && !msg.hasExpired()) {
            _announcements.add(msg);
        }
    }

    /**
     * As overlay messages are interpreted, this method will add them to
     * _overlays. It will replace old intro messages for newer, and old
     * afterSearch messages for newer.
     * 
     * When I say 'old' I mean, a message that was received just a while ago as
     * we were parsing the document.
     * 
     * The idea is that update.frostwire.com defines first the more generic
     * update overlays, and further down in the document the more specific
     * overlays, so that we can ignore generic configuration if we are fit for a
     * more specific overlay
     * 
     * e.g. An intro for a specific frostwire version, language.
     * 
     * which message to keep for the client.
     * 
     * @see 
     *      UpdateManager.updateOverlays(HashSet<UpdateMessage>,UpdateMessageReader
     *      aka:me)
     * @param msg
     */
    public void addOverlay(UpdateMessage msg) {
        if (msg != null && msg.getMessageType().equals("overlay")) {

            if (_overlays == null)
                _overlays = new LinkedList<UpdateMessage>();

            // Replace newly found intro, or aftersearch
            // for previously added message of same nature.
            if (!_overlays.isEmpty()) {
                Iterator<UpdateMessage> it = _overlays.iterator();
                while (it.hasNext()) {
                    UpdateMessage m = it.next();

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

    public void characters(char[] ch, int start, int length) throws SAXException {

    }

    public void endDocument() throws SAXException {
    }

    /**
     * When the tag closes, if we have a _buffer message, we check what type it
     * is and set it as the Update Message (if its for this client/os) or if its
     * an announcement, we add it to the announcements collection.
     * 
     * This function will make use of 'isMessageForMe', to try to discard the
     * message in case its addressed to another OS different than the one where
     * this client is running on top of.
     * 
     * If its an update message and no update message has been spotted so far,
     * the UpdateReader sets it as the update message object available
     * 
     * If its an announcement we attempt adding it as another announcement. That
     * method will only add it if the message has not expired.
     * 
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

    public void endPrefixMapping(String arg0) throws SAXException {
    }

    public HashSet<UpdateMessage> getAnnouncements() {
        return _announcements;
    }

    public List<UpdateMessage> getOverlays() {
        return _overlays;
    }

    public UpdateMessage getUpdateMessage() {
        return _updateMessage;
    }

    public String getUpdateURL() {
        return _updateURL;
    }

    public boolean hasAnnouncements() {
        return _announcements != null && _announcements.size() > 0;
    }

    public boolean hasOverlays() {
        return _overlays != null && _overlays.size() > 0;
    }

    public boolean hasUpdateMessage() {
        return _updateMessage != null;
    }

    public void ignorableWhitespace(char[] arg0, int arg1, int arg2) throws SAXException {
    }

    public boolean isIntroLoaded() {
        return _introloaded; // checks if intro is loaded for the current
                             // language
    }

    /**
     * Checks if we should show this message for the language the user is using.
     * If no language is specified in the message, then it returns true right
     * away the message is meant for everyone, unless there's a more specific
     * message ahead.
     * 
     * 
     * If you want a full blown validation use isMessageForMe()
     * 
     * @param msg
     * @return
     */
    private boolean isMessageEligibleForMyLang(UpdateMessage msg) {
        String langinmsg = msg.getLanguage(); // current language in message

        if (langinmsg == null || langinmsg.equals("*"))
            return true;

        String langinapp = ApplicationSettings.getLanguage().toLowerCase();

        if (langinmsg.length() == 2)
            return langinapp.toLowerCase().startsWith(langinmsg.toLowerCase());

        if (langinmsg.endsWith("*")) {
            langinapp = ApplicationSettings.getLanguage().substring(0, 2);
            langinmsg = langinmsg.substring(0, langinmsg.indexOf("*")); // removes
                                                                        // last
                                                                        // char
        }

        return langinmsg.equalsIgnoreCase(langinapp);
    }

    /**
     * Checks if this message should be shown for the OS on which this FrostWire
     * is runnning on.
     * 
     * If you want a full blown validation use isMessageForMe()
     * 
     * @param msg
     * @return
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
     * 
     * When it's an update message: If the message is an update message then it
     * doesn't matter what version it's sent, it should be valid because we need
     * to use the version in it to see if we have to update or not.
     * 
     * If you want a full blown validation use isMessageForMe()
     * 
     * @param msg
     * @return
     */
    private boolean isMessageEligibleForMyVersion(UpdateMessage msg) {
        if (msg.getVersion() == null || msg.getMessageType().equalsIgnoreCase("update"))
            return true;

        return !UpdateManager.isFrostWireOld(msg.getVersion());
    }

    /**
     * Tells me if I'm supposed to keep the given update message. Compares the
     * message's OS string against the current operating system.
     * 
     * Compares: - version (If message matches all versions before me [not exact
     * match])
     * 
     * If the message is an announcement, it cares about the version number not
     * being outdated.
     * 
     * @param msg
     * @return
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

    public boolean isOtherLoaded() {
        return _otherloaded; // check if overlay is loaded for the current
                             // language
    }

    public void processingInstruction(String arg0, String arg1) throws SAXException {
    }

    public void readUpdateFile() {
        HttpURLConnection connection = null;
        InputSource src = null;

        try {
            String userAgent = "FrostWire/" + OSUtils.getOS() + "-" + OSUtils.getArchitecture() + "/" + FrostWireUtils.getFrostWireVersion();
            connection = (HttpURLConnection) (new URL(getUpdateURL())).openConnection();
            //String url = getUpdateURL();
            //LOG.info("Reading update file from " + url);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Connection", "close");
            connection.setReadTimeout(10000); // 10 secs timeout

            if (connection.getResponseCode() >= 400) {
                // invalid URL for sure
                connection.disconnect();
                return;
            }

            src = new InputSource(connection.getInputStream());

            XMLReader rdr = XMLReaderFactory.createXMLReader("com.sun.org.apache.xerces.internal.parsers.SAXParser");
            rdr.setContentHandler(this);

            rdr.parse(src);
            connection.getInputStream().close();
            connection.disconnect();
        } catch (java.net.SocketTimeoutException e3) {
            System.out.println("UpdateMessageReadre.readUpdateFile() Socket Timeout Exeception " + e3.toString());
        } catch (IOException e) {
            System.out.println("UpdateMessageReader.readUpdateFile() IO exception " + e.toString());
        } catch (SAXException e2) {
            System.out.println("UpdateMessageReader.readUpdateFile() SAX exception " + e2.toString());
        }
    }

    public void setDocumentLocator(Locator arg0) {
    }

    /**
     * Sets only the first update message it finds. Make sure to put only have a
     * single update message everytime on the server, If you plan to leave old
     * messages there, keep the newest one at the beginning of the file.
     * 
     * @param msg
     */
    public void setUpdateMessage(UpdateMessage msg) {
        if (_updateMessage == null && msg != null && msg.getMessageType().equals("update")) {
            _updateMessage = msg;
        }
    }

    public void setUpdateURL(String updateURL) {
        if (updateURL == null)
            _updateURL = DEFAULT_UPDATE_URL;
        else
            _updateURL = updateURL;
    }

    public void skippedEntity(String arg0) throws SAXException {
    }

    public void startDocument() throws SAXException {
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        // deal with the opening tag
        if (localName.equalsIgnoreCase("update")) {
            UpdateManager.getInstance().setServerTime(atts.getValue("time"));

            if (atts.getValue("torrentDetailsUrl") != null && atts.getValue("torrentDetailsUrl").length() > 0) {
                String torrentDetailsUrl = atts.getValue("torrentDetailsUrl");

                List<SearchEngine> searchEngines = SearchEngine.getEngines();
                for (SearchEngine searchEngine : searchEngines) {
                    searchEngine.redirectUrl = torrentDetailsUrl;
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
            String src = atts.getValue("src");

            _bufferMessage = new UpdateMessage(type, message);
            _bufferMessage.setUrl(url);
            _bufferMessage.setTorrent(torrent);
            _bufferMessage.setInstallerUrl(installerUrl);
            _bufferMessage.setOs(os);
            _bufferMessage.setShowOnce(showOnce);
            _bufferMessage.setVersion(version);

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

            if (_bufferMessage.getMessageType().equals("uxstats")) {
                processUXStatsMsg(_bufferMessage, atts);
            }
        }

    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    private void processUXStatsMsg(UpdateMessage msg, Attributes atts) {
        try {
            String enabled = atts.getValue("enabled");

            if (enabled != null && enabled.equals("true") && ApplicationSettings.UX_STATS_ENABLED.getValue()) {
                String url = "http://ux.frostwire.com/dux";
                String os = OSUtils.getFullOS();
                String fwversion = FrostWireUtils.getFrostWireVersion();
                String fwbuild = String.valueOf(FrostWireUtils.getBuildNumber());
                int period = Integer.parseInt(atts.getValue("period"));
                int minEntries = Integer.parseInt(atts.getValue("minEntries"));
                int maxEntries = Integer.parseInt(atts.getValue("maxEntries"));

                UXStatsConf context = new UXStatsConf(url, os, fwversion, fwbuild, period, minEntries, maxEntries);
                UXStats.instance().setContext(context);
            }
        } catch (Throwable e) {
            LOG.warn("Unable to process uxstats message from server", e);
        }
    }
}
