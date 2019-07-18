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

package org.limewire.util;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Properties;

/**
 * Provides convenience functionality ranging from getting user information,
 * copying files to getting the stack traces of all current threads.
 * <DL>
 * <DT>User Information
 * <DD>Get a username, a user home directory, etc.
 *
 * <DT>File Operation
 * <DD>Copy resource files, get the current directory, set, get and validate the
 * directory to store user settings. Also, you can use convertFileName to replace
 * operating system specific illegal characters.
 *
 * <DT>Threads
 * <DD>Get the stack traces of all current threads.
 *
 * <DT>Time
 * <DD>Convert an integer value representing the seconds into an appropriate days,
 * hour, minutes and seconds format (d:hh:mm:ss).
 *
 * <DT>Decode
 * <DD>Decode a URL encoded from a string.
 *
 * <DT>Resources
 * <DD>Retrieve a resource file and a stream.
 * </DL>
 */
public class CommonUtils {
    private static final String FROSTWIRE_420_PREFS_DIR_NAME = ".frostwire4.20";
    private static final String FROSTWIRE_500_PREFS_DIR_NAME = ".frostwire5";
    private static final String META_SETTINGS_KEY_USER_SETTINGS_WINDOWS = "user.settings.dir.windows";
    private static final String META_SETTINGS_KEY_USER_SETTINGS_MAC = "user.settings.dir.mac";
    private static final String META_SETTINGS_KEY_USER_SETTINGS_POSIX = "user.settings.dir.posix";
    private static final String META_SETTINGS_KEY_ROOT_FOLDER_WINDOWS = "user.settings.root_folder.windows";
    private static final String META_SETTINGS_KEY_ROOT_FOLDER_MAC = "user.settings.root_folder.mac";
    private static final String META_SETTINGS_KEY_ROOT_FOLDER_POSIX = "user.settings.root_folder.posix";
    //private static Boolean IS_PORTABLE = null;
    /**
     * Several arrays of illegal characters on various operating systems.
     * Used by convertFileName
     */
    private static final char[] ILLEGAL_CHARS_ANY_OS = {'/', '\n', '\r', '\t', '\0', '\f'};
    private static final char[] ILLEGAL_CHARS_UNIX = {'`'};
    private static final char[] ILLEGAL_CHARS_WINDOWS = {'?', '*', '\\', '<', '>', '|', '\"', ':'};
    private static final char[] ILLEGAL_CHARS_MACOS = {':'};
    /**
     * The location where settings are stored.
     */
    private static volatile File settingsDirectory = null;

    /**
     * Returns the user home directory.
     *
     * @return the <tt>File</tt> instance denoting the abstract pathname of
     * the user's home directory, or <tt>null</tt> if the home directory
     * does not exist
     */
    public static File getUserHomeDir() {
        return new File(System.getProperty("user.home"));
    }

    /**
     * Gets an InputStream from a resource file.
     *
     * @param location the location of the resource in the resource file
     * @return an <tt>InputStream</tt> for the resource
     * @throws IOException if the resource could not be located or there was
     *                     another IO error accessing the resource
     */
    static InputStream getResourceStream(String location) throws IOException {
        ClassLoader cl = CommonUtils.class.getClassLoader();
        URL resource;
        if (cl == null) {
            resource = ClassLoader.getSystemResource(location);
        } else {
            resource = cl.getResource(location);
        }
        if (resource == null)
            throw new IOException("null resource: " + location);
        else
            return resource.openStream();
    }

    /**
     * Copied from URLDecoder.java
     */
    public static String decode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '+':
                    sb.append(' ');
                    break;
                case '%':
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(s);
                    }
                    i += 2;
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        // Undo conversion to external encoding
        String result = sb.toString();
        try {
            byte[] inputBytes = result.getBytes("8859_1");
            result = new String(inputBytes);
        } catch (UnsupportedEncodingException e) {
            // The system should always have 8859_1
        }
        return result;
    }

    /**
     * Converts a value in seconds to:
     * "d:hh:mm:ss" where d=days, hh=hours, mm=minutes, ss=seconds, or
     * "h:mm:ss" where h=hours<24, mm=minutes, ss=seconds, or
     * "m:ss" where m=minutes<60, ss=seconds
     */
    public static String seconds2time(long seconds) {
        int minutes = (int) (seconds / 60);
        seconds = seconds - minutes * 60;
        int hours = minutes / 60;
        minutes = minutes - hours * 60;
        int days = hours / 24;
        hours = hours - days * 24;
        // build the numbers into a string
        StringBuilder time = new StringBuilder();
        if (days != 0) {
            time.append(days);
            time.append(":");
            if (hours < 10)
                time.append("0");
        }
        if (days != 0 || hours != 0) {
            time.append(hours);
            time.append(":");
            if (minutes < 10)
                time.append("0");
        }
        time.append(minutes);
        time.append(":");
        if (seconds < 10)
            time.append("0");
        time.append(seconds);
        return time.toString();
    }

    /**
     * Cleans up the filename and truncates it to length of 180 bytes by calling
     * {@link #convertFileName(String, int) convertFileName(String, 180)}.
     */
    public static String convertFileName(String name) {
        return convertFileName(name, 180);
    }

    /**
     * Cleans up the filename from illegal characters and truncates it to the
     * length of bytes specified.
     *
     * @param name     the filename to clean up
     * @param maxBytes the maximumm number of bytes the cleaned up file name
     *                 can take up
     * @return the cleaned up file name
     */
    private static String convertFileName(String name, int maxBytes) {
        // use default encoding which is also used for files judging from the
        // property name "file.encoding"
        try {
            return convertFileName(name, maxBytes, Charset.defaultCharset());
        } catch (CharacterCodingException cce) {
            try {
                // UTF-8 should always be available
                return convertFileName(name, maxBytes, Charset.forName("UTF-8"));
            } catch (CharacterCodingException e) {
                // should not happen, UTF-8 can encode unicode and gives us a
                // good length estimate
                throw new RuntimeException("UTF-8 should have encoded: " + name, e);
            }
        }
    }

    /**
     * Replaces OS specific illegal characters from any filename with '_',
     * including ( / \n \r \t ) on all operating systems, ( ? * \  < > | " )
     * on Windows, ( ` ) on unix.
     *
     * @param name     the filename to check for illegal characters
     * @param maxBytes the maximum number of bytes for the resulting file name,
     *                 must be > 0
     * @return String containing the cleaned filename
     * @throws CharacterCodingException if the charset could not encode the
     *                                  characters in <code>name</code>
     * @throws IllegalArgumentException if maxBytes <= 0
     */
    private static String convertFileName(String name, int maxBytes, Charset charSet) throws CharacterCodingException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be > 0");
        }
        // ensure that block-characters aren't in the filename.
        name = I18NConvert.instance().compose(name);
        // if the name is too long, reduce it.  We don't go all the way
        // up to 255 because we don't know how long the directory name is
        // We want to keep the extension, though.
        if (name.length() > maxBytes || name.getBytes().length > maxBytes) {
            int extStart = name.lastIndexOf('.');
            if (extStart == -1) { // no extension, weird, but possible
                name = getPrefixWithMaxBytes(name, maxBytes, charSet);
            } else {
                // if extension is greater than 11, we truncate it.
                // ( 11 = '.' + 10 extension bytes )
                int extLength = name.length() - extStart;
                int extEnd = extLength > 11 ? extStart + 11 : name.length();
                byte[] extension = getMaxBytes(name.substring(extStart, extEnd), 16, charSet);
                try {
                    // disregard extension if we lose too much of the name
                    // since the name is also used for searching
                    if (extension.length >= maxBytes - 10) {
                        name = getPrefixWithMaxBytes(name, maxBytes, charSet);
                    } else {
                        name = getPrefixWithMaxBytes(name, maxBytes - extension.length, charSet) + new String(extension, charSet.name());
                    }
                } catch (UnsupportedEncodingException uee) {
                    throw new RuntimeException("Could not handle string", uee);
                }
            }
        }
        for (char ILLEGAL_CHARS_ANY_O : ILLEGAL_CHARS_ANY_OS) name = name.replace(ILLEGAL_CHARS_ANY_O, '_');
        if (OSUtils.isWindows() || OSUtils.isOS2()) {
            for (char ILLEGAL_CHARS_WINDOW : ILLEGAL_CHARS_WINDOWS) name = name.replace(ILLEGAL_CHARS_WINDOW, '_');
        } else if (OSUtils.isLinux() || OSUtils.isSolaris()) {
            for (char aILLEGAL_CHARS_UNIX : ILLEGAL_CHARS_UNIX) name = name.replace(aILLEGAL_CHARS_UNIX, '_');
        } else if (OSUtils.isMacOSX()) {
            for (char ILLEGAL_CHARS_MACO : ILLEGAL_CHARS_MACOS) name = name.replace(ILLEGAL_CHARS_MACO, '_');
        }
        return name;
    }

    /**
     * Returns the prefix of <code>string</code> which takes up a maximum
     * of <code>maxBytes</code>.
     */
    private static String getPrefixWithMaxBytes(String string, int maxBytes, Charset charSet) throws CharacterCodingException {
        try {
            return new String(getMaxBytes(string, maxBytes, charSet), charSet.name());
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Could not recreate string", uee);
        }
    }

    /**
     * Returns the first <code>maxBytes</code> of <code>string</code> encoded
     * using the encoder of <code>charSet</code>
     *
     * @param string   whose prefix bytes to return
     * @param maxBytes the maximum number of bytes to return
     * @param charSet  the char set used for encoding the characters into bytes
     * @return the array of bytes of length <= maxBytes
     * @throws CharacterCodingException if the char set's encoder could not
     *                                  handle the characters in the string
     */
    private static byte[] getMaxBytes(String string, int maxBytes, Charset charSet) throws CharacterCodingException {
        byte[] bytes = new byte[maxBytes];
        ByteBuffer out = ByteBuffer.wrap(bytes);
        CharBuffer in = CharBuffer.wrap(string.toCharArray());
        CharsetEncoder encoder = charSet.newEncoder();
        CoderResult cr = encoder.encode(in, out, true);
        encoder.flush(out);
        if (cr.isError()) {
            cr.throwException();
        }
        byte[] result = new byte[out.position()];
        System.arraycopy(bytes, 0, result, 0, result.length);
        return result;
    }

    /**
     * Returns the user's current working directory as a <tt>File</tt>
     * instance, or <tt>null</tt> if the property is not set.
     *
     * @return the user's current working directory as a <tt>File</tt>
     * instance, or <tt>null</tt> if the property is not set
     */
    public static File getCurrentDirectory() {
        return new File(System.getProperty("user.dir"));
    }

    public static String getExecutableDirectory() {
        Class<?> clazz;
        String path;
        String defaultPath = "/Applications/FrostWire.app/";
        String decodedPath = defaultPath;
        try {
            clazz = Class.forName("com.limegroup.gnutella.gui.Main");
            if (clazz != null) {
                path = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
                decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
                if (decodedPath != null && decodedPath.toLowerCase().lastIndexOf("frostwire.jar") != -1) {
                    decodedPath = decodedPath.substring(0, decodedPath.toLowerCase().lastIndexOf("frostwire.jar"));
                }
            }
        } catch (Throwable t) {
            decodedPath = defaultPath;
        }
        return decodedPath;
    }

    /**
     * Validates a potential settings directory.
     * This returns the validated directory, or throws an IOException
     * if it can't be validated.
     */
    private static File validateSettingsDirectory(File dir) throws IOException {
        dir = dir.getAbsoluteFile();
        if (!dir.isDirectory()) {
            dir.delete(); // delete whatever it may have been
            if (!dir.mkdirs())
                throw new IOException("could not create preferences directory: " + dir);
        }
        if (!dir.canWrite())
            throw new IOException("settings dir not writable: " + dir);
        if (!dir.canRead())
            throw new IOException("settings dir not readable: " + dir);
        // Validate that you can write a file into settings directory.
        //  catches vista problem where if settings directory is
        //  locked canRead and canWrite still return true
        File file = File.createTempFile("test", "test", dir);
        if (!file.exists())
            throw new IOException("can't write test file in directory: " + dir);
        file.delete();
        return dir;
    }

    /**
     * Returns the directory where all user settings should be stored.  This
     * is where all application data should be stored.  If the directory is not
     * set, this returns the user's home directory.
     * <p>
     * settingsDirectory has already been set at this point as portable if we're on portable.
     * see LimeCoreGlue.preinstall()
     */
    public synchronized static File getUserSettingsDir() {
        if (settingsDirectory != null)
            return settingsDirectory;
        else
            return getUserSettingsDir2();
    }

    /**
     * Sets the new settings directory.
     * The settings directory cannot be set more than once.
     * <p>
     * If the directory can't be set (because it isn't a folder, can't be made into
     * a folder, or isn't readable and writable), an IOException is thrown.
     */
    public static void setUserSettingsDir(File settingsDir) throws IOException {
        if (settingsDirectory != null)
            throw new IllegalStateException("settings directory already set!");
        settingsDirectory = validateSettingsDirectory(settingsDir);
    }

    /**
     * Returns the location where the user settings directory should be placed.
     */
    private static File getUserSettingsDir2() {
        // LOGIC:
        // On all platforms other than Windows or OSX,
        // this will return <user-home>/.frostwire<versionMajor.versionMinor>
        // On OSX, this will return <user-home>/Library/Preferences/FrostWire5
        // On Windows, this first tries to find:
        // a) <user-home>/$LIMEWIRE_PREFS_DIR/.frostwire
        // b) <user-home>/$APPDATA/FrostWire
        // c) <user-home/.frostwire
        // If the $LIMEWIRE_PREFS_DIR variable doesn't exist, it falls back
        // to trying b).  If The $APPDATA variable can't be read or doesn't
        // exist, it falls back to a).
        // If using a) or b), and neither of those directories exist, but c)
        // does, then c) is used.  Once a) or b) exist, they are used indefinitely.
        // If neither a), b) nor c) exist, then the former is created in preference of
        // of a), then b).        
        Properties metaConfiguration = CommonUtils.loadMetaConfiguration();
        if (!metaConfiguration.isEmpty()) {
            //override this logic if it's been specified in .meta configuration file.
            return CommonUtils.getPortableSettingsDir(metaConfiguration);
        }
        File userDir = CommonUtils.getUserHomeDir();
        // Changing permissions without permission in Unix is rude
        if (OSUtils.isWindows() && userDir.exists())
            FileUtils.setWriteable(userDir);
        File settingsDir = new File(userDir, FROSTWIRE_500_PREFS_DIR_NAME);
        if (OSUtils.isMacOSX()) {
            settingsDir = new File(CommonUtils.getUserHomeDir(), "Library/Preferences/FrostWire5");
        }
        return settingsDir;
    }

    public static File getFrostWire4UserSettingsDir() {
        // LOGIC:
        // On all platforms other than Windows or OSX,
        // this will return <user-home>/.frostwire<versionMajor.versionMinor>
        // On OSX, this will return <user-home>/Library/Preferences/FrostWire
        // On Windows, this first tries to find:
        // a) <user-home>/$LIMEWIRE_PREFS_DIR/.frostwire
        // b) <user-home>/$APPDATA/FrostWire
        // c) <user-home/.frostwire
        // If the $LIMEWIRE_PREFS_DIR variable doesn't exist, it falls back
        // to trying b).  If The $APPDATA variable can't be read or doesn't
        // exist, it falls back to a).
        // If using a) or b), and neither of those directories exist, but c)
        // does, then c) is used.  Once a) or b) exist, they are used indefinitely.
        // If neither a), b) nor c) exist, then the former is created in preference of
        // of a), then b).        
        File userDir = CommonUtils.getUserHomeDir();
        //        // Changing permissions without permission in Unix is rude
        //        if(!OSUtils.isPOSIX() && userDir != null && userDir.exists())
        //            FileUtils.setWriteable(userDir);
        File settingsDir = new File(userDir, FROSTWIRE_420_PREFS_DIR_NAME);
        if (OSUtils.isMacOSX()) {
            settingsDir = new File(CommonUtils.getUserHomeDir(), "Library/Preferences/FrostWire");
        }
        return settingsDir;
    }

    public static boolean isDebugMode() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
    }

    /**
     * Looks for a ".meta" configuration file on the same folder as the FrostWire executable.
     * If found this file should contain the following configuration values:
     * <p>
     * user.settings.dir.windows = <relative path to frostwire settings directory for windows installation>
     * user.settings.dir.mac = <relative path to frostwire settings directory for windows installation>
     * user.settings.dir.posix = <relative path to frostwire settings directory for posix installation>
     *
     * @return A Properties object, if the .meta file is not found returns an empty Properties object.
     */
    public static Properties loadMetaConfiguration() {
        Properties meta = new Properties();
        File metaFile = new File(".meta");
        if (metaFile.exists() && metaFile.isFile() && metaFile.canRead()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(metaFile);
                meta.load(fis);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeQuietly(fis);
            }
        }
        return meta;
    }

    public static File getPortableSettingsDir(Properties metaConfiguration) {
        return getPortableMetaFile(metaConfiguration, CommonUtils.META_SETTINGS_KEY_USER_SETTINGS_WINDOWS, CommonUtils.META_SETTINGS_KEY_USER_SETTINGS_MAC, CommonUtils.META_SETTINGS_KEY_USER_SETTINGS_POSIX);
    }

    /**
     * The root folder where all the default save directories exist.
     */
    public static File getPortableRootFolder() {
        Properties metaConfiguration = CommonUtils.loadMetaConfiguration();
        return getPortableRootFolder(metaConfiguration);
    }

    private static File getPortableRootFolder(Properties metaConfiguration) {
        return getPortableMetaFile(metaConfiguration, CommonUtils.META_SETTINGS_KEY_ROOT_FOLDER_WINDOWS, CommonUtils.META_SETTINGS_KEY_ROOT_FOLDER_MAC, CommonUtils.META_SETTINGS_KEY_ROOT_FOLDER_POSIX);
    }

    /**
     * Get the file/dir pointed out by a configuration key form the .meta Properties object
     * depending on what operating system you are on.
     *
     * @return The file if the key has been specified, otherwise null.
     */
    private static File getPortableMetaFile(Properties metaConfiguration, final String windowsKey, final String macKey, final String posixKey) {
        File portableMetaDir = null;
        String metaKey = null;
        if (OSUtils.isWindows() && metaConfiguration.containsKey(windowsKey)) {
            metaKey = windowsKey;
        } else if (OSUtils.isMacOSX() && metaConfiguration.containsKey(macKey)) {
            metaKey = macKey;
        } else if (OSUtils.isLinux() && metaConfiguration.containsKey(posixKey)) {
            metaKey = posixKey;
        }
        if (metaKey != null) {
            portableMetaDir = new File(metaConfiguration.getProperty(metaKey));
            if (!portableMetaDir.exists()) {
                portableMetaDir.mkdirs();
            }
            if (OSUtils.isWindows()) {
                FileUtils.setWriteable(portableMetaDir);
            }
        }
        return portableMetaDir;
    }

    public static boolean isPortable() {
        // aldenml: Until we fully implement it
//        if (IS_PORTABLE == null) {
//            Properties metaConfiguration = CommonUtils.loadMetaConfiguration();
//            IS_PORTABLE = !metaConfiguration.isEmpty();
//        }
        return false;//IS_PORTABLE;
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }
}
