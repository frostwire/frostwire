/*
 * Copyright (c) 2012, the Last.fm Java Project and Committers All rights
 * reserved. Redistribution and use of this software in source and binary forms,
 * with or without modification, are permitted provided that the following
 * conditions are met: - Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following disclaimer. -
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. THIS SOFTWARE IS
 * PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.andrew.apollo.lastfm;

import static com.andrew.apollo.lastfm.StringUtilities.encode;
import static com.andrew.apollo.lastfm.StringUtilities.map;

import android.content.Context;
import android.util.Log;

import com.andrew.apollo.lastfm.Result.Status;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * The <code>Caller</code> class handles the low-level communication between the
 * client and last.fm.<br/>
 * Direct usage of this class should be unnecessary since all method calls are
 * available via the methods in the <code>Artist</code>, <code>Album</code>,
 * <code>User</code>, etc. classes. If specialized calls which are not covered
 * by the Java API are necessary this class may be used directly.<br/>
 * Supports the setting of a custom {@link Proxy} and a custom
 * <code>User-Agent</code> HTTP header.
 * 
 * @author Janni Kovacs
 */
public class Caller {

    private final static String TAG = "LastFm.Caller";

    private final static String PARAM_API_KEY = "api_key";

    private final static String DEFAULT_API_ROOT = "http://ws.audioscrobbler.com/2.0/";

    private static Caller mInstance = null;

    private final String apiRootUrl = DEFAULT_API_ROOT;

    private final String userAgent = "Apollo";

    private Result lastResult;

    /**
     * @param context The {@link Context} to use
     */
    private Caller(final Context context) {
    }

    /**
     * @param context The {@link Context} to use
     * @return A new instance of this class
     */
    public final static synchronized Caller getInstance(final Context context) {
        if (mInstance == null) {
            mInstance = new Caller(context.getApplicationContext());
        }
        return mInstance;
    }

    /**
     * @param method
     * @param apiKey
     * @param params
     * @return
     * @throws CallException
     */
    public Result call(final String method, final String apiKey, final String... params) {
        return call(method, apiKey, map(params));
    }

    /**
     * Performs the web-service call. If the <code>session</code> parameter is
     * <code>non-null</code> then an authenticated call is made. If it's
     * <code>null</code> then an unauthenticated call is made.<br/>
     * The <code>apiKey</code> parameter is always required, even when a valid
     * session is passed to this method.
     * 
     * @param method The method to call
     * @param apiKey A Last.fm API key
     * @param params Parameters
     * @param session A Session instance or <code>null</code>
     * @return the result of the operation
     */
    public Result call(final String method, final String apiKey, Map<String, String> params) {
        params = new WeakHashMap<String, String>(params);
        InputStream inputStream = null;

        // no entry in cache, load from web
        if (inputStream == null) {
            // fill parameter map with apiKey and session info
            params.put(PARAM_API_KEY, apiKey);
            try {
                final HttpURLConnection urlConnection = openPostConnection(method, params);
                inputStream = getInputStreamFromConnection(urlConnection);

                if (inputStream == null) {
                    lastResult = Result.createHttpErrorResult(urlConnection.getResponseCode(),
                            urlConnection.getResponseMessage());
                    return lastResult;
                }
            } catch (final IOException ioEx) {
                // We will assume that the server is not ready
                Log.e(TAG, "Failed to download data", ioEx);
                lastResult = Result.createHttpErrorResult(503/*HttpStatus.SC_SERVICE_UNAVAILABLE*/,
                        ioEx.getLocalizedMessage());
                return lastResult;
            }
        }

        try {
            final Result result = createResultFromInputStream(inputStream);
            lastResult = result;
        } catch (final IOException ioEx) {
            Log.e(TAG, "Failed to read document", ioEx);
            lastResult = new Result(ioEx.getLocalizedMessage());
        } catch (final SAXException saxEx) {
            Log.e(TAG, "Failed to parse document", saxEx);
            lastResult = new Result(saxEx.getLocalizedMessage());
        }
        return lastResult;
    }

    /**
     * Creates a new {@link HttpURLConnection}, sets the proxy, if available,
     * and sets the User-Agent property.
     * 
     * @param url URL to connect to
     * @return a new connection.
     * @throws IOException if an I/O exception occurs.
     */
    public HttpURLConnection openConnection(final String url) throws IOException {
        final URL u = new URL(url);
        HttpURLConnection urlConnection;
        urlConnection = (HttpURLConnection)u.openConnection();
        urlConnection.setRequestProperty("User-Agent", userAgent);
        urlConnection.setUseCaches(true);
        return urlConnection;
    }

    /**
     * @param method
     * @param params
     * @return
     * @throws IOException
     */
    private HttpURLConnection openPostConnection(final String method,
            final Map<String, String> params) throws IOException {
        final HttpURLConnection urlConnection = openConnection(apiRootUrl);
        urlConnection.setRequestMethod("POST");
        urlConnection.setDoOutput(true);
        urlConnection.setUseCaches(true);
        final OutputStream outputStream = urlConnection.getOutputStream();
        final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
        final String post = buildPostBody(method, params);
        writer.write(post);
        writer.close();
        return urlConnection;
    }

    /**
     * @param connection
     * @return
     * @throws IOException
     */
    private InputStream getInputStreamFromConnection(final HttpURLConnection connection)
            throws IOException {
        final int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN
                || responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
            return connection.getErrorStream();
        } else if (responseCode == HttpURLConnection.HTTP_OK) {
            return connection.getInputStream();
        }

        return null;
    }

    /**
     * @param inputStream
     * @return
     * @throws SAXException
     * @throws IOException
     */
    private Result createResultFromInputStream(final InputStream inputStream) throws SAXException,
            IOException {
        final Document document = newDocumentBuilder().parse(
                new InputSource(new InputStreamReader(inputStream, "UTF-8")));
        final Element root = document.getDocumentElement(); // lfm element
        final String statusString = root.getAttribute("status");
        final Status status = "ok".equals(statusString) ? Status.OK : Status.FAILED;
        if (status == Status.FAILED) {
            final Element errorElement = (Element)root.getElementsByTagName("error").item(0);
            final int errorCode = Integer.parseInt(errorElement.getAttribute("code"));
            final String message = errorElement.getTextContent();
            return Result.createRestErrorResult(errorCode, message);
        } else {
            return Result.createOkResult(document);
        }
    }

    /**
     * @return
     */
    private DocumentBuilder newDocumentBuilder() {
        try {
            final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            return builderFactory.newDocumentBuilder();
        } catch (final ParserConfigurationException e) {
            // better never happens
            throw new RuntimeException(e);
        }
    }

    /**
     * @param method
     * @param params
     * @param strings
     * @return
     */
    private String buildPostBody(final String method, final Map<String, String> params,
            final String... strings) {
        final StringBuilder builder = new StringBuilder(100);
        builder.append("method=");
        builder.append(method);
        builder.append('&');
        for (final Iterator<Entry<String, String>> it = params.entrySet().iterator(); it.hasNext();) {
            final Entry<String, String> entry = it.next();
            builder.append(entry.getKey());
            builder.append('=');
            builder.append(encode(entry.getValue()));
            if (it.hasNext() || strings.length > 0) {
                builder.append('&');
            }
        }
        int count = 0;
        for (final String string : strings) {
            builder.append(count % 2 == 0 ? string : encode(string));
            count++;
            if (count != strings.length) {
                if (count % 2 == 0) {
                    builder.append('&');
                } else {
                    builder.append('=');
                }
            }
        }
        return builder.toString();
    }
}
