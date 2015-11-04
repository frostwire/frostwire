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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.util.FrostWireUtils;

/**
 * Reads an update.xml file from frostwire.com The update xml file can also come
 * with special announcements for the community.
 * 
 * The correct syntax of the update.xml file should be as follows: <update
 * time="${server_time_here}" version="${update_manager_version}"
 * buyUrl="${proxy_buy_url}" />
 * 
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
 * here, with tags, it doesnt matter cause its CDATA. Could be HTML if you want
 * in theory. Just dont put a ]]> in it. ]]> <!-- there can be many messages -->
 * </message> </update>
 * 
 * @author gubatron
 * 
 * 
 * 
 */
public final class UpdateManager implements Serializable {

    private static final int OPTION_OPEN_URL = 1;
    private static final int OPTION_LATER = 0;
    private static final int OPTION_DOWNLOAD_TORRENT = 2;

    transient private static HashSet<UpdateMessage> _seenMessages;

    transient UpdateMessage _updateMessage = null;
    transient HashSet<UpdateMessage> _announcements = null;

    /**
     * Starts an Update Task in <secondsAfter> seconds after.
     * 
     */
    public static void scheduleUpdateCheckTask(final int secondsAfter, final String updateURL, final boolean force) {

        Runnable checkForUpdatesTask = new Runnable() {

            // Uses the UpdateManager to check for updates. Then kills the
            // timer.
            public void run() {
                //System.out.println("UpdateManager.scheduleUpdateCheckTask() - about to check for update in " + secondsAfter + " seconds");

                try {
                    Thread.sleep(secondsAfter * 1000);
                } catch (InterruptedException e) {

                }

                //System.out.println("UpdateManager.scheduleUpdateCheckTask() Runnable: here we go!");
                UpdateManager um = UpdateManager.getInstance();
                um.checkForUpdates(updateURL, force);
            }
        };

        new Thread(checkForUpdatesTask).start();
    }
    
    /**
     * Starts an Update Task in <secondsAfter> seconds after at a custom update
     * URL
     * 
     * @param secondsAfter
     */
    public static void scheduleUpdateCheckTask(int secondsAfter) {
        scheduleUpdateCheckTask(secondsAfter, false);
    }

    /**
     * Starts an Update Task in <secondsAfter> seconds after at a custom update
     * URL
     * 
     * @param secondsAfter
     * @param force (force the update download)
     */
    public static void scheduleUpdateCheckTask(int secondsAfter, boolean force) {
        scheduleUpdateCheckTask(secondsAfter, null, force);
    }

    /** The singleton instance */
    private static UpdateManager INSTANCE = null;

    // Time on the server for when we last checked the updates.
    private Date _serverTime = null;

    private UpdateManager() {
    }

    public void setServerTime(String serverTime) {
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

    public Date getServerTime() {
        return _serverTime;
    }

    /**
     * Checks for updates, and shows message dialogs if needed.
     */
    public void checkForUpdates(String updateURL, boolean force) {
        // We start the XML Reader/Parser. It will connect to
        // frostwire.com/update.xml
        // and parse the given XML.
        //System.out.println("UpdateManager.checkForUpdates() - Invoked");

        UpdateMessageReader umr = new UpdateMessageReader();
        umr.setUpdateURL(updateURL); // if null goes to default url
        umr.readUpdateFile();
        // if it fails to read an update, we just go on, might be that the
        // website is down, or the XML is malformed.

        //show a message update, download a torrent update, or let user know update has been downloaded	
        handlePossibleUpdateMessage(umr, force);

        // attempt to show available non expired announcements
        if (umr.hasAnnouncements()) {
            attemptShowAnnouncements(umr.getAnnouncements());
        }

    } // checkForUpdates

    /**
     * If this FrostWire is a match for an update message (it's old and it's the corresponding OS)
     * Depending on what operating system and how the message looks it can either
     * tell the user to go to FrostWire and download an update or it can start
     * downloading FrostWire via BitTorrent silently using an InstallerUpdater.
     * 
     * The user will only be notified that a FrostWire installer has already been downloaded
     * only right after startup, meaning InstallerUpdater will finish the download and not say anything
     * to not interrupt the user's activity and not tempt the user to restart.
     * 
     * Currently BitTorrent Updates are supported on Windows, Debian and Ubuntu.
     * 
     * @param umr
     */
    private void handlePossibleUpdateMessage(UpdateMessageReader umr, boolean force) {
        UpdateMessage updateMessage = umr.getUpdateMessage();
        
        if (updateMessage != null) {
            UpdateMediator.instance().setUpdateMessage(updateMessage);
        }
        
        // attempt to show system Update Message if needed
        if (umr.hasUpdateMessage() && updateMessage.getVersion() != null && !updateMessage.getVersion().trim().equals("")
                && UpdateManager.isFrostWireOld(updateMessage.getVersion())) {

            boolean hasUrl = updateMessage.getUrl() != null;
            boolean hasTorrent = updateMessage.getTorrent() != null;
            boolean hasInstallerUrl = updateMessage.getInstallerUrl() != null;

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
     * 
     * @param msg
     */
    public void showUpdateMessage(final UpdateMessage msg) {
        final String title = (msg.getMessageType().equals("update")) ? I18n.tr("New FrostWire Update Available") : I18n.tr("FrostWire Team Announcement");

        int optionType = JOptionPane.CANCEL_OPTION;

        // check if there's an URL to link to
        if (msg.getUrl() != null && !msg.getUrl().trim().equals("")) {
            System.out.println("\t" + msg.getUrl());
            optionType = optionType | JOptionPane.OK_OPTION;
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

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int result = JOptionPane.showOptionDialog(null, msg.getMessage(), title, finalOptionType, JOptionPane.INFORMATION_MESSAGE, null, // Icon
                        finalOptions, // Options[]
                        null); // Initial value (Object)

                if (result == OPTION_OPEN_URL) {
                    GUIMediator.openURL(msg.getUrl());
                } else if (result == OPTION_DOWNLOAD_TORRENT) {
                    openTorrent(msg.getTorrent());
                }
            }
        });
    }

    /**
     * Given announcements it will show them.
     * 
     * @param announcements
     */
    public void attemptShowAnnouncements(HashSet<UpdateMessage> announcements) {
        // System.out.println("ABOUT TO SHOW SOME ANNOUNCEMENTS");
        java.util.Iterator<UpdateMessage> it = announcements.iterator();

        while (it.hasNext()) {
            UpdateMessage msg = it.next();
            // System.out.println("UpdateManager.attemptShowAnnouncements(), what about... "
            // + msg);

            if (msg.isShownOnce() && haveShownMessageBefore(msg)) {
                // System.out.println("UpdateManager.attemptShowAnnouncements() - Skipping message:\n"
                // + msg+ "\n");
                continue;
            }

            // check for url to link to with this message
            if (msg.getUrl() != null && !msg.getUrl().trim().equals("")) {
                showUpdateMessage(msg);
            } // if
        } // while
    } // attemptShowAnnouncements

    @SuppressWarnings("unchecked")
    private void loadSeenMessages() {
        // de-serializes seen messages if available
        File f = new File(CommonUtils.getUserSettingsDir(), "seenMessages.dat");
        _seenMessages = new HashSet<UpdateMessage>();

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
            oos.writeObject((HashSet<UpdateMessage>) _seenMessages);
            oos.close();
        } catch (Exception e) {
            System.out.println("UpdateManager.saveSeenMessages() - Cannot serialize.");
            e.printStackTrace();
        }
    }

    /**
     * Checks on a Message map, if we've seen this message before. The message
     * map is serialized on disk everytime we write to it. Its initialized from
     * disk when we start the Update Manager.
     */
    public boolean haveShownMessageBefore(UpdateMessage msg) {
        if (!msg.isShownOnce())
            return false; // we'll ignore messages that can be seen more than
        // once

        loadSeenMessages();

        if (_seenMessages == null || _seenMessages.size() == 0 || !_seenMessages.contains(msg)) {

            if (_seenMessages == null)
                _seenMessages = new HashSet<UpdateMessage>();

            _seenMessages.add(msg);

            saveSeenMessages();
            return false;
        }

        return true;
    }

    /**
     * Given a version string, it compares against the current frostwire
     * version. If frostwire is old, it will return true.
     * 
     * A valid version string looks like this: "MAJOR.RELEASE.SERVICE"
     * 
     * 4.13.1 4.13.2 ... 4.13.134
     * 
     * It will compare each number of the current version to the version
     * published by the update message.
     * 
     * @param messageVersion
     */
    public static boolean isFrostWireOld(String messageVersion) {
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
     * 
     * @param uriStr
     */
    public static void openTorrent(String uriStr) {
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

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
    }
}
