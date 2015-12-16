//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.frostwire.search.youtube.jd;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Browser {
    // we need this class in here due to jdownloader stable 0.9 compatibility
    public class BrowserException extends IOException {

        private static final long    serialVersionUID = 1509988898224037320L;
        private HTTPConnectionImpl connection;
        private Exception            e                = null;

        public BrowserException(final String string) {
            super(string);
        }

        public BrowserException(final String message, final HTTPConnectionImpl con) {
            this(message);
            this.connection = con;
        }

        public BrowserException(final String message, final HTTPConnectionImpl con, final Exception e) {
            this(message, con);
            this.e = e;
        }

        /**
         * Returns the connection adapter that caused the browserexception
         * 
         * @return
         */
        public HTTPConnectionImpl getConnection() {
            return this.connection;
        }

        public Exception getException() {
            return this.e;
        }

    }

    private static final HashMap<String, Cookies> COOKIES         = new HashMap<String, Cookies>();
    private static Logger                         LOGGER          = null;

    // added proxy map to find proxy passwords.

    private static int                            TIMEOUT_CONNECT = 30000;

    private static int                            TIMEOUT_READ    = 30000;

    public static String getHost(final String url) {
        return Browser.getHost(url, false);
    }

    public static String getHost(final String url, final boolean includeSubDomains) {
        if (url == null) { return null; }
        /* direct ip */
        String ret = new Regex(url, "(^[a-z0-9]+://|^)(\\d+\\.\\d+\\.\\d+\\.\\d+)(/|$|:)").getMatch(1);
        if (ret != null) { return ret; }
        /* normal url */
        if (includeSubDomains) {
            ret = new Regex(url, "^[a-z0-9]+://(.*?@)?(.*?)(/|$|:)").getMatch(1);
        }
        if (ret == null) {
            ret = new Regex(url, ".*?([^.:/]+\\.[^.:/]+)(/|$|:)").getMatch(0);
        }
        if (ret != null) { return ret.toLowerCase(Locale.ENGLISH); }
        return url;
    }

    /**
     * Returns the host for url. input: http://srv2.bluehost.to/dsdsf ->out bluehost.to
     * 
     * @param url
     * @return
     * @throws MalformedURLException
     */

    public static String getHost(final URL url) {
        return Browser.getHost(url.getHost());
    }

    private static synchronized void waitForPageAccess(final Browser browser, final Request request) throws InterruptedException {
        final String host = Browser.getHost(request.getUrl());
        try {
            Integer localLimit = null;
            Integer globalLimit = null;
            Long localLastRequest = null;
            Long globalLastRequest = null;

            if (localLimit == null && globalLimit == null) { return; }
            if (localLastRequest == null && globalLastRequest == null) { return; }
            if (localLimit != null && localLastRequest == null) { return; }
            if (globalLimit != null && globalLastRequest == null) { return; }

            if (globalLimit == null) {
                globalLimit = 0;
            }
            if (localLimit == null) {
                localLimit = 0;
            }
            if (localLastRequest == null) {
                localLastRequest = System.currentTimeMillis();
            }
            if (globalLastRequest == null) {
                globalLastRequest = System.currentTimeMillis();
            }
            final long dif = Math.max(localLimit - (System.currentTimeMillis() - localLastRequest), globalLimit - (System.currentTimeMillis() - globalLastRequest));

            if (dif > 0) {
                // System.out.println("Sleep " + dif + " before connect to " +
                // request.getUrl().getHost());
                Thread.sleep(dif);
                // waitForPageAccess(request);
            }
        } finally {
        }
    }

    private int[]          allowedResponseCodes = new int[0];

    private static boolean VERBOSE              = false;

    /**
     * Returns a corrected url, where multiple / and ../. are removed
     * 
     * @param url
     * @return
     */
    public static String correctURL(String url) {
        if (url == null) { return url; }
        /* check if we need to correct url */
        int begin = url.indexOf("://");
        if (begin > 0 && url.indexOf("/", begin + 3) < 0) {
            /* check for missing first / in url */
            url = url + "/";
        }
        if (begin > 0 && !url.substring(begin + 3).contains("//") && !url.contains("./")) { return url; }
        String ret = url;
        String end = null;
        String tmp = null;
        boolean endisslash = false;
        if (url.startsWith("http://")) {
            begin = 8;
        } else if (url.startsWith("https://")) {
            begin = 9;
        } else {
            begin = 0;
        }
        final int first = url.indexOf("/", begin);
        if (first < 0) { return ret; }
        ret = url.substring(0, first);
        final int endp = url.indexOf("?", first);
        if (endp > 0) {
            end = url.substring(endp);
            tmp = url.substring(first, endp);
        } else {
            tmp = url.substring(first);
        }
        /* is the end of url a / */
        endisslash = tmp.endsWith("/");

        /* filter multiple / */
        /*
         * NOTE: http://webmasters.stackexchange.com/questions/8354/what-does-the-double-slash-mean-in-urls
         * 
         * http://svn.jdownloader.org/issues/5610
         */
        tmp = tmp.replaceAll("/{3,}", "/");

        /* filter .. and . */
        final String parts[] = tmp.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase(".")) {
                parts[i] = "";
            } else if (parts[i].equalsIgnoreCase("..")) {
                if (i > 0) {
                    int j = i - 1;
                    while (true && j > 0) {
                        if (parts[j].length() > 0) {
                            parts[j] = "";
                            break;
                        }
                        j--;
                    }
                }
                parts[i] = "";
            } else if (i > 0 && parts[i].length() == 0) {
                parts[i] = "/";
            }
        }
        tmp = "";
        for (final String part : parts) {
            if (part.length() > 0) {
                if ("/".equals(part)) {
                    tmp = tmp + "/";
                } else {
                    tmp = tmp + "/" + part;
                }
            }
        }
        if (endisslash) {
            tmp = tmp + "/";
        }
        return ret + tmp + (end != null ? end : "");
    }

    private String                   acceptLanguage      = "de, en-gb;q=0.9, en;q=0.8";

    /*
     * -1 means use default Timeouts
     * 
     * 0 means infinite (DO NOT USE if not needed)
     */
    private int                      connectTimeout      = -1;

    private HashMap<String, Cookies> cookies             = new HashMap<String, Cookies>();
    private boolean                  cookiesExclusive    = true;
    private String                   currentURL          = null;
    private String                   customCharset       = null;
    private boolean                  debug               = false;
    private boolean                  doRedirects         = false;
    private RequestHeader            headers;
    private int                      limit               = 1 * 1024 * 1024;
    private Logger                   logger              = null;
    private int                      readTimeout         = -1;

    private int                      redirectLoopCounter = 0;

    private Request                  request;

    private boolean                  verbose             = false;

    public Browser() {
    }

    /**
     * Assures that the browser does not download any binary files in textmode
     * 
     * @param request
     * @throws BrowserException
     */
    private void checkContentLengthLimit(final Request request) throws BrowserException {
        long length = -1;
        if (request == null || request.getHttpConnection() == null || (length = request.getHttpConnection().getLongContentLength()) < 0) {
            return;
        } else if (length > this.limit) {
            final Logger llogger = this.getLogger();
            if (llogger != null) {
                llogger.severe(request.printHeaders());
            }
            throw new BrowserException("Content-length too big", request.getHttpConnection());
        }
    }

    /**
     * Clears all cookies for the given url. URL has to be a valid url if url==null,all cookies were cleared
     * 
     * @param url
     */
    public void clearCookies(final String url) {
        if (url == null) {
            this.cookies.clear();
        }
        final String host = Browser.getHost(url);
        final Iterator<String> it = this.getCookies().keySet().iterator();
        String check = null;
        while (it.hasNext()) {
            check = it.next();
            if (check.contains(host)) {
                this.cookies.get(check).clear();
                break;
            }
        }
    }

    /**
     * Connects a request. and sets the requests as the browsers latest request
     * 
     * @param request
     * @throws IOException
     */
    public void connect(final Request request) throws IOException {
        // sets request BEVOR connection. this enhables to find the request in
        // the protocol handlers
        this.request = request;
        try {
            Browser.waitForPageAccess(this, request);
        } catch (final InterruptedException e) {
            throw new IOException("requestIntervalTime Exception");
        }
        try {
            request.connect();
        } finally {
            if (this.isDebug()) {
                final Logger llogger = this.getLogger();
                if (llogger != null) {
                    try {
                        llogger.finest("\r\n" + request.printHeaders());
                    } catch (final Throwable e) {
                        e.printStackTrace();
                        //LogSource.exception(llogger, e);
                    }
                }
            }
        }
    }

    public boolean containsHTML(final String regex) {
        return new Regex(this, regex).matches();
    }

    /**
     * Creates a new GET request.
     * 
     * @param string
     *            a string including an url
     * 
     * @return the created GET request
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public Request createGetRequest(final String string) throws IOException {
        return this.createGetRequest(string, null);
    }

    /**
     * Creates a new GET request.
     * 
     * @param string
     *            a string including an url
     * @param oldRequest
     *            the old request for forwarding cookies to the new request. Can be null, to ignore old cookies.
     * 
     * @return the created GET request
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public Request createGetRequest(String string, final Request oldRequest) throws IOException {
        string = this.getURL(string);
        boolean sendref = true;
        if (this.currentURL == null) {
            sendref = false;
            this.currentURL = string;
        }

        final GetRequest request = new GetRequest(string);
        request.setCustomCharset(this.customCharset);

        // if old request is set, use it's cookies for the new request
        if (oldRequest != null && oldRequest.hasCookies()) {
            request.setCookies(oldRequest.getCookies());
        }

        // doAuth(request);
        /* set Timeouts */
        request.setConnectTimeout(this.getConnectTimeout());
        request.setReadTimeout(this.getReadTimeout());

        request.getHeaders().put("Accept-Language", this.acceptLanguage);
        // request.setFollowRedirects(doRedirects);
        this.forwardCookies(request);
        if (sendref) {
            request.getHeaders().put("Referer", this.currentURL.toString());
        }
        if (this.headers != null) {
            this.mergeHeaders(request);
        }

        // if (this.doRedirects && request.getLocation() != null) {
        // this.openGetConnection(null);
        // } else {
        //
        // currentURL = new URL(string);
        // }
        // return this.request.getHttpConnection();
        return request;
    }

    public void disconnect() {
        try {
            this.getRequest().getHttpConnection().disconnect();
        } catch (final Throwable e) {
        }
    }

    public void forwardCookies(final Request request) {
        if (request == null) { return; }
        final String host = Browser.getHost(request.getUrl());
        final Cookies cookies = this.getCookies().get(host);
        if (cookies == null) { return; }

        for (final Cookie cookie : cookies.getCookies()) {
            // Pfade sollten verarbeitet werden...TODO
            if (cookie.isExpired()) {
                continue;
            }
            request.getCookies().add(cookie);
        }
    }

    private String getBase(final String string) {
        if (string == null) { return ""; }

        final String base = this.getRegex("<base\\s*href=\"(.*?)\"").getMatch(0);
        if (base != null) { return base; }

        final URL url = this.request.getHttpConnection().getURL();
        final String host = url.getHost();
        String portUse = "";
        if (url.getDefaultPort() > 0 && url.getPort() > 0 && url.getDefaultPort() != url.getPort()) {
            portUse = ":" + url.getPort();
        }
        String proto = "http://";
        if (url.toString().startsWith("https")) {
            proto = "https://";
        }
        String path = url.getPath();
        int id;
        if ((id = path.lastIndexOf('/')) >= 0) {
            path = path.substring(0, id);
        }
        return proto + host + portUse + path + "/";
    }

    /**
     * returns current ConnectTimeout
     * 
     * @return
     */
    public int getConnectTimeout() {
        return this.connectTimeout < 0 ? Browser.TIMEOUT_CONNECT : this.connectTimeout;
    }

    private HashMap<String, Cookies> getCookies() {
        return this.cookiesExclusive ? this.cookies : Browser.COOKIES;
    }

    public RequestHeader getHeaders() {
        if (this.headers == null) {
            this.headers = new RequestHeader();
        }
        return this.headers;
    }

    public String getHost() {
        return this.request == null ? null : Browser.getHost(this.request.getUrl(), false);
    }

    public Logger getLogger() {
        final Logger llogger = this.logger;
        if (llogger != null) { return llogger; }
        return Browser.LOGGER;
    }

    public String getMatch(final String string) {
        return this.getRegex(string).getMatch(0);
    }

    public String getPage(final String string) throws IOException {
        this.openRequestConnection(this.createGetRequest(string));
        return this.loadConnection(null).getHtmlCode();
    }

    /**
     * returns current ReadTimeout
     * 
     * @return
     */
    public int getReadTimeout() {
        return this.readTimeout < 0 ? Browser.TIMEOUT_READ : this.readTimeout;
    }

    /**
     * If automatic redirectfollowing is disabled, you can get the redirect url if there is any.
     * 
     * @return
     */
    public String getRedirectLocation() {
        if (this.request == null) { return null; }
        return this.request.getLocation();
    }

    public Regex getRegex(final Pattern compile) {
        return new Regex(this, compile);
    }

    public Regex getRegex(final String string) {
        return new Regex(this, string);
    }

    /**
     * Gets the latest request
     * 
     * @return
     */
    public Request getRequest() {
        return this.request;
    }

    public String getURL() {
        return this.request == null ? null : this.request.getUrl().toString();
    }

    /**
     * TRies to get a fuill url out of string
     * 
     * @throws BrowserException
     */
    public String getURL(String string) throws BrowserException {
        if (string == null) {
            string = this.getRedirectLocation();
        }
        if (string == null) { throw new BrowserException("Null URL"); }
        try {
            new URL(string);
        } catch (final Exception e) {
            if (this.request == null || this.request.getHttpConnection() == null) { return string; }
            final String base = this.getBase(string);
            if (string.startsWith("/") || string.startsWith("\\")) {
                try {
                    final URL bUrl = new URL(base);
                    String proto = "http://";
                    if (base.startsWith("https")) {
                        proto = "https://";
                    }
                    String portUse = "";
                    if (bUrl.getDefaultPort() > 0 && bUrl.getPort() > 0 && bUrl.getDefaultPort() != bUrl.getPort()) {
                        portUse = ":" + bUrl.getPort();
                    }
                    string = proto + new URL(base).getHost() + portUse + string;
                } catch (final MalformedURLException e1) {
                    e1.printStackTrace();
                }
            } else {
                string = base + string;
            }
        }
        return Browser.correctURL(Encoding.urlEncode_light(string));
    }

    public boolean isDebug() {
        return this.debug || this.isVerbose();
    }

    public boolean isVerbose() {
        return Browser.VERBOSE || this.verbose;
    }

    /**
     * Reads the content behind a con and returns them. Note: if con==null, the current request is read. This is usefull for redirects. Note
     * #2: if a connection is loaded, data is not stored in the browser instance.
     * 
     * @param con
     * @return
     * @throws IOException
     */
    public Request loadConnection(HTTPConnectionImpl con) throws IOException {

        Request requ;
        if (con == null) {
            requ = this.request;
        } else {
            requ = new Request(con) {
                {
                    this.requested = true;
                }

                @Override
                public long postRequest() throws IOException {
                    return 0;
                }

                @Override
                public void preRequest() throws IOException {
                }
            };
        }
        try {
            this.checkContentLengthLimit(requ);
            con = requ.getHttpConnection();
            /* we update allowedResponseCodes here */
            con.setAllowedResponseCodes(this.allowedResponseCodes);
            requ.read();
        } catch (final BrowserException e) {
            throw e;
        } catch (final IOException e) {
            throw new BrowserException(e.getMessage(), con, e);
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
        if (this.isVerbose()) {
            final Logger llogger = this.getLogger();
            if (llogger != null) {
                llogger.finest("\r\n" + requ + "\r\n");
            }
        }
        return requ;
    }

    private void mergeHeaders(final Request request) {
        if (this.headers.isDominant()) {
            request.getHeaders().clear();
        }
        final int size = this.headers.size();
        String value;
        for (int i = 0; i < size; i++) {
            value = this.headers.getValue(i);
            if (value == null) {
                request.getHeaders().remove(this.headers.getKey(i));
            } else {
                request.getHeaders().put(this.headers.getKey(i), value);
            }
        }
    }

    /**
     * Opens a new get connection
     * 
     * @param string
     * @return
     * @throws IOException
     */
    public HTTPConnectionImpl openGetConnection(final String string) throws IOException {
        return this.openRequestConnection(this.createGetRequest(string));

    }

    /**
     * Opens a connection based on the requets object
     */
    public HTTPConnectionImpl openRequestConnection(final Request request) throws IOException {
        this.connect(request);
        this.updateCookies(request);
        this.request = request;
        if (this.doRedirects && request.getLocation() != null) {
            if (request.getLocation().toLowerCase().startsWith("ftp://")) { throw new BrowserException("Cannot redirect to FTP"); }
            final String org = request.getUrl();
            final String red = request.getLocation();
            if (org.equalsIgnoreCase(red) && this.redirectLoopCounter >= 20) {
                final Logger llogger = this.getLogger();
                if (llogger != null) {
                    llogger.severe("20 Redirects!!!");
                }
            } else if (!org.equalsIgnoreCase(red) || this.redirectLoopCounter < 20) {
                if (org.equalsIgnoreCase(red)) {
                    this.redirectLoopCounter++;
                } else {
                    this.redirectLoopCounter = 0;
                }
                /* prevent buggy redirect loops */
                /* source==dest */
                try {
                    /* close old connection, because we follow redirect */
                    request.httpConnection.disconnect();
                } catch (final Throwable e) {
                }
                this.openGetConnection(null);
            }
        } else {
            this.currentURL = request.getUrl();
        }
        return this.request.getHttpConnection();
    }

    public void setCookie(final String url, final String key, final String value) {
        final String host = Browser.getHost(url);
        Cookies cookies;
        if (!this.getCookies().containsKey(host) || (cookies = this.getCookies().get(host)) == null) {
            cookies = new Cookies();
            this.getCookies().put(host, cookies);
        }
        cookies.add(new Cookie(host, key, value));
    }

    public void setDebug(final boolean debug) {
        this.debug = debug;
    }

    public void setFollowRedirects(final boolean b) {
        this.doRedirects = b;
    }

    public void setHeaders(final RequestHeader h) {
        this.headers = h;
    }

    public void setLogger(final Logger logger) {
        this.logger = logger;
    }

    public void setRequest(final Request request) {
        if (request == null) { return; }
        this.updateCookies(request);
        this.request = request;
        this.currentURL = request.getUrl();
    }

    @Override
    public String toString() {
        if (this.request == null) { return "Browser. no request yet"; }
        return this.request.getHTMLSource();
    }

    public void updateCookies(final Request request) {
        if (request == null) { return; }
        final String host = Browser.getHost(request.getUrl());
        Cookies cookies = this.getCookies().get(host);
        if (cookies == null) {
            cookies = new Cookies();
            this.getCookies().put(host, cookies);
        }
        cookies.add(request.getCookies());
    }
}
