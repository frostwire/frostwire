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

package com.limegroup.gnutella.gui;

import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.jlibtorrent.swig.libtorrent_jni;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.util.OSUtils;

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
        if (OSUtils.isWindows() && !OSUtils.isMachineX64()) {
            String jlibtorrentPath = getWindows32JLibtorrentPath();
            System.setProperty("jlibtorrent.jni.path", jlibtorrentPath);
        }
        if (OSUtils.isMacOSX()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.eawt.CocoaComponent.CompatibilityMode", "false");
        }
        if (OSUtils.isLinux() && !OSUtils.isMachineX64()) {
            String jlibtorrentPath = getLinux32JLibtorrentPath();
            System.setProperty("jlibtorrent.jni.path", jlibtorrentPath);
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
                Class.forName("com.limegroup.gnutella.gui.GUILoader").getMethod("load", new Class[]{String[].class, Frame.class}).invoke(null, args, splash);
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

    private static String getWindows32JLibtorrentPath() {
        String jarPath = new File(FrostWireUtils.getFrostWireJarPath()).getAbsolutePath();
        jarPath = jarPath.replaceAll("%20", " ");
        boolean isRelease = !jarPath.contains("frostwire-desktop");
        String libPath = jarPath + File.separator + ((isRelease) ? "jlibtorrentX86.dll" : "lib/native/jlibtorrentX86.dll");
        if (!new File(libPath).exists()) {
            libPath = new File(jarPath + File.separator + "../../lib/native/jlibtorrentx86.dll").getAbsolutePath();
        }
        System.out.println("Using jlibtorrent 32 bits: " + libPath);
        return libPath;
    }

    private static String getLinux32JLibtorrentPath() {
        String jarPath = new File(FrostWireUtils.getFrostWireJarPath()).getAbsolutePath();
        boolean isRelease = !jarPath.contains("frostwire-desktop");
        String libPath = jarPath + File.separator + ((isRelease) ? "libjlibtorrentX86.so" : "lib/native/libjlibtorrentX86.so");
        if (!new File(libPath).exists()) {
            libPath = new File(jarPath + File.separator + "../../lib/native/libjlibtorrentx86.so").getAbsolutePath();
        }
        System.out.println("Using jlibtorrent 32 bits: " + libPath);
        return libPath;
    }
}
