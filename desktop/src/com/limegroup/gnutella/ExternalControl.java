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

package com.limegroup.gnutella;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.gui.GUIUtils;
import org.limewire.util.OSUtils;
import org.limewire.util.StringUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.*;

public class ExternalControl {
    private static final Logger LOG = Logger.getLogger(ExternalControl.class);
    private static final String NL = "\015\012";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String LOCALHOST_NAME = "localhost";
    private static final int SERVER_PORT = 45099;
    private static ExternalControl INSTANCE;
    private final ActivityCallback activityCallback;
    private volatile String enqueuedRequest = null;
    private ExternalControl(ActivityCallback activityCallback) {
        this.activityCallback = activityCallback;
    }

    public static ExternalControl instance(ActivityCallback activityCallback) {
        if (INSTANCE == null) {
            INSTANCE = new ExternalControl(activityCallback);
        }
        return INSTANCE;
    }

    public void startServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(SERVER_PORT, 50, InetAddress.getByName(LOCALHOST_IP));
                while (true) {
                    final Socket socket = serverSocket.accept();
                    new Thread(() -> {
                        boolean closeSocket = true;
                        try {
                            String address = socket.getInetAddress().getHostAddress();
                            if (address.equals(LOCALHOST_NAME) || address.equals(LOCALHOST_IP)) {
                                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), GUIUtils.DEFAULT_ENCODING));
                                String line = br.readLine();
                                if (line != null) {
                                    if (line.toUpperCase().startsWith("GET ")) {
                                        line = line.substring(4);
                                        int pos = line.lastIndexOf(' ');
                                        line = line.substring(0, pos);
                                        closeSocket = process(line, socket.getOutputStream());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (closeSocket) {
                                try {
                                    socket.close();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @SuppressWarnings("RedundantThrows")
    private boolean process(String get, OutputStream os) throws IOException {
        Map<String, String> original_params = new HashMap<>();
        Map<String, String> lc_params = new HashMap<>();
        List<String> source_params = new ArrayList<>();
        int pos = get.indexOf('?');
        String arg_str;
        if (pos == -1) {
            arg_str = "";
        } else {
            arg_str = get.substring(pos + 1);
            pos = arg_str.lastIndexOf(' ');
            if (pos >= 0) {
                arg_str = arg_str.substring(0, pos).trim();
            }
            StringTokenizer tok = new StringTokenizer(arg_str, "&");
            while (tok.hasMoreTokens()) {
                String arg = tok.nextToken();
                pos = arg.indexOf('=');
                if (pos == -1) {
                    String lhs = arg.trim();
                    original_params.put(lhs, "");
                    lc_params.put(lhs.toLowerCase(GUIUtils.LOCALE_ENGLISH), "");
                } else {
                    try {
                        String lhs = arg.substring(0, pos).trim();
                        String lc_lhs = lhs.toLowerCase(GUIUtils.LOCALE_ENGLISH);
                        String rhs = URLDecoder.decode(arg.substring(pos + 1).trim(), GUIUtils.DEFAULT_ENCODING);
                        original_params.put(lhs, rhs);
                        lc_params.put(lc_lhs, rhs);
                        if (lc_lhs.equals("xsource")) {
                            source_params.add(rhs);
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (get.startsWith("/download")) {
            String info_hash = lc_params.get("info_hash");
            if (info_hash != null) {
                if (activityCallback.isRemoteDownloadsAllowed()) {
                    writeJSReply(os, "checkResult(1);");
                    handleTorrentMagnetRequest("magnet:?xt=urn:btih:" + info_hash + '&' + arg_str);
                } else {
                    writeJSReply(os, "checkResult(0);");
                }
                return true;
            } else {
                //this handles when FrostWire is already open. The second instance forwarded
                //parameters to us via HTTP.
                String url = UrlUtils.decode(lc_params.get("url"));
                if (!StringUtils.isNullOrEmpty(url) && activityCallback.isRemoteDownloadsAllowed()) {
                    if (url.startsWith("magnet:?")) {
                        handleTorrentMagnetRequest(url);
                    } else if (url.startsWith("file://") || url.endsWith(".torrent")) {
                        // local torrent files
                        handleTorrentRequest(url);
                    }
                }
                writeHTTPReply(os);
                return true;
            }
        } else if (get.startsWith("/show")) {
            writeHTTPReply(os);
            restoreApplication();
            return true;
        }
        writeNotFound(os);
        return true;
    }

    private void writeNotFound(OutputStream os) {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        pw.print("HTTP/1.0 404 Not Found" + NL + NL);
        pw.flush();
    }

    private void writeJSReply(OutputStream os, String string) {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        pw.print("HTTP/1.1 200 OK" + NL);
        pw.print("Cache-Control: no-cache" + NL);
        pw.print("Pragma: no-cache" + NL);
        pw.print("Content-Type: text/javascript" + NL);
        pw.print("Content-Length: " + string.length() + NL + NL);
        pw.write(string);
        pw.flush();
    }

    private void writeHTTPReply(OutputStream os) {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        pw.print("HTTP/1.1 200 OK" + NL);
        pw.print("Cache-Control: no-cache" + NL);
        pw.print("Pragma: no-cache" + NL);
        pw.print("Content-Type: text/plain" + NL);
        pw.print("Content-Length: " + "Running".length() + NL + NL);
        pw.write("Running");
        pw.flush();
    }

    public String preprocessArgs(String[] args) {
        LOG.info("enter proprocessArgs");
        StringBuilder arg = new StringBuilder();
        for (String s : args) {
            arg.append(s);
        }
        return arg.toString();
    }

    /**
     * Uses the magnet infrastructure to check if FrostWire is running.
     * If it is, it is restored and this instance exits.
     * Note that the already-running FrostWire is not checked
     * for 'allow multiple instances' -- only the instance that was just
     * started.
     */
    public void checkForActiveFrostWire() {
        if (testForFrostWire(null)) {
            System.exit(0);
        }
    }

    public void checkForActiveFrostWire(String arg) {
        if ((OSUtils.isWindows() || OSUtils.isLinux()) && testForFrostWire(arg)) {
            System.exit(0);
        }
    }

    public void enqueueControlRequest(String arg) {
        enqueuedRequest = arg;
    }

    public void runQueuedControlRequest() {
        if (enqueuedRequest != null) {
            String request = enqueuedRequest;
            enqueuedRequest = null;
            if (isTorrentMagnetRequest(request)) {
                LOG.info("ExternalControl.runQueuedControlRequest() handleTorrentMagnetRequest() - " + request);
                handleTorrentMagnetRequest(request);
            } else if (isTorrentRequest(request)) {
                LOG.info("ExternalControl.runQueuedControlRequest() handleTorrentRequest() - " + request);
                handleTorrentRequest(request);
            } else {
                LOG.info("ExternalControl.runQueuedControlRequest() handleMagnetRequest() - " + request);
                handleMagnetRequest(request);
            }
        }
    }

    private boolean isTorrentMagnetRequest(String request) {
        return request.startsWith("magnet:?xt=urn:btih");
    }

    private void handleTorrentMagnetRequest(String request) {
        ActivityCallback callback = restoreApplication();
        LOG.info("handleTorrentMagnetRequest restored application. About to callback.handleTorrentMagnet()");
        callback.handleTorrentMagnet(request, true);
    }

    /**
     * @return true if this is a torrent request.
     */
    private boolean isTorrentRequest(String arg) {
        if (arg == null)
            return false;
        arg = arg.trim().toLowerCase();
        // magnets pointing to .torrent files are just magnets for now
        return arg.endsWith(".torrent") && !arg.startsWith("magnet:");
    }

    //refactored the download logic into a separate method
    private void handleMagnetRequest(String arg) {
        LOG.info("enter handleMagnetRequest");
        if (isTorrentMagnetRequest(arg)) {
            LOG.info("ExternalControl.handleMagnetRequest(" + arg + ") -> handleTorrentMagnetRequest()");
            handleTorrentMagnetRequest(arg);
            return;
        }
        //ActivityCallback callback = restoreApplication();
        MagnetOptions[] options = MagnetOptions.parseMagnet(arg);
        if (options.length == 0) {
            LOG.warn("Invalid magnet, ignoring: " + arg);
        }
    }

    private ActivityCallback restoreApplication() {
        activityCallback.restoreApplication();
        activityCallback.showDownloads();
        return activityCallback;
    }

    private void handleTorrentRequest(String arg) {
        LOG.info("enter handleTorrentRequest");
        ActivityCallback callback = restoreApplication();
        File torrentFile = new File(arg.trim());
        callback.handleTorrent(torrentFile);
    }

    /**
     * Check if the client is already running, and if so, pop it up.
     * Sends the MAGNET message along the given socket.
     *
     * @return true if a local FrostWire responded with a true.
     */
    private boolean testForFrostWire(String arg) {
        try {
            //LOG.info("testForFrostWire(arg = ["+arg+"])");
            String urlParameter;
            if (arg != null && (arg.startsWith("http://") || arg.startsWith("https://") || arg.startsWith("magnet:?") || arg.endsWith(".torrent"))) {
                urlParameter = "/download?url=" + UrlUtils.encode(arg);
            } else {
                urlParameter = "/show";
            }
            //LOG.info("urlParameter = " + urlParameter);
            final String response = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).get("http://" + LOCALHOST_IP + ":" + SERVER_PORT + urlParameter, 1000);
            if (response != null) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
