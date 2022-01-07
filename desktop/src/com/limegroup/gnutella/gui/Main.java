/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui;

import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.jlibtorrent.swig.libtorrent_jni;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.util.FrostWireUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * This class constructs an <tt>Initializer</tt> instance that constructs
 * all of the necessary classes for the application.
 */
public class Main {
    private static URL CHOSEN_SPLASH_URL = null;

    /**
     * Creates an <tt>Initializer</tt> instance that constructs the
     * necessary classes for the application.
     *
     * @param args the array of command line arguments
     */
    public static void main(String[] args) {
        ThemeMediator.changeTheme();
        System.setProperty("sun.awt.noerasebackground", "true");
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        if (OSUtils.isWindows()) {
            System.setProperty("jlibtorrent.jni.path", getWindowsJLibtorrentPath());
        }
        if (OSUtils.isMacOSX()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.eawt.CocoaComponent.CompatibilityMode", "false");
            System.setProperty("jlibtorrent.jni.path", getMacOSJLibtorrentPath());
        }
        if (OSUtils.isLinux()) {
            System.setProperty("jlibtorrent.jni.path", getLinuxJLibtorrentPath());
        }
        //System.out.println("1: Main.main("+args+")");
        // make sure jlibtorrent is statically loaded on time to avoid jni symbols not found issues.
        libtorrent_jni.version();
        Frame splash = null;
        try {
            // show initial splash screen only if there are no arguments
            if (args == null || args.length == 0)
                splash = showInitialSplash();
            // load the GUI through reflection so that we don't reference classes here,
            // which would slow the speed of class-loading, causing the splash to be
            // displayed later.
            try {
                Class.forName("com.limegroup.gnutella.gui.GUILoader").
                        getMethod("load", new Class[]{String[].class, Frame.class}).
                        invoke(null, args, splash);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Shows the initial splash window.
     */
    private static Frame showInitialSplash() {
        Frame splashFrame = null;
        Image image = null;
        URL imageURL = getChosenSplashURL();
        if (imageURL != null) {
            try {
                image = ImageIO.read(imageURL);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (image != null) {
                splashFrame = AWTSplashWindow.splash(image);
            }
        }
        return splashFrame;
    }

    /**
     * Tries to get a random splash every time. It keeps track of the
     * last 2 shown splashes to avoid recent collisions.
     */
    public static URL getChosenSplashURL() {
        if (CHOSEN_SPLASH_URL != null)
            return CHOSEN_SPLASH_URL;
        final String splashPath = "org/limewire/gui/images/app_splash.jpg";
        CHOSEN_SPLASH_URL = ClassLoader.getSystemResource(splashPath);
        return CHOSEN_SPLASH_URL;
    }

    private static String getWindowsJLibtorrentPath() {
        return getJLibtorrentPath(".dll");
    }

    private static String getMacOSJLibtorrentPath() {
        String pathWithoutOSArch = getJLibtorrentPath(".dylib");
        String os_arch = System.getProperty("os.arch");
        if ("aarch64".equals(os_arch)) {
            os_arch = "arm64";
        }
        return pathWithoutOSArch.replace(".dylib", "." + os_arch + ".dylib");
    }

    private static String getLinuxJLibtorrentPath() {
        return getJLibtorrentPath(".so");
    }

    private static String getJLibtorrentPath(String libraryExtension) {
        String jarPath = new File(FrostWireUtils.getFrostWireJarPath()).getAbsolutePath();
        String libraryName = "libjlibtorrent";
        if (OSUtils.isWindows()) {
            jarPath = jarPath.replaceAll("%20", " ");
            libraryName = "jlibtorrent";
        }
        boolean isRelease = !jarPath.contains("frostwire" + File.separator + "desktop");
        String productionLibPath = jarPath + File.separator + ((isRelease) ? libraryName : "lib" + File.separator + "native" + File.separator + libraryName) + libraryExtension;
        File fileFromProductionPath = new File(productionLibPath);
        if (fileFromProductionPath.exists()) {
            System.out.println("Using jlibtorrent (production path): " + fileFromProductionPath.getAbsolutePath());
            return fileFromProductionPath.getAbsolutePath();
        }
        String pathRunningFromCmdLine = jarPath + File.separator + ".." + File.separator + ".." + File.separator + ".." + File.separator + "lib" + File.separator + "native" + File.separator + libraryName + libraryExtension;
        File fileFromCMDLine = new File(pathRunningFromCmdLine);
        if (fileFromCMDLine.exists()) {
            System.out.println("Using jlibtorrent (cmd line path): " + fileFromCMDLine.getAbsolutePath());
            return fileFromCMDLine.getAbsolutePath();
        }
        String pathRunningFromIntelliJ = jarPath + File.separator + ".." + File.separator + ".." + File.separator + "lib" + File.separator + "native" + File.separator + libraryName + libraryExtension;
        File fileFromIntelliJProject = new File(pathRunningFromIntelliJ);
        if (fileFromIntelliJProject.exists()) {
            System.out.println("Using jlibtorrent (intellij path): " + fileFromIntelliJProject.getAbsolutePath());
            return fileFromIntelliJProject.getAbsolutePath();
        }
        System.out.println("Using jlibtorrent (fallback): " + "../../lib/native/" + libraryName + libraryExtension);
        return ".." + File.separator + ".." + File.separator + "lib" + File.separator + "native" + File.separator + libraryName + libraryExtension;
    }
}
