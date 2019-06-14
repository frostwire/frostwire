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

package com.limegroup.gnutella.util;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.settings.URLHandlerSettings;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.OSUtils;
import org.limewire.util.StringUtils;
import org.limewire.util.SystemUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * This class launches files in their associated applications and opens
 * urls in the default browser for different operating systems.  This
 * really only works meaningfully for the Mac and Windows.<p>
 * <p>
 * Acknowledgement goes to Eric Albert for demonstrating the general
 * technique for loading the MRJ classes in his frequently-used
 * "BrowserLauncher" code.
 * <p>
 * This code is Copyright 1999-2001 by Eric Albert (ejalbert@cs.stanford.edu)
 * and may be redistributed or modified in any form without restrictions as
 * long as the portion of this comment from this paragraph through the end of
 * the comment is not removed.  The author requests that he be notified of any
 * application, applet, or other binary that makes use of this code, but that's
 * more out of curiosity than anything and is not required.  This software
 * includes no warranty.  The author is not repsonsible for any loss of data
 * or functionality or any adverse or unexpected effects of using this software.
 * <p>
 * Credits:
 * <br>Steven Spencer, JavaWorld magazine
 * (<a href="http://www.javaworld.com/javaworld/javatips/jw-javatip66.html">Java Tip 66</a>)
 * <br>Thanks also to Ron B. Yeh, Eric Shapiro, Ben Engber, Paul Teitlebaum,
 * Andrea Cantatore, Larry Barowski, Trevor Bedzek, Frank Miedrich, and Ron
 * Rabakukk
 *
 * @author Eric Albert
 * (<a href="mailto:ejalbert@cs.stanford.edu">ejalbert@cs.stanford.edu</a>)
 * @version 1.4b1 (Released June 20, 2001)
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class Launcher {
    /**
     * This class should be never be instantiated; this just ensures so.
     */
    private Launcher() {
    }

    /**
     * Opens the specified url in a browser.
     *
     * <p>A browser will only be opened if the underlying operating system
     * recognizes the url as one that should be opened in a browser,
     * namely a url that ends in .htm or .html.
     *
     * @param url The url to open
     * @return An int indicating the success of the browser launch
     * @throws IOException if the url cannot be loaded do to an IO problem
     */
    public static int openURL(String url) throws IOException {
        if (url == null) {
            return -1;
        }
        if (OSUtils.isWindows()) {
            return openURLWindows(url);
        } else if (OSUtils.isMacOSX()) {
            openURLMac(url);
        } else {
            // Other OS
            launchFileOther(url);
        }
        return -1;
    }

    /**
     * Opens the default web browser on windows, passing it the specified
     * url.
     *
     * @param url the url to open in the browser
     * @return the error code of the native call, -1 if the call failed
     * for any reason
     */
    private static int openURLWindows(String url) throws IOException {
        //Windows like escaping of '&' character when passed as part of a parameter
        //& -> ^&
        url = url.replace("&", "^&");
        String[] command = new String[]{"cmd.exe", "/c", "start", url};
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.start();
        return 0;
    }

    /**
     * Opens the specified url in the default browser on the Mac.
     * This makes use of the dynamically-loaded MRJ classes.
     *
     * @param url the url to load
     * @throws <tt>IOException</tt> if the necessary mac classes were not
     *                              loaded successfully or if another exception was
     *                              throws -- it wraps these exceptions in an <tt>IOException</tt>
     */
    private static void openURLMac(String url) throws IOException {
        Runtime.getRuntime().exec(new String[]{"open", url});
    }

    /**
     * Launches the file whose abstract path is specified in the <tt>File</tt>
     * parameter. This method will not launch any file with .exe, .vbs, .lnk,
     * .bat, .sys, or .com extensions, diplaying an error if one of the file is
     * of one of these types.
     *
     * @return an object for accessing the launch process; null, if the process
     * can be represented (e.g. the file was launched through a native
     * call)
     * @throws IOException       if the file cannot be launched
     * @throws SecurityException if the file has an extension that is not allowed
     */
    public static LimeProcess launchFile(File file) throws IOException,
            SecurityException {
        List<String> forbiddenExtensions = Arrays.asList("exe", "vbs", "lnk",
                "bat", "sys", "com", "js", "scpt");
        if (file.isFile()
                && forbiddenExtensions.contains(FilenameUtils
                .getExtension(file.getName()))) {
            throw new SecurityException();
        }
        String path = file.getCanonicalPath();
        if (OSUtils.isWindows()) {
            launchFileWindows(path);
            return null;
        } else if (OSUtils.isMacOSX()) {
            return launchFileMacOSX(path);
        } else {
            // Other OS, use helper apps
            return launchFileOther(path);
        }
    }

    /**
     * Launches the Explorer/Finder and highlights the file.
     *
     * @param file the file to show in explorer
     * @return null, if not supported by platform; the launched process otherwise
     * @see #launchFile(File)
     */
    public static void launchExplorer(File file) throws IOException, SecurityException {
        if (OSUtils.isWindows()) {
            String explorePath = file.getPath();
            try {
                explorePath = file.getCanonicalPath();
            } catch (IOException ignored) {
            }
            if (file.isDirectory()) {
                // launches explorer in the directory
                LimeProcess.exec(new String[]{"explorer", explorePath});
            } else {
                // launches explorer and highlights the file
                LimeProcess.exec(new String[]{"explorer", "/select,", explorePath});
            }
        } else if (OSUtils.isMacOSX()) {
            // launches the Finder and highlights the file
            LimeProcess.exec(selectFileCommand(file));
        } else if (OSUtils.isLinux()) {
            if (file.isDirectory()) {
                Desktop.getDesktop().open(file);
            } else if (file.isFile()) {
                Desktop.getDesktop().open(file.getParentFile());
            }
        }
    }

    /**
     * Launches the given file on Windows.
     *
     * @param path the path of the file to launch
     * @return an int for the exit code of the native method
     */
    private static int launchFileWindows(String path) throws IOException {
        try {
            return SystemUtils.openFile(path);
        } catch (IOException iox) {
            throw new LaunchException(iox, path);
        }
    }

    /**
     * Launches a file on OSX, appending the full path of the file to the
     * "open" command that opens files in their associated applications
     * on OSX.
     *
     * @param file the <tt>File</tt> instance denoting the abstract pathname
     *             of the file to launch
     * @return
     * @throws IOException if an I/O error occurs in making the runtime.exec()
     *                     call or in getting the canonical path of the file
     */
    private static LimeProcess launchFileMacOSX(final String file) throws IOException {
        return LimeProcess.exec(new String[]{"open", file});
    }

    /**
     * Launches the Finder and selects the given File
     */
    private static String[] selectFileCommand(File file) {
        String path = null;
        try {
            path = file.getCanonicalPath();
        } catch (IOException err) {
            path = file.getAbsolutePath();
        }
        return new String[]{
                "osascript",
                "-e", "set unixPath to \"" + path + "\"",
                "-e", "set hfsPath to POSIX file unixPath",
                "-e", "tell application \"Finder\"",
                "-e", "activate",
                "-e", "select hfsPath",
                "-e", "end tell"
        };
    }

    /**
     * Attempts to launch the given file.
     *
     * @throws IOException if the call to Runtime.exec throws an IOException
     *                     or if the Process created by the Runtime.exec call
     *                     throws an InterruptedException
     */
    private static LimeProcess launchFileOther(String path) throws IOException {
        String handler;
        if (MediaType.getAudioMediaType().matches(path)) {
            handler = URLHandlerSettings.AUDIO_PLAYER.getValue();
        } else if (MediaType.getVideoMediaType().matches(path)) {
            handler = URLHandlerSettings.VIDEO_PLAYER.getValue();
        } else if (MediaType.getImageMediaType().matches(path)) {
            handler = URLHandlerSettings.IMAGE_VIEWER.getValue();
        } else {
            handler = URLHandlerSettings.BROWSER.getValue();
        }
        QuotedStringTokenizer tok = new QuotedStringTokenizer(handler);
        String[] strs = new String[tok.countTokens()];
        for (int i = 0; i < strs.length; i++) {
            strs[i] = StringUtils.replace(tok.nextToken(), "$URL$", path);
        }
        return LimeProcess.exec(strs);
    }
}
