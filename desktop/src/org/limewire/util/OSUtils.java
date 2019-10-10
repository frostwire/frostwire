package org.limewire.util;

import java.util.Locale;

/**
 * Provides methods to get operating system properties, resources and versions,
 * and determine operating system criteria.
 */
public class OSUtils {
    private static boolean _isWindows;
    private static boolean _isWindowsNT;
    private static boolean _isWindowsXP;
    private static boolean _isWindows95;
    private static boolean _isWindows98;
    private static boolean _isWindowsMe;
    private static boolean _isWindowsVista;
    private static boolean _isWindows7;
    /**
     * Variable for whether or not the operating system allows the
     * application to be reduced to the system tray.
     */
    private static boolean _supportsTray;
    /**
     * Variable for whether or not we're on Mac OS X.
     */
    private static boolean _isMacOSX;
    /**
     * Variable for whether or not we're on Linux.
     */
    private static boolean _isLinux;

    /*
      OSX versions
      10.9 - Mavericks
      10.10 - Yosemite
      10.11 - El Capitan
      -- No longer called OSX, now called macOS
      10.12 - Sierra
     */
    /**
     * Variable for whether or not we're on Ubuntu
     */
    private static boolean _isUbuntu;
    /**
     * Variable for whether or not we're on Fedora
     */
    private static boolean _isFedora;
    /**
     * Variable for whether or not we're on Solaris.
     */
    private static boolean _isSolaris;
    /**
     * Variable for whether or not we're on OS/2.
     */
    private static boolean _isOS2;

    static {
        setOperatingSystems();
    }

    /**
     * Sets the operating system variables.
     */
    private static void setOperatingSystems() {
        _isWindows = false;
        _isWindows7 = false;
        _isWindowsVista = false;
        _isWindowsXP = false;
        _isWindowsMe = false;
        _isWindowsNT = false;
        _isWindows98 = false;
        _isWindows95 = false;
        _isLinux = false;
        _isUbuntu = false;
        _isFedora = false;
        _isMacOSX = false;
        _isSolaris = false;
        _isOS2 = false;
        String os = System.getProperty("os.name");
        System.out.println("os.name=\"" + os + "\"");
        os = os.toLowerCase(Locale.US);
        // set the operating system variables
        _isWindows = os.contains("windows");
        _isSolaris = os.contains("solaris");
        _isLinux = os.contains("linux");
        _isOS2 = os.contains("os/2");
        if (_isWindows) {
            _isWindows7 = os.contains("windows 7");
            _isWindowsVista = os.contains("windows vista");
            _isWindowsXP = os.contains("windows xp");
            _isWindowsNT = os.contains("windows nt");
            _isWindowsMe = os.contains("windows me");
            _isWindows98 = os.contains("windows 98");
            _isWindows95 = os.contains("windows 95");
        }
        if (_isLinux) {
            String unameStr = UnameReader.read();
            _isUbuntu = unameStr.contains("buntu") || unameStr.contains("ebian");
            _isFedora = unameStr.contains("edora") || unameStr.contains("ed Hat");
        }
        if (_isWindows || _isLinux)
            _supportsTray = true;
        if (os.startsWith("mac os")) {
            if (os.endsWith("x")) {
                _isMacOSX = true;
            }
        }
    }

    /**
     * Returns the operating system.
     */
    public static String getOS() {
        return System.getProperty("os.name");
    }

    /**
     * Returns the operating system version.
     */
    private static String getOSVersion() {
        return System.getProperty("os.version");
    }

    public static String getFullOS() {
        return getOS() + "-" + getOSVersion() + "-" + getArchitecture();
    }

    /**
     * Returns true if this is Windows NT or Windows 2000 and
     * hence can support a system tray feature.
     */
    public static boolean supportsTray() {
        return _supportsTray;
    }

    /**
     * Returns whether or not the OS is some version of Windows.
     *
     * @return <tt>true</tt> if the application is running on some Windows
     * version, <tt>false</tt> otherwise
     */
    public static boolean isWindows() {
        return _isWindows;
    }

    /**
     * Returns whether or not the OS is WinXP.
     *
     * @return <tt>true</tt> if the application is running on WinXP,
     * <tt>false</tt> otherwise
     */
    public static boolean isWindowsXP() {
        return _isWindowsXP;
    }

    /**
     * @return true if the application is running on Windows NT
     */
    public static boolean isWindowsNT() {
        return _isWindowsNT;
    }

    /**
     * @return true if the application is running on Windows 95
     */
    public static boolean isWindows95() {
        return _isWindows95;
    }

    /**
     * @return true if the application is running on Windows 98
     */
    public static boolean isWindows98() {
        return _isWindows98;
    }

    /**
     * @return true if the application is running on Windows ME
     */
    public static boolean isWindowsMe() {
        return _isWindowsMe;
    }

    /**
     * @return true if the application is running on Windows Vista
     */
    public static boolean isWindowsVista() {
        return _isWindowsVista;
    }

    /**
     * @return true if the application is running on Windows 7
     */
    public static boolean isWindows7() {
        return _isWindows7;
    }

    /**
     * @return true if the application is running on a windows
     * that supports native theme.
     */
    public static boolean isNativeThemeWindows() {
        return isWindowsVista() || isWindowsXP();
    }

    /**
     * Returns whether or not the OS is OS/2.
     *
     * @return <tt>true</tt> if the application is running on OS/2,
     * <tt>false</tt> otherwise
     */
    static boolean isOS2() {
        return _isOS2;
    }

    /**
     * Returns whether or not the OS is Mac OS X.
     *
     * @return <tt>true</tt> if the application is running on Mac OS X,
     * <tt>false</tt> otherwise
     */
    public static boolean isMacOSX() {
        return _isMacOSX;
    }

    /**
     * Returns whether or not the OS is any Mac OS.
     *
     * @return <tt>true</tt> if the application is running on Mac OSX
     * or any previous mac version, <tt>false</tt> otherwise
     */
    public static boolean isAnyMac() {
        return _isMacOSX;
    }

    /**
     * Returns whether or not the OS is Solaris.
     *
     * @return <tt>true</tt> if the application is running on Solaris,
     * <tt>false</tt> otherwise
     */
    static boolean isSolaris() {
        return _isSolaris;
    }

    /**
     * Returns whether or not the OS is Linux.
     *
     * @return <tt>true</tt> if the application is running on Linux,
     * <tt>false</tt> otherwise
     */
    public static boolean isLinux() {
        return _isLinux;
    }

    /**
     * Returns whether or not the Linux distribution is Ubuntu or Debian.
     *
     * @return <tt>true</tt> if the application is running on Ubuntu or Debian distributions,
     * <tt>false</tt> otherwise
     */
    public static boolean isUbuntu() {
        return _isUbuntu;
    }

    /**
     * Returns whether or not the Linux distribution is Fedora or Red Hat
     *
     * @return <tt>true</tt> if the application is running on Fedora or Red Hat distributions,
     * <tt>false</tt> otherwise
     */
    public static boolean isFedora() {
        return _isFedora;
    }

    /**
     * Returns whether or not the OS is some version of
     * Unix, defined here as only Solaris or Linux.
     */
    public static boolean isUnix() {
        return _isLinux || _isSolaris;
    }

    /**
     * Returns whether or not this operating system is considered
     * capable of meeting the requirements of a high load server.
     *
     * @return <tt>true</tt> if this OS meets high load server requirements,
     * <tt>false</tt> otherwise
     */
    private static boolean isHighLoadOS() {
        return !(_isWindows98 || _isWindows95 || _isWindowsMe || _isWindowsNT);
    }

    /**
     * @return true if this is a well-supported version of windows.
     * (not 95, 98, nt or me)
     */
    public static boolean isGoodWindows() {
        return isWindows() && isHighLoadOS();
    }

    public static boolean isModernWindows() {
        return isWindows()
                && !(_isWindows98 || _isWindows95 || _isWindowsMe || _isWindowsNT || _isWindowsXP);
    }

    /**
     * Return whether the current operating system supports moving files
     * to the trash.
     */
    public static boolean supportsTrash() {
        return isWindows() || isMacOSX();
    }

    public static String getArchitecture() {
        return System.getProperty("os.arch");
    }

    public static boolean isMachineX64() {
        String value = System.getProperty("sun.arch.data.model");
        return value != null && value.equals("64");
    }

    public static boolean isMacOSCatalina105OrNewer() {
        // iTunes died with Catalina, now it's called "Apple Music", technically "Music.app"
        String osVersion = OSUtils.getOSVersion();
        String[] os_parts = osVersion.split("\\.");
        int major = Integer.parseInt(os_parts[0]);
        int minor = Integer.parseInt(os_parts[1]);
        return OSUtils.isAnyMac() && major >= 10 && minor >= 15;
    }
}
