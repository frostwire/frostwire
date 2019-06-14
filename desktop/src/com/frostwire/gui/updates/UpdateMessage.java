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

import com.limegroup.gnutella.settings.ApplicationSettings;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;

/**
 * POJO to represent an UpdateMessage.
 */
final class UpdateMessage implements Serializable {
    public static final long serialVersionUID = 44L;
    private int _hashCode = -1; //set to <= 0 if you want it to be recalculated on this.hashCode()
    private String _message;
    private String _messageInstallerReady;
    private String _url;
    private String _messageType; //update | announcement | overlay | hostiles | chat_server
    private String _version = null;
    private String build = null;
    private Date _expiration = null; //only needed for messageType == "announcement"
    private String _torrent = null; //optional torrent url
    private String installerUrl = null; // optional installer url
    private String _os = null; //optional OS string. If this exists and this machine
    //is not that OS, then the reader should discard this message
    private String _showOnce = "false"; //if this message is to be shown once or not
    private String _src = ""; //image src for overlay image message
    private boolean _intro = false; //if overlay should be shown at intro, or after searches.
    private String _lang = "en"; // default language for image
    private String _md5 = ""; // md5 hash code for overlay image
    private String _saveAs = null; // optional, makes InstallerUpdater set a name for the downloaded file

    UpdateMessage(String msgType, String message) {
        setMessageType(msgType);
        setMessage(message);
    }

    public String getMessage() {
        return _message;
    }

    private void setMessage(String m) {
        _message = m;
    }

    String getMessageInstallerReady() {
        return _messageInstallerReady;
    }

    void setMessageInstallerReady(String m) {
        _messageInstallerReady = m;
    }

    public String getUrl() {
        return _url;
    }

    public void setUrl(String u) {
        _url = u;
    }

    public String getSrc() {
        return _src;
    }

    public void setSrc(String src) {
        _src = src;
    }

    boolean isIntro() {
        return _intro;
    }

    void setIntro(boolean intro) {
        _intro = intro;
    }

    public String getLanguage() {
        return _lang;
    }

    public void setLanguage(String lang) {
        _lang = lang;
    }

    String getRemoteMD5() {
        return _md5;
    }

    void setRemoteMD5(String md5) {
        _md5 = md5.trim().toUpperCase();
    } // convert xml to upper case because built function uses uppercase

    String getMessageType() {
        return _messageType;
    }

    // void setMessageType(String mt)
    // If given a wrong msgType, or none, we default to update.
    // Currently valid message types are:
    // "update" : For new frostwire versions
    // "announcement" : For important announcements to the community
    // "overlay" : For overlay promotions
    // "hostiles" : For an update of the hostiles.txt file
    private void setMessageType(String mt) {
        String type = mt != null ? mt.toLowerCase().trim() : "";
        boolean typeIsValid = (type.equals("update") ||
                type.equals("announcement") || type.equals("overlay") ||
                type.equals("chat_server") || type.equals("uxstats"));
        if (mt == null || !typeIsValid) {
            _messageType = "update";
            return;
        }
        _messageType = mt.toLowerCase().trim();
    }

    public String getOs() {
        return _os;
    }

    /**
     * If it receives a valid os string ("windows", "mac", "linux")
     * it will set it.
     * If it receives null or *, it will set _os to null.
     * Having getOS() return null, means this message is for every OS instance.
     */
    public void setOs(String os) {
        _os = null;
        if (os != null) {
            os = os.trim();
            if (os.equalsIgnoreCase("windows") ||
                    os.equalsIgnoreCase("linux") ||
                    os.equalsIgnoreCase("mac")) {
                _os = os.toLowerCase();
            } else if (os.equals("*")) {
                _os = null;
            }
        }
    }

    public String getTorrent() {
        return _torrent;
    }

    public void setTorrent(String t) {
        _torrent = t;
    }

    String getInstallerUrl() {
        return installerUrl;
    }

    void setInstallerUrl(String installerUrl) {
        this.installerUrl = installerUrl;
    }

    private Date getExpiration() {
        return _expiration;
    }

    /**
     * Sets the expiration date out of a string with the timestamp
     * Pass null, and it means this message has no expiration date.
     */
    void setExpiration(String expTimestamp) {
        if (expTimestamp == null || expTimestamp.equals("0")) {
            _expiration = null;
            return;
        }
        try {
            _expiration = new Date(Long.parseLong(expTimestamp));
        } catch (NumberFormatException e) {
            System.out.println("Expiration passed cannot be converted to a long");
            _expiration = null;
        }
    } //setExpiration

    boolean hasExpired() {
        //not meant to expire
        if (getExpiration() == null)
            return false;
        long serverTimestamp = UpdateManager.getInstance().getServerTime().getTime();
        long myTimestamp = _expiration.getTime();
        return myTimestamp < serverTimestamp;
    }

    public String getVersion() {
        if (_version != null && _version.equals("")) {
            _version = null;
        }
        return _version;
    }

    public void setVersion(String v) {
        _version = v;
    }

    public String getBuild() {
        if (build != null && build.equals("")) {
            build = null;
        }
        return build;
    }

    public void setBuild(String b) {
        build = b;
    }

    boolean isShownOnce() {
        return _showOnce.equalsIgnoreCase("true");
    }

    void setShowOnce(String s) {
        if (s != null)
            _showOnce = s;
    }

    public boolean equals(Object obj) {
        return obj instanceof UpdateMessage &&
                obj.hashCode() == this.hashCode() &&
                isIntro() == ((UpdateMessage) obj).isIntro();
    }

    public int hashCode() {
        if (_hashCode <= 0) {
            try {
                String byteString = _message + _url + _messageType + _version + _torrent + _os + _showOnce;
                _hashCode = byteString.hashCode();
            } catch (Throwable e) {
                e.printStackTrace(); // just in case
            }
        }
        return _hashCode;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
    }

    public String toString() {
        return "\n" +
                "UpdateMessage @" + super.hashCode() +
                "{" +
                "_hashCode : " + hashCode() + ", \n" +
                "_message : " + getMessage() + ", \n" +
                "_url : " + getUrl() + ", \n" +
                "_messageType : " + getMessageType() + ", \n" +
                "_version : " + getVersion() + ", \n" +
                "build : " + getBuild() + ", \n" +
                "_expiration : " + getExpiration() + ", \n" +
                "_torrent : " + getTorrent() + ", \n" +
                "installerUrl : " + getInstallerUrl() + ", \n" +
                "_os : " + getOs() + ", \n" +
                "_language : " + getLanguage() + ", \n" +
                "_applanguage : " + ApplicationSettings.getLanguage() + ", \n" +
                "_showOnce : " + isShownOnce() + ", \n" +
                "_isIntro : " + isIntro() + ", \n" +
                "_md5 : " + getRemoteMD5() + ", \n" +
                "_saveAs : " + getSaveAs() + ", \n" +
                "}\n";
    }

    public String getSaveAs() {
        return _saveAs;
    }

    public void setSaveAs(String saveAs) {
        _saveAs = saveAs;
    }
} //UpdateMessage class
