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
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

/**
 * Reads an update.xml file from frostwire.com The update xml file can also come
 * with special announcements for the community.
 * <p>
 * The correct syntax of the update.xml file should be as follows: <update
 * time="${server_time_here}" version="${update_manager_version}"
 * buyUrl="${proxy_buy_url}" />
 * <p>
 * <message type="{update | announcement | overlay | hostiles }"
 * value="<text message goes here>" [version="${version_string}"] //version is
 * mandatory for message type in ['update','hostiles'] [url="${url}"] //url is
 * mandatory for message type = 'update' and type='overlay'. Optional for
 * announcements. [expires="${server_timestamp}] //mandatory only for message
 * type = announcement, otherwise the announcement will be shown forever.
 * [torrent=${torrent_url}] //optional, the message suggests a torrent should be
 * downloaded [os=${os_string}] //optional, filters message per os, valid
 * os_strings are 'windows','linux','mac' [src=${src}] //image src for overlay
 * image messages to be shown on the search result panels when frostwire is
 * opened, or search tabs are closed [intro=${intro}] //intro boolean, true for
 * overlay message shown when frostwire is opened, otherwise its the image shown
 * after search tabs are closed [language=""] //optional, examples are es_ve,
 * es*. If language is not present message is always valid language wise.
 * [md5=""] //optional, used for the MD5 of the overlay image so we can cache
 * them or refresh them. Also to check the MD5 of the hostiles.version.txt.zip
 * when the torrent has been downloaded /> <![CDATA[ Put all the text you want
 * here, with tags, it doesn't matter cause its CDATA. Could be HTML if you want
 * in theory. Just dont put a ]]> in it. ]]> <!-- there can be many messages -->
 * </message> </update>
 *
 * @author gubatron
 */
public final class UpdateManager implements Serializable {
    private static final Logger LOG = Logger.getLogger(UpdateManager.class);
    private static final int OPTION_OPEN_URL = 1;
    private static final int OPTION_LATER = 0;
    private static final int OPTION_DOWNLOAD_TORRENT = 2;
    transient private static HashSet<UpdateMessage> _seenMessages;
    /**
     * The singleton instance
     */
    private static UpdateManager INSTANCE = null;
    // Time on the server for when we last checked the updates.
    private Date _serverTime = null;

    private UpdateManager() {
    }

    /**
     * Starts an Update Task in <secondsAfter> seconds after.
     */
    static void scheduleUpdateCheckTask(final int secondsAfter, final boolean force) {
        // Uses the UpdateManager to check for updates. Then kills the timer
        Runnable checkForUpdatesTask = () -> {
            //System.out.println("UpdateManager.scheduleUpdateCheckTask() - about to check for update in " + secondsAfter + " seconds");
            try {
                Thread.sleep(secondsAfter * 1000);
            } catch (InterruptedException ignored) {
            }
            //System.out.println("UpdateManager.scheduleUpdateCheckTask() Runnable: here we go!");
            UpdateManager um = UpdateManager.getInstance();
            um.checkForUpdates(force);
        };
        new Thread(checkForUpdatesTask).start();
    }

    /**
     * Starts an Update Task in <secondsAfter> seconds after at a custom update
     * URL
     */
    public static void scheduleUpdateCheckTask(int secondsAfter) {
        scheduleUpdateCheckTask(secondsAfter, false);
    }

    static boolean isFrostWireOld(UpdateMessage message) {
        if (message.getBuild() != null) {
            try {
                int buildNumber = Integer.parseInt(message.getBuild());
                return buildNumber > FrostWireUtils.getBuildNumber();
            } catch (Throwable t) {
                System.err.println("UpdateManager::isFrostWireOld() invalid buildNumber ('" + message.getBuild() + "'), falling back to version check");
                t.printStackTrace();
            }
        }
        return isFrostWireOld(message.getVersion());
    }

    /**
     * Given a version string, it compares against the current frostwire
     * version. If frostwire is old, it will return true.
     * <p>
     * A valid version string looks like this: "MAJOR.RELEASE.SERVICE"
     * <p>
     * 4.13.1 4.13.2 ... 4.13.134
     * <p>
     * It will compare each number of the current version to the version
     * published by the update message.
     */
    private static boolean isFrostWireOld(String messageVersion) {
        // if there's nothing to compare with, then FrostWire shouldn't be old
        // for it.
        if (messageVersion == null)
            return false;
        String currentVersion = FrostWireUtils.getFrostWireVersion();
        // first discard if we're the exact same version
        if (currentVersion.equals(messageVersion)) {
            return false;
        }
        // if there's a difference, maybe we have a higher version...
        // being optimistic :)
        try {
            String[] fwVersionParts = currentVersion.split("\\.");
            int fw_major = Integer.parseInt(fwVersionParts[0]);
            int fw_release = Integer.parseInt(fwVersionParts[1]);
            int fw_service = Integer.parseInt(fwVersionParts[2]);
            String[] msgVersionParts = messageVersion.split("\\.");
            int msg_major = Integer.parseInt(msgVersionParts[0]);
            int msg_release = Integer.parseInt(msgVersionParts[1]);
            int msg_service = Integer.parseInt(msgVersionParts[2]);
            if (fw_major < msg_major) {
                return true;
            }
            if (fw_major == msg_major && fw_release < msg_release) {
                return true;
            }
            if (fw_major == msg_major && fw_release == msg_release && fw_service < msg_service) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static synchronized UpdateManager getInstance() {
        if (UpdateManager.INSTANCE == null) {
            UpdateManager.INSTANCE = new UpdateManager();
        }
        return UpdateManager.INSTANCE;
    } // getInstance

    /**
     * Starts a torrent download
     */
    private static void openTorrent(String uriStr) {
        try {
            URI uri = new URI(uriStr);
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equalsIgnoreCase("http")) {
                // System.out.println("Not a torrent URL");
                return;
            }
            String authority = uri.getAuthority();
            if (authority == null || authority.equals("") || authority.indexOf(' ') != -1) {
                // System.out.println("Invalid authority");
                return;
            }
            GUIMediator.instance().openTorrentURI(uri.toString(), false);
        } catch (URISyntaxException e) {
            System.out.println(e);
        }
    }

    Date getServerTime() {
        return _serverTime;
    }

    void setServerTime(String serverTime) {
        // if we can't get the server's time, we will show all the
        // announcements.
        // rather have the message getting to our community than not getting to
        // them at all
        // we can always remove messages from update.frostwire.com
        _serverTime = null;
        try {
            _serverTime = new Date(Long.parseLong(serverTime));
        } catch (Exception e) {
            System.out.println("Warning: UpdateManager.setServerTime(): Could not set time from server, using local time");
        }
        if (_serverTime == null)
            _serverTime = Calendar.getInstance().getTime();
    } // setServerTime

    /**
     * Checks for updates, and shows message dialogs if needed.
     */
    private void checkForUpdates(boolean force) {
        // We start the XML Reader/Parser. It will connect to
        // frostwire.com/update.xml
        // and parse the given XML.
        //System.out.println("UpdateManager.checkForUpdates() - Invoked");
        UpdateMessageReader umr = new UpdateMessageReader();
        umr.readUpdateFile();
        // if it fails to read an update, we just go on, might be that the
        // website is down, or the XML is malformed.
        //show a message update, download a torrent update, or let user know update has been downloaded
        handlePossibleUpdateMessage(umr, force);
        // attempt to show available non expired announcements
        if (umr.hasAnnouncements()) {
            LOG.info("checkForUpdates() has announcements");
            attemptShowAnnouncements(umr.getAnnouncements());
        } else {
            LOG.info("checkForUpdates() has no announcements");
        }
    } // checkForUpdates

    /**
     * If this FrostWire is a match for an update message (it's old and it's the corresponding OS)
     * Depending on what operating system and how the message looks it can either
     * tell the user to go to FrostWire and download an update or it can start
     * downloading FrostWire via BitTorrent silently using an InstallerUpdater.
     * <p>
     * The user will only be notified that a FrostWire installer has already been downloaded
     * only right after startup, meaning InstallerUpdater will finish the download and not say anything
     * to not interrupt the user's activity and not tempt the user to restart.
     * <p>
     * Currently BitTorrent Updates are supported on Windows, Debian and Ubuntu.
     */
    private void handlePossibleUpdateMessage(UpdateMessageReader umr, boolean force) {
        UpdateMessage updateMessage = umr.getUpdateMessage();
        if (updateMessage != null) {
            UpdateMediator.instance().setUpdateMessage(updateMessage);
        } else {
            return;
        }
        // we might be testing and want to force the update message
        boolean forceUpdateMessage = System.getenv().get("FROSTWIRE_FORCE_UPDATE_MESSAGE") != null;
        // attempt to show system Update Message if needed
        if (umr.hasUpdateMessage()
                &&
                ((updateMessage.getBuild() != null && !updateMessage.getBuild().trim().equals("")) ||
                        (updateMessage.getVersion() != null && !updateMessage.getVersion().trim().equals("")))
                && (forceUpdateMessage || UpdateManager.isFrostWireOld(updateMessage))) {
            boolean hasUrl = updateMessage.getUrl() != null;
            boolean hasTorrent = updateMessage.getTorrent() != null;
            boolean hasInstallerUrl = updateMessage.getInstallerUrl() != null;
            if (forceUpdateMessage) {
                System.out.println("FROSTWIRE_FORCE_UPDATE_MESSAGE env found, testing update message. (turn off with `unset FROSTWIRE_FORCE_UPDATE_MESSAGE`)");
            }
            // Logic for Windows or Mac Update
            if (OSUtils.isWindows() || OSUtils.isMacOSX()) {
                if (hasUrl && !hasTorrent && !hasInstallerUrl) {
                    showUpdateMessage(updateMessage);
                } else if (hasTorrent || hasInstallerUrl) {
                    new InstallerUpdater(updateMessage, force).start();
                }
            }
            // Logic for Linux
            else if (OSUtils.isLinux()) {
                if (OSUtils.isUbuntu()) {
                    if (hasTorrent || hasInstallerUrl) {
                        new InstallerUpdater(updateMessage, force).start();
                    } else {
                        showUpdateMessage(updateMessage);
                    }
                } else if (hasUrl) {
                    showUpdateMessage(updateMessage);
                }
            }
        }
    }

    /**
     * Given an update message, it checks the frostwire version on it, if we
     * have a lower version, then we show the message.
     */
    private void showUpdateMessage(final UpdateMessage msg) {
        final String title = (msg.getMessageType().equals("update")) ? I18n.tr("New FrostWire Update Available") : I18n.tr("FrostWire Team Announcement");
        int optionType = JOptionPane.CANCEL_OPTION;
        // check if there's an URL to link to
        if (msg.getUrl() != null && !msg.getUrl().trim().equals("")) {
            System.out.println("\t" + msg.getUrl());
            optionType |= JOptionPane.OK_OPTION;
        }
        String[] options = new String[3];
        if (msg.getTorrent() != null) {
            options[OPTION_DOWNLOAD_TORRENT] = I18n.tr("Download Torrent");
        } else {
            options = new String[2];
        }
        options[OPTION_LATER] = I18n.tr("Thanks, but not now");
        options[OPTION_OPEN_URL] = I18n.tr("Go to webpage");
        final int finalOptionType = optionType;
        final String[] finalOptions = options;
        SwingUtilities.invokeLater(() -> {
            int result = JOptionPane.showOptionDialog(null, msg.getMessage(), title, finalOptionType, JOptionPane.INFORMATION_MESSAGE, null, // Icon
                    finalOptions, // Options[]
                    null); // Initial value (Object)
            if (result == OPTION_OPEN_URL) {
                GUIMediator.openURL(msg.getUrl());
            } else if (result == OPTION_DOWNLOAD_TORRENT) {
                openTorrent(msg.getTorrent());
            }
        });
    }

    /**
     * Given announcements it will show them.
     */
    private void attemptShowAnnouncements(HashSet<UpdateMessage> announcements) {
        for (UpdateMessage msg : announcements) {
            if (msg.isShownOnce() && haveShownMessageBefore(msg)) {
                continue;
            }
            if (msg.getUrl() != null && !msg.getUrl().trim().equals("")) {
                showUpdateMessage(msg);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSeenMessages() {
        // de-serializes seen messages if available
        File f = new File(CommonUtils.getUserSettingsDir(), "seenMessages.dat");
        _seenMessages = new HashSet<>();
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (Exception e) {
                System.out.println("UpdateManager.loadSeenMessages() - Cannot create file to deserialize");
            }
            return;
        }
        if (f.length() == 0)
            return;
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
            _seenMessages = (HashSet<UpdateMessage>) ois.readObject();
            ois.close();
        } catch (Exception e) {
            System.out.println("UpdateManager.loadSeenMessages() - Cannot deserialize - ");
            System.out.println(e);
        }
    }

    private void saveSeenMessages() {
        // serializes _seenMessages into seenMessages.dat
        if (_seenMessages == null || _seenMessages.size() < 1)
            return;
        File f = new File(CommonUtils.getUserSettingsDir(), "seenMessages.dat");
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (Exception e) {
                System.out.println("UpdateManager.saveSeenMessages() cannot create file to serialize seen messages");
            }
        }
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
            oos.writeObject(_seenMessages);
            oos.close();
        } catch (Exception e) {
            System.out.println("UpdateManager.saveSeenMessages() - Cannot serialize.");
            e.printStackTrace();
        }
    }

    /**
     * Checks on a Message map, if we've seen this message before. The message
     * map is serialized on disk every time we write to it. Its initialized from
     * disk when we start the Update Manager.
     */
    private boolean haveShownMessageBefore(UpdateMessage msg) {
        if (!msg.isShownOnce())
            return false; // we'll ignore messages that can be seen more than
        // once
        loadSeenMessages();
        if (_seenMessages == null || _seenMessages.size() == 0 || !_seenMessages.contains(msg)) {
            if (_seenMessages == null)
                _seenMessages = new HashSet<>();
            _seenMessages.add(msg);
            saveSeenMessages();
            return false;
        }
        return true;
    }
}
