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

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;

/**
 * This class handles Macintosh specific events. The handled events
 * include the selection of the "About" option in the Mac file menu,
 * the selection of the "Quit" option from the Mac file menu, and the
 * dropping of a file on LimeWire on the Mac, which LimeWire would be
 * expected to handle in some way.
 */
public class MacEventHandler {
    private static final Logger LOG = Logger.getLogger(MacEventHandler.class);
    private static MacEventHandler INSTANCE;

    private MacEventHandler() {
        MacOSHandler.setAboutHandler(args -> handleAbout());
        MacOSHandler.setQuitHandler(args -> handleQuit());
        MacOSHandler.setAppReopenedListener(args -> handleReopen());
        MacOSHandler.setPreferencesHandler(args -> handlePreferences());
        MacOSHandler.setOpenFileHandler(args -> {
            List<File> files = MacOSHandler.getFiles(args[0]);
            if (files != null && files.size() > 0) {
                File file = files.get(0);
                LOG.debug("File: " + file);
                if (file.getName().toLowerCase().endsWith(".torrent")) {
                    GUIMediator.instance().openTorrentFile(file, false);
                }
            }
        });
        MacOSHandler.setOpenURIHandler(args -> {
            String uri = MacOSHandler.getURI(args[0]).toString();
            LOG.debug("URI: " + uri);
            if (uri.startsWith("magnet:?xt=urn:btih")) {
                GUIMediator.instance().openTorrentURI(uri, false);
            }
        });
    }

    public static synchronized MacEventHandler instance() {
        if (INSTANCE == null)
            INSTANCE = new MacEventHandler();
        return INSTANCE;
    }

    /**
     * This responds to the selection of the about option by displaying the
     * about window to the user.  On OSX, this runs in a new ManagedThread to handle
     * the possibility that event processing can become blocked if launched
     * in the calling thread.
     */
    private void handleAbout() {
        GUIMediator.showAboutWindow();
    }

    /**
     * This method responds to a quit event by closing the application in
     * the whichever method the user has configured (closing after completed
     * file transfers by default).  On OSX, this runs in a new ManagedThread to handle
     * the possibility that event processing can become blocked if launched
     * in the calling thread.
     */
    private void handleQuit() {
        GUIMediator.applyWindowSettings();
        GUIMediator.close(false);
    }

    private void handleReopen() {
        GUIMediator.handleReopen();
    }

    private void handlePreferences() {
        GUIMediator.instance().setOptionsVisible(true);
    }

    private static final class MacOSHandler implements InvocationHandler {
        private static final int javaVersion = javaVersion();
        private static final Class<?> applicationClass = applicationClass();
        private static final Object application = applicationObject();
        private final String handlerMethod;
        private final EventHandler handler;

        private MacOSHandler(String handlerMethod, EventHandler handler) {
            this.handlerMethod = handlerMethod;
            this.handler = handler;
        }

        private static int javaVersion() {
            String versionStr = System.getProperty("java.version");
            if (versionStr.startsWith("1.8")) {
                return 8;
            }
            if (versionStr.startsWith("9")) {
                return 9;
            }
            if (versionStr.startsWith("10")) {
                return 10;
            }
            if (versionStr.startsWith("12")) {
                return 12;
            }
            throw new RuntimeException("Java version " + versionStr + " not supported");
        }

        private static Class<?> applicationClass() {
            try {
                if (javaVersion == 8) {
                    return Class.forName("com.apple.eawt.Application");
                }
                if (javaVersion >= 9) {
                    return Class.forName("java.awt.Desktop");
                }
            } catch (Throwable e) {
                LOG.error("Error getting application class", e);
            }
            return null;
        }

        private static Object applicationObject() {
            try {
                Method m = null;
                if (javaVersion == 8) {
                    m = applicationClass.getDeclaredMethod("getApplication");
                }
                if (javaVersion >= 9) {
                    m = applicationClass.getDeclaredMethod("getDesktop");
                }
                return m.invoke(null);
            } catch (Throwable e) {
                LOG.error("Error creating application instance", e);
            }
            return null;
        }

        private static void setEventHandler(String methodName, String handlerName,
                                            String handlerMethod, EventHandler handler) {
            try {
                Class<?> handlerClass = Class.forName(handlerName);
                Method setMethod = null;
                try {
                    setMethod = applicationClass.getDeclaredMethod(methodName,
                            handlerClass);
                } catch (NoSuchMethodException ignore) {
                    // try first interface
                    setMethod = applicationClass.getDeclaredMethod(methodName,
                            handlerClass.getInterfaces()[0]);
                }
                MacOSHandler adapter = new MacOSHandler(handlerMethod, handler);
                Object proxy = Proxy.newProxyInstance(MacOSHandler.class.getClassLoader(),
                        new Class<?>[]{handlerClass}, adapter);
                setMethod.invoke(application, proxy);
            } catch (Throwable e) {
                LOG.error("Error setting application handler", e);
            }
        }

        static void setAboutHandler(EventHandler handler) {
            if (javaVersion == 8) {
                setEventHandler("setAboutHandler", "com.apple.eawt.AboutHandler",
                        "handleAbout", handler);
            }
            if (javaVersion >= 9) {
                setEventHandler("setAboutHandler", "java.awt.desktop.AboutHandler",
                        "handleAbout", handler);
            }
        }

        static void setQuitHandler(EventHandler handler) {
            if (javaVersion == 8) {
                setEventHandler("setQuitHandler", "com.apple.eawt.QuitHandler",
                        "handleQuitRequestWith", handler);
            }
            if (javaVersion >= 9) {
                setEventHandler("setQuitHandler", "java.awt.desktop.QuitHandler",
                        "handleQuitRequestWith", handler);
            }
        }

        static void setAppReopenedListener(EventHandler handler) {
            if (javaVersion == 8) {
                setEventHandler("addAppEventListener", "com.apple.eawt.AppReOpenedListener",
                        "appReOpened", handler);
            }
            if (javaVersion >= 9) {
                setEventHandler("addAppEventListener", "java.awt.desktop.AppReopenedListener",
                        "appReopened", handler);
            }
        }

        static void setPreferencesHandler(EventHandler handler) {
            if (javaVersion == 8) {
                setEventHandler("setPreferencesHandler", "com.apple.eawt.PreferencesHandler",
                        "handlePreferences", handler);
            }
            if (javaVersion >= 9) {
                setEventHandler("setPreferencesHandler", "java.awt.desktop.PreferencesHandler",
                        "handlePreferences", handler);
            }
        }

        static void setOpenFileHandler(EventHandler handler) {
            if (javaVersion == 8) {
                setEventHandler("setOpenFileHandler", "com.apple.eawt.OpenFilesHandler",
                        "openFiles", handler);
            }
            if (javaVersion >= 9) {
                setEventHandler("setOpenFileHandler", "java.awt.desktop.OpenFilesHandler",
                        "openFiles", handler);
            }
        }

        static void setOpenURIHandler(EventHandler handler) {
            if (javaVersion == 8) {
                setEventHandler("setOpenURIHandler", "com.apple.eawt.OpenURIHandler",
                        "openURI", handler);
            }
            if (javaVersion >= 9) {
                setEventHandler("setOpenURIHandler", "java.awt.desktop.OpenURIHandler",
                        "openURI", handler);
            }
        }

        @SuppressWarnings("unchecked")
        static List<File> getFiles(Object event) {
            try {
                Method m = event.getClass().getMethod("getFiles");
                return (List<File>) m.invoke(event);
            } catch (Throwable e) {
                LOG.error("Error invoking getFiles in event: " + event);
            }
            return null;
        }

        static URI getURI(Object event) {
            try {
                Method m = event.getClass().getDeclaredMethod("getURI");
                return (URI) m.invoke(event);
            } catch (Throwable e) {
                LOG.error("Error invoking getFiles in event: " + event);
            }
            return null;
        }

        @Override
        public final Object invoke(Object proxy, Method method, Object[] args) {
            try {
                if (handlerMethod.equals(method.getName()) && args.length > 0) {
                    handler.handle(args);
                }
            } catch (Throwable e) {
                LOG.error("Error invoking handler", e);
            }
            return null;
        }

        interface EventHandler {
            void handle(Object[] args);
        }
    }
}
