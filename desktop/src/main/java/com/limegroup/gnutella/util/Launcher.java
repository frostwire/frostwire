/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.util;

import org.apache.commons.io.FilenameUtils;
import com.frostwire.util.OSUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/**
 * This class launches files in their associated applications and opens
 * urls in the default browser for different operating systems.  This
 * really only works meaningfully for the Mac and Windows.
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
     * @param url The url to open. Should not be null
     * @return An int indicating the success of the browser launch
     * @throws NullPointerException The URL was null.
     * @throws IOException Either a default browser wasn't found (maybe not set
     * somehow) or failed to be launched.
     * @throws URISyntaxException The URL was invalid.
     * @throws UnsupportedOperationException Opening a URL isn't supported on
     * the current platform.
     */
    public static int openURL(String url)
            throws NullPointerException, UnsupportedOperationException, URISyntaxException, IOException {
        if (url == null) {
            throw new NullPointerException("Attempted to open null URL");
        }

        Desktop.getDesktop().browse(new URI(url));
        return 0;
    }

    /**
     * Launches the file whose abstract path is specified in the `File`
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
    public static FWProcess launchFile(File file) throws IOException,
            SecurityException {
        List<String> forbiddenExtensions = Arrays.asList("exe", "vbs", "lnk",
                "bat", "sys", "com", "js", "scpt");
        if (file.isFile()
                && forbiddenExtensions.contains(FilenameUtils
                .getExtension(file.getName()))) {
            throw new SecurityException();
        }

        try {
            Desktop.getDesktop().open(file);
        } catch (UnsupportedOperationException e) {
            System.err.println("Attempted to open an unsupported file!");
        }

        return null;
    }

    /**
     * Launches the Explorer/Finder and highlights the file.
     *
     * @param file the file to show in explorer
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
                FWProcess.exec(new String[]{"explorer", explorePath});
            } else {
                // launches explorer and highlights the file
                FWProcess.exec(new String[]{"explorer", "/select,", explorePath});
            }
        } else if (OSUtils.isMacOSX()) {
            // launches the Finder and highlights the file
            FWProcess.exec(selectFileCommand(file));
        } else if (OSUtils.isLinux()) {
            if (file.isDirectory()) {
                Desktop.getDesktop().open(file);
            } else if (file.isFile()) {
                Desktop.getDesktop().open(file.getParentFile());
            }
        }
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

}
