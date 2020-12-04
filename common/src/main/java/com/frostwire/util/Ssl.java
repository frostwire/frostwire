/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;

/**
 * Util class to provide SSL/TLS specific features.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Ssl {
    private static final Logger LOG = Logger.getLogger(Ssl.class);
    private static final HostnameVerifier FW_HOSTNAME_VERIFIER = new FWHostnameVerifier();
    private static final X509TrustManager NULL_TRUST_MANAGER = new NullTrustManager();
    private static final SSLSocketFactory NULL_SOCKET_FACTORY = buildNullSSLSocketFactory();

    private Ssl() {
    }

    public static void addValidDomain(String domain) {
        FWHostnameVerifier.addValidDomain(domain);
    }

    /**
     * Returns a hostname verifier instance that accepts only domain names that we trust for our operation
     * <p>
     * The instance returned is always the same and it's thread safe.
     *
     * @return the hostname verifier.
     */
    public static HostnameVerifier fwHostnameVerifier() {
        return FW_HOSTNAME_VERIFIER;
    }

    /**
     * Returns a X509TrustManager instance that simply accepts all certificates
     * as valid.
     * <p>
     * The instance returned is always the same and it's thread safe.
     *
     * @return the hostname verifier.
     */
    public static X509TrustManager nullTrustManager() {
        return NULL_TRUST_MANAGER;
    }

    /**
     * Returns a SSLSocketFactory instance that simply accepts all certificates
     * as valid.
     * <p>
     * The instance returned is always the same and it's thread safe.
     *
     * @return the hostname verifier.
     */
    public static SSLSocketFactory nullSocketFactory() {
        return NULL_SOCKET_FACTORY;
    }

    private static SSLSocketFactory buildNullSSLSocketFactory() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{nullTrustManager()}, new SecureRandom());
            SSLSocketFactory d = sc.getSocketFactory();
            return new WrapSSLSocketFactory(d);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static final class FWHostnameVerifier implements HostnameVerifier {

        private static final String[] validDomains = {
                "api-v2.soundcloud.com",
                "sndcdn.com",
                "api.frostclick.com",
                "archive.org",
                "clients1.google.com",
                "dl.frostwire.com",
                "eztv.re",
                "gtdb.to",
                "idope.se",
                "nyaa.si",
                "pirate-bay.info",
                "pirate-bays.net",
                "piratebay.live",
                "static.frostclick.com",
                "static.frostwire.com",
                "www.frostwire.com",
                "thepiratebay0.org",
                "thepiratebay-unblocked.org",
                "thepiratebay.org",
                "thepiratebay.vip",
                "thepiratebay.zone",
                "torrent-paradise.ml",
                "torrentz2.unblockninja.com",
                "update.frostwire.com",
                "www.1377x.to",
                "www.limetorrents.info",
                "magnetdl.com",
                "www.magnetdl.com",
                "www.pirate-bay.net",
                "www.torlock.com",
                "www.torrentdownloads.me",
                "www.yify-torrent.org",
                "www.youtube.com",
                "yify-torrent.cc",
                "youtu.be",
                "zoink.ch",
                "zooqle.com",
        };

        private static final HashSet<String> validDomainsSet = new HashSet<>();

        static {
            Collections.addAll(validDomainsSet, validDomains);
        }

        public static void addValidDomain(String domain) {
            LOG.info("addValidDomain: " + domain);
            validDomainsSet.add(domain);
            int firstDotIndex = domain.indexOf(".");
            int secondDotIndex = domain.indexOf(".", firstDotIndex);
            if (secondDotIndex != -1) {
                String baseDomain = domain.substring(firstDotIndex + 1);
                LOG.info("addValidDomain: " + baseDomain);
                validDomainsSet.add(baseDomain);
            }
        }

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            if (!validDomainsSet.contains(s)) {

                // check if the s is a subdomain
                for (String baseDomain : validDomainsSet) {
                    if (s.contains(baseDomain)) {
                        validDomainsSet.add(s);
                        return true;
                    }
                }
            }
            return validDomainsSet.contains(s);
        }
    }

    private static final class NullTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    }

    /**
     * This class is a trivial wrapper of a real SSLSocketFactory as a
     * workaround to a bug in android
     */
    private static final class WrapSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory d;

        WrapSSLSocketFactory(SSLSocketFactory d) {
            this.d = d;
        }

        @Override
        public Socket createSocket(String s, int i) throws IOException {
            return d.createSocket(s, i);
        }

        @Override
        public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
            return d.createSocket(s, i, inetAddress, i1);
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
            return d.createSocket(inetAddress, i);
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
            return d.createSocket(inetAddress, i, inetAddress1, i1);
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return d.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return d.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String s, int i, boolean b) throws IOException {
            return d.createSocket(socket, s, i, b);
        }
    }
}
