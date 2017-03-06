package com.frostwire.android.core.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.frostwire.util.Logger;

import fi.iki.elonen.NanoHTTPD;

public class CastFileServer extends NanoHTTPD {

    private static final int PORT = 8080;
    private static final int TIMEOUT = 5000;

    private static final String ACCEPT_RANGES_HEADER = "Accept-Ranges";
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final String CONTENT_RANGE_HEADER = "Content-Range";
    private static final String E_TAG_HEADER = "ETag";

    public static final Logger LOG = Logger.getLogger(CastFileServer.class);

    private HashMap<String, String> fileLookup = new HashMap<>();
    private HashMap<String, String> reverseFileLookup = new HashMap<>();

    private static CastFileServer instance;

    public static CastFileServer getInstance() {
        if (instance == null) {
            instance = new CastFileServer(PORT);
        }
        return instance;
    }

    private CastFileServer(int port) {
        super(port);
    }

    public void start() {
        try {
            this.start(TIMEOUT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        Response response = verifyHTTPMethod(session.getMethod());
        if (response != null) {
            return response;
        }

        File file = new File(fileLookup.get(uri));
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "File not found");
        }

        String mimeType = getMimeTypeForFile(fileLookup.get(uri));

        return serveFile(session.getHeaders(), file, mimeType);
    }

    private Response verifyHTTPMethod(Method method) {
        if (Method.OPTIONS.equals(method)) {
            Response response = newFixedLengthResponse("");
            response.addHeader("Allow", "GET");
            return response;
        }

        if (!Method.GET.equals(method)) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/html", "Operation not supported");
        }
        return null;
    }

    /**
     * @param path original file path
     * @return file id on server
     */
    public String addFileToCast(String path) {
        if (reverseFileLookup.get(path) != null) {
            return reverseFileLookup.get(path);
        }
        String obfuscatedPath = "/" + UUID.randomUUID().toString();
        fileLookup.put(obfuscatedPath, path);
        reverseFileLookup.put(path, obfuscatedPath);
        return obfuscatedPath;
    }

    /**
     * @param path path to remove from streaming
     */
    public void removeFileFromCast(String path) {
        fileLookup.remove(reverseFileLookup.remove(path));
    }

    private String getIp() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                List<InetAddress> addresses = Collections.list(networkInterface.getInetAddresses());
                for (InetAddress address : addresses) {
                    if (!address.isLoopbackAddress()) {
                        String hostAddress = address.getHostAddress();
                        boolean isIPv4 = hostAddress.indexOf(':') < 0;
                        if (isIPv4) {
                            return hostAddress;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getFullHost() {
        return getIp() + ":" + getListeningPort();
    }


    private Response serveFile(Map<String, String> header,
                               File file, String mime) {
        Response response;
        try {
            String eTag = Integer.toHexString((file.getAbsolutePath()
                    + file.lastModified() + "" + file.length()).hashCode());

            long startFrom = 0;
            long endAt = -1;
            String range = header.get("range");
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length());
                    int minus = range.indexOf('-');
                    try {
                        if (minus > 0) {
                            startFrom = Long.parseLong(range
                                    .substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            long fileLen = file.length();
            if (range != null && startFrom >= 0) {
                if (startFrom >= fileLen) {
                    response = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE,
                            NanoHTTPD.MIME_PLAINTEXT, "");
                    response.addHeader(ACCEPT_RANGES_HEADER, "bytes");
                    response.addHeader(CONTENT_RANGE_HEADER, "bytes 0-0/" + fileLen);
                    response.addHeader(E_TAG_HEADER, eTag);
                } else {
                    if (endAt < 0) {
                        endAt = fileLen - 1;
                    }
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0) {
                        newLen = 0;
                    }

                    final long dataLen = newLen;
                    FileInputStream fis = new FileInputStream(file) {
                        @Override
                        public int available() throws IOException {
                            return (int) dataLen;
                        }
                    };
                    long skipped = fis.skip(startFrom);
                    if (skipped != startFrom) {
                        LOG.warn("Problem skipping, wanted: " + startFrom + " got: " + skipped);
                    }
                    response = newChunkedResponse(Response.Status.PARTIAL_CONTENT, mime,
                            fis);
                    response.addHeader(ACCEPT_RANGES_HEADER, "bytes");
                    response.addHeader(CONTENT_LENGTH_HEADER, "" + dataLen);
                    response.addHeader(CONTENT_RANGE_HEADER, "bytes " + startFrom + "-"
                            + endAt + "/" + fileLen);
                    response.addHeader(E_TAG_HEADER, eTag);
                }
            } else {
                if (eTag.equals(header.get("if-none-match"))) {
                    response = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "");
                    response.addHeader(ACCEPT_RANGES_HEADER, "bytes");
                } else {
                    response = newChunkedResponse(Response.Status.OK, mime,
                            new FileInputStream(file));
                    response.addHeader(ACCEPT_RANGES_HEADER, "bytes");
                    response.addHeader(CONTENT_LENGTH_HEADER, "" + fileLen);
                    response.addHeader(E_TAG_HEADER, eTag);
                }
            }
        } catch (IOException e) {
            LOG.warn("IOE while serving file", e);
            response = newFixedLengthResponse(Response.Status.FORBIDDEN,
                    NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
            response.addHeader(ACCEPT_RANGES_HEADER, "bytes");
        }

        return response;
    }

}
