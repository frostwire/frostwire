/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.util;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.settings.URLHandlerSettings;
import org.apache.commons.io.FilenameUtils;
import com.frostwire.util.OSUtils;
import org.limewire.util.StringUtils;
import org.limewire.util.SystemUtils;

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
    public static FWProcess launchFile(File file) throws IOException,
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
    private static FWProcess launchFileMacOSX(final String file) throws IOException {
        return FWProcess.exec(new String[]{"open", file});
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
    private static FWProcess launchFileOther(String path) throws IOException {
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
        return FWProcess.exec(strs);
    }
}
