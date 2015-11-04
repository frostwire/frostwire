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

//import java.awt.Image;
//import java.awt.image.BufferedImage;
//import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

//import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import com.frostwire.search.youtube.jd.ReusableByteArrayOutputStreamPool.ReusableByteArrayOutputStream;

public abstract class Request {
    // public static int MAX_REDIRECTS = 30;

    public static String getCookieString(final Cookies cookies) {
        if (cookies == null || cookies.isEmpty()) { return null; }

        final StringBuilder buffer = new StringBuilder();
        boolean first = true;
        final LinkedList<Cookie> cookies2 = new LinkedList<Cookie>(cookies.getCookies());
        for (final Cookie cookie : cookies2) {
            // Pfade sollten verarbeitet werden...TODO
            if (cookie.isExpired()) {
                continue;
            }

            if (first) {
                first = false;
            } else {
                buffer.append("; ");
            }
            buffer.append(cookie.getKey());
            buffer.append("=");
            buffer.append(cookie.getValue());
        }
        return buffer.toString();
    }

    /**
     * Gibt eine Hashmap mit allen key:value pairs im query zurück
     * 
     * @param query
     *            kann ein reines query ein (&key=value) oder eine url mit query
     * @return
     * @throws MalformedURLException
     */

    public static LinkedHashMap<String, String> parseQuery(String query) throws MalformedURLException {
        if (query == null) { return null; }
        final LinkedHashMap<String, String> ret = new LinkedHashMap<String, String>();
        if (query.toLowerCase().trim().startsWith("http")) {
            query = new URL(query).getQuery();
        }

        if (query == null) { return ret; }
        final String[][] split = new Regex(query.trim(), "&?(.*?)=(.*?)($|&(?=.*?=.+))").getMatches();
        if (split != null) {
            final int splitLength = split.length;
            for (int i = 0; i < splitLength; i++) {
                ret.put(split[i][0], split[i][1]);
            }
        }
        return ret;
    }

    public static byte[] read(final HTTPConnectionImpl con) throws IOException {
        final InputStream is = con.getInputStream();
        byte[] ret = null;
        if (is == null) {
            // TODO: check if we have t close con here
            return null;
        }
        ReusableByteArrayOutputStream tmpOut;
        ReusableByteArrayOutputStream tmpOut2 = ReusableByteArrayOutputStreamPool.getReusableByteArrayOutputStream(1048);
        final long contentLength = con.getLongContentLength();
        if (contentLength != -1) {
            final int length = contentLength > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) contentLength;
            tmpOut = ReusableByteArrayOutputStreamPool.getReusableByteArrayOutputStream(length);
        } else {
            tmpOut = ReusableByteArrayOutputStreamPool.getReusableByteArrayOutputStream(16384);
        }
        boolean okay = false;
        /* added "Corrupt GZIP trailer" for CamWinsCom */
        try {
            int len;
            while ((len = is.read(tmpOut2.getInternalBuffer())) != -1) {
                if (len > 0) {
                    tmpOut.write(tmpOut2.getInternalBuffer(), 0, len);
                }
            }
            okay = true;
        } catch (final EOFException e) {
            e.printStackTrace();
            okay = true;
        } catch (final IOException e) {
            if (e.toString().contains("end of ZLIB") || e.toString().contains("Premature") || e.toString().contains("Corrupt GZIP trailer")) {
                System.out.println("Try workaround for " + e);
                okay = true;
            } else {
                throw e;
            }
        } finally {
            try {
                is.close();
            } catch (final Exception e) {
            }
            try {
                /* disconnect connection */
                con.disconnect();
            } catch (final Exception e) {
            }
            ReusableByteArrayOutputStreamPool.reuseReusableByteArrayOutputStream(tmpOut2);
            if (okay) {
                ret = tmpOut.toByteArray();
            }
            ReusableByteArrayOutputStreamPool.reuseReusableByteArrayOutputStream(tmpOut);
            tmpOut = null;
            tmpOut2 = null;
        }
        return ret;
    }

    /*
     * default timeouts, because 0 is infinite and BAD, if we need 0 then we have to set it manually
     */
    private int                    connectTimeout = 30000;

    private int                    readTimeout    = 60000;
    private Cookies                cookies        = null;
    private RequestHeader          headers;

    private String                 htmlCode;

    protected HTTPConnectionImpl httpConnection;
    private long                   readTime       = -1;

    protected boolean              requested      = false;
    private String                 orgURL;
    private String                 customCharset  = null;
    private byte[]                 byteArray      = null;

    //private BufferedImage          image;

    private boolean                contentDecoded = true;

    public Request(final String url) throws MalformedURLException {
        this.orgURL = Browser.correctURL(url);
        this.initDefaultHeader();
    }

    public Request(final HTTPConnectionImpl con) {
        this.httpConnection = con;
        this.collectCookiesFromConnection();
    }

    private void collectCookiesFromConnection() {
        final List<String> cookieHeaders = this.httpConnection.getHeaderFields("Set-Cookie");
        if (cookieHeaders == null || cookieHeaders.size() == 0) { return; }
        final String date = this.httpConnection.getHeaderField("Date");
        final String host = Browser.getHost(this.httpConnection.getURL());
        for (int i = 0; i < cookieHeaders.size(); i++) {
            final String header = cookieHeaders.get(i);
            this.getCookies().add(Cookies.parseCookies(header, host, date));
        }
    }

    /**
     * DO NEVER call this method directly... use browser.connect
     */
    protected Request connect() throws IOException {
        try {
            this.openConnection();
            this.postRequest();
            /*
             * we connect to inputstream to make sure the response headers are getting parsed first
             */
            this.httpConnection.finalizeConnect();
            try {
                this.collectCookiesFromConnection();
            } catch (final NullPointerException e) {
                throw new IOException("Malformed url?" + e.toString());
            }
        } finally {
            this.requested = true;
        }
        return this;
    }

    public void disconnect() {
        try {
            this.httpConnection.disconnect();
        } catch (final Throwable e) {
        }
    }

    public String getCharsetFromMetaTags() {
        String parseFrom = null;
        if (this.htmlCode == null && this.byteArray != null) {
            parseFrom = new String(this.byteArray);
        } else if (this.htmlCode != null) {
            parseFrom = this.htmlCode;
        }
        if (parseFrom == null) { return null; }
        String charSetMetaTag = new Regex(parseFrom, "http-equiv=\"Content-Type\"[^<>]+content=\"[^\"]+charset=(.*?)\"").getMatch(0);
        if (charSetMetaTag == null) {
            charSetMetaTag = new Regex(parseFrom, "meta charset=\"(.*?)\"").getMatch(0);
        }
        return charSetMetaTag;
    }

    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    public long getContentLength() {
        return this.httpConnection == null ? -1 : this.httpConnection.getLongContentLength();
    }

    public Cookies getCookies() {
        if (this.cookies == null) {
            this.cookies = new Cookies();
        }
        return this.cookies;
    }

    public String getCookieString() {
        return Request.getCookieString(this.cookies);
    }

    public RequestHeader getHeaders() {
        return this.headers;
    }

    public String getHtmlCode() throws CharacterCodingException {
        final String ct = this.httpConnection.getContentType();
        /* check for image content type */
        if (ct != null && Pattern.compile("images?/\\w*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(ct).matches()) { throw new IllegalStateException("Content-Type: " + ct); }
        if (this.htmlCode == null && this.byteArray != null) {
            /* use custom charset or charset from httpconnection */
            String useCS = this.customCharset == null ? this.httpConnection.getCharset() : this.customCharset;
            if (useCS == null) {
                useCS = this.getCharsetFromMetaTags();
            }
            try {
                try {
                    try {
                        if (useCS != null) {
                            /* try to use wanted charset */
                            this.htmlCode = new String(this.byteArray, useCS.toUpperCase());
                            return this.htmlCode;
                        }
                    } catch (final Exception e) {
                    }
                    this.htmlCode = new String(this.byteArray, "ISO-8859-1");
                    return this.htmlCode;
                } catch (final Exception e) {
                    System.out.println("could neither charset: " + useCS + " nor default charset");
                    /* fallback to default charset in error case */
                    this.htmlCode = new String(this.byteArray);
                    return this.htmlCode;
                }
            } catch (final Exception e) {
                /* in case of error we do not reset byteArray */
            }
        }
        return this.htmlCode;
    }

    protected String getHTMLSource() {
        if (!this.requested) { return "Request not sent yet"; }
        try {
            this.getHtmlCode();
            if (this.htmlCode == null || this.htmlCode.length() == 0) {
                if (this.getLocation() != null) { return "Not HTML Code. Redirect to: " + this.getLocation(); }
                return "No htmlCode read";
            }
        } catch (final Exception e) {
            return "NOTEXT: " + e.getMessage();
        }
        return this.htmlCode;
    }

    public HTTPConnectionImpl getHttpConnection() {
        return this.httpConnection;
    }

    public String getLocation() {
        if (this.httpConnection == null) { return null; }
        String red = this.httpConnection.getHeaderField("Location");
        if (StringUtils.isEmpty(StringUtils.trim(red))) {
            /* check if we have an old-school refresh header */
            red = this.httpConnection.getHeaderField("refresh");
            if (red != null) {
                // we need to filter the time count from the url
                red = new Regex(red, "url=(.+);?").getMatch(0);
            }
            if (StringUtils.isEmpty(StringUtils.trim(red))) { return null; }
        }
        final String encoding = this.httpConnection.getHeaderField("Content-Type");
        if (encoding != null && encoding.contains("UTF-8")) {
            red = Encoding.UTF8Decode(red, "ISO-8859-1");
        }
        try {
            new URL(red);
        } catch (final Exception e) {
            String path = this.getHttpConnection().getURL().getFile();
            if (!path.endsWith("/")) {
                /*
                 * path does not end with / we have to find latest valid path
                 * 
                 * \/test.rar should result in empty path
                 * 
                 * \/test/test.rar should result in \/test/
                 */
                final String validPath = new Regex(path, "(/.*?/.*?)(\\?|$)").getMatch(0);
                if (validPath != null && validPath.length() > 0) {
                    path = validPath;
                } else {
                    path = "";
                }
            }
            final int port = this.getHttpConnection().getURL().getPort();
            final int defaultport = this.getHttpConnection().getURL().getDefaultPort();
            String proto = "http://";
            if (this.getHttpConnection().getURL().toString().startsWith("https")) {
                proto = "https://";
            }
            String addPort = "";
            if (defaultport > 0 && port > 0 && defaultport != port) {
                addPort = ":" + port;
            }
            red = proto + this.getHttpConnection().getURL().getHost() + addPort + (red.charAt(0) == '/' ? red : path + "/" + red);
        }
        return Browser.correctURL(Encoding.urlEncode_light(red));

    }

    /**
     * tries to generate an image out of the loaded bytes
     * 
     * @return
     */
//    public Image getResponseImage() {
//        final String ct = this.httpConnection.getContentType();
//        /* check for image content */
//        if (ct != null && !Pattern.compile("images?/\\w*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(ct).matches()) { throw new IllegalStateException("Content-Type: " + ct); }
//        // TODO..this is just quick and dirty.. may result in memory leaks
//        if (this.image == null && this.byteArray != null) {
//            final InputStream fake = new ByteArrayInputStream(this.byteArray);
//            try {
//                this.image = ImageIO.read(fake);
//            } catch (final Exception e) {
//                e.printStackTrace();
//            }
//        }
//        return this.image;
//    }

    /**
     * Will replace #getHtmlCode() with next release
     */
    public String getResponseText() throws CharacterCodingException {
        return this.getHtmlCode();
    }

    public String getUrl() {
        return this.orgURL;
    }

    protected boolean hasCookies() {
        return this.cookies != null && !this.cookies.isEmpty();
    }

    protected void initDefaultHeader() {
        this.headers = new RequestHeader();
        this.headers.put("User-Agent", "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.0.10) Gecko/2009042523 Ubuntu/9.04 (jaunty) Firefox/3.0.10");
        this.headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        this.headers.put("Accept-Language", "de, en-gb;q=0.9, en;q=0.8");

        this.headers.put("Accept-Encoding", "gzip");
        this.headers.put("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.3");

        this.headers.put("Cache-Control", "no-cache");
        this.headers.put("Pragma", "no-cache");
        this.headers.put("Connection", "close");
    }

    public boolean isContentDecoded() {
        return this.httpConnection == null ? this.contentDecoded : this.httpConnection.isContentDecoded();
    }

    public boolean isRequested() {
        return this.requested;
    }

    private void openConnection() throws IOException {
        this.httpConnection = new HTTPConnectionImpl(new URL(this.orgURL));
        this.httpConnection.setRequest(this);
        this.httpConnection.setReadTimeout(this.readTimeout);
        this.httpConnection.setConnectTimeout(this.connectTimeout);
        this.httpConnection.setContentDecoded(this.contentDecoded);

        if (this.headers != null) {
            final int headersSize = this.headers.size();
            for (int i = 0; i < headersSize; i++) {
                this.httpConnection.setRequestProperty(this.headers.getKey(i), this.headers.getValue(i));
            }
        }
        this.preRequest();
        if (this.hasCookies()) {
            final String cookieString = this.getCookieString();
            if (cookieString != null) {
                this.httpConnection.setRequestProperty("Cookie", cookieString);
            }
        }
    }

    abstract public long postRequest() throws IOException;

    abstract public void preRequest() throws IOException;

    public String printHeaders() {
        return this.httpConnection.toString();
    }

    public Request read() throws IOException {
        final long tima = System.currentTimeMillis();
        this.httpConnection.setCharset(this.customCharset);
        this.byteArray = Request.read(this.httpConnection);
        this.readTime = System.currentTimeMillis() - tima;
        return this;
    }

    public void setConnectTimeout(final int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setContentDecoded(final boolean c) {
        this.contentDecoded = c;
    }

    public void setCookies(final Cookies cookies) {
        this.cookies = cookies;
    }

    public void setCustomCharset(final String charset) {
        this.customCharset = charset;
    }

    /**
     * DO NOT USE in 09581 Stable
     */
    public void setHeaders(final RequestHeader headers) {
        this.headers = headers;
    }

    public void setReadTimeout(final int readTimeout) {
        this.readTimeout = readTimeout;
        final HTTPConnectionImpl con = this.httpConnection;
        if (con != null) {
            con.setReadTimeout(readTimeout);
        }
    }

    // @Override
    @Override
    public String toString() {
        if (!this.requested) { return "Request not sent yet"; }
        final StringBuilder sb = new StringBuilder();
        try {
            sb.append(this.httpConnection.toString());
            sb.append("\r\n");
            this.getHtmlCode();
            sb.append(this.getHTMLSource());
        } catch (final Exception e) {
            return "NOTEXT: " + e.getMessage();
        }
        return sb.toString();
    }

}
