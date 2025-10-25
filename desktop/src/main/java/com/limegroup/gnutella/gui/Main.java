/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui;

import com.frostwire.jlibtorrent.swig.libtorrent_jni;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.util.CommonUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * This class constructs an `Initializer` instance that constructs
 * all the necessary classes for the application.
 */
public class Main {
    private static URL CHOSEN_SPLASH_URL = null;

    /**
     * Creates an `Initializer` instance that constructs the
     * necessary classes for the application.
     *
     * @param args the array of command line arguments
     */
    public static void main(String[] args) {
        System.setProperty("sun.awt.noerasebackground", "true");

        // Check if we're on Linux and running under Wayland
        if (OSUtils.isLinux()) {
            String sessionType = System.getenv("XDG_SESSION_TYPE");
            if (sessionType != null && sessionType.equalsIgnoreCase("wayland")) {
                System.err.println("\n========================================");
                System.err.println("FrostWire requires an X.org session to run.");
                System.err.println("Your system is currently using Wayland.");
                System.err.println("\nPlease switch to X.org or Xwayland and try again.");
                System.err.println("========================================\n");
                System.exit(1);
            }
        }

        String arch = System.getProperty("os.arch").toLowerCase();
        boolean isARM64 = arch.equals("aarch64") || arch.equals("arm64");

        // Check if running in a development environment (Gradle or IntelliJ)
        boolean isDevEnvironment = CommonUtils.isRunningFromGradle() || CommonUtils.isRunningFromIntelliJ();
        boolean hasDebugger = CommonUtils.isStepDebuggerAttached();

        if (isDevEnvironment && !hasDebugger) {
            // DEVELOPMENT ENVIRONMENT: Fail fast if EDT (Event Dispatcher Thread) stalls
            String source = CommonUtils.isRunningFromGradle() ? "Gradle" : "IntelliJ";
            System.out.println("Main: FrostWire is running in DEVELOPMENT environment (" + source + ").");
            com.frostwire.util.StrictEdtMode.install(java.time.Duration.ofMillis(2000));
            System.out.println("Main: Strict EDT mode is ON. (The application will fail fast if the EDT [Event Dispatcher Thread] stalls for more than 2000 ms)");
        } else if (hasDebugger) {
            System.out.println("Main: FrostWire is running in DEVELOPMENT environment (Debugger attached).");
            System.out.println("Main: Strict EDT mode is OFF while stepping through code to avoid false positives.");
        } else {
            System.out.println("FrostWire is running in a PRODUCTION environment.");
        }
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        //load_jlibtorrent();
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

    private static void load_jlibtorrent() {
        try {
            String jlibtorrent_library_path = loadJlibtorrentJNIFromClassloaderResource();
            if (jlibtorrent_library_path != null) {
                System.out.println("jlibtorrent JNI library loaded from classloader resource path: " + jlibtorrent_library_path);
                System.setProperty("jlibtorrent.jni.path", jlibtorrent_library_path);
            } else {
                System.err.println("Failed to load jlibtorrent JNI library from classpath, using fallback paths.");
            }
        } catch (IOException e) {
            System.err.println("Failed to load jlibtorrent JNI library from classpath: " + e.getMessage());
            e.printStackTrace();
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
        final String splashPath = "org/limewire/gui/images/app_splash.png";
        CHOSEN_SPLASH_URL = ClassLoader.getSystemResource(splashPath);
        return CHOSEN_SPLASH_URL;
    }

    private static String getWindowsJLibtorrentPath() {
        return getJLibtorrentPath(".dll");
    }

    private static String getMacOSJLibtorrentPath() {
        return getJLibtorrentPath("." + OSUtils.getMacOSArchitecture() + ".dylib");
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

    // DELETE EVERYTHING BELOW WHEN WE BRING JLIBTORRENT TO 2.0.12.0

    public static boolean isMacOS() {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.US);
        return os.startsWith("mac os");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.US).contains("win");
    }

    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.US).contains("linux");
    }

    private static String loadJlibtorrentJNIFromClassloaderResource() throws java.io.IOException {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.US);
        String arch = System.getProperty("os.arch").toLowerCase(java.util.Locale.US);
        boolean isArm64 = arch.equals("aarch64") || arch.equals("arm64");
        String version = "1.2.19.0";
        String libraryName;
        String pathToLibraryInJar;

        // Determine platform-specific library path
        if (isWindows()) {
            libraryName = "libjlibtorrent-" + version;
            pathToLibraryInJar = "lib/x86_64/" + libraryName + ".dll";
        } else if (isMacOS()) {
            if (isArm64) {
                libraryName = "libjlibtorrent.arm64-" + version;
                pathToLibraryInJar = "lib/arm64/" + libraryName + ".dylib";
            } else {
                libraryName = "libjlibtorrent.x86_64-" + version;
                pathToLibraryInJar = "lib/x86_64/" + libraryName + ".dylib";
            }
        } else {
            throw new java.io.IOException("Unsupported OS: " + os);
        }

        try {
            // Get the native library resource from the classpath
            System.out.println("Loading jlibtorrent from classloader path: " + pathToLibraryInJar);
            InputStream libStream = libtorrent_jni.class.getClassLoader().getResourceAsStream(pathToLibraryInJar);
            if (libStream == null) {
                System.err.println("jlibtorrent: Could not find native library in JAR: " + pathToLibraryInJar);
                throw new FileNotFoundException("Could not find native library in JAR: " + pathToLibraryInJar);
            }

            // Create temp file
            String suffix = pathToLibraryInJar.substring(pathToLibraryInJar.lastIndexOf('.')); // e.g., ".dylib"
            Path tempLib = Files.createTempFile("jni-", suffix);
            tempLib.toFile().deleteOnExit();

            // Extract to temp file
            try (InputStream in = libStream) {
                Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            }

            // Load the library
            String absolutePath = tempLib.toAbsolutePath().toString();
            System.out.println("jlibtorrent: Extracted and loading native library from classpath to: " + absolutePath);
            System.load(absolutePath);
            return absolutePath;
        } catch (IOException e) {
            System.err.println("jlibtorrent: Failed to extract/load native library: " + e.getMessage());
            return null;
        }
    }
}
