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

package com.frostwire.util;

import javax.net.ssl.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
                "api-v2.sndcdn.com",
                "api-v2.soundcloud.com",
                "api.frostclick.com",
                "archive.org",
                "btdig.com",
                "cdninstagram.com",
                "clients1.google.com",
                "cloudflaressl.com",
                "dl.frostwire.com",
                "edgestatic.com",
                "fbcdn.net",
                "frostclick.com",
                "frostwire.com",
                "github.com",
                "github.io",
                "githubusercontent.com",
                "google.com",
                "googleapis.com",
                "gstatic.com",
                "gtso.cc",
                "idope.hair",
                "igcdn.com",
                "igsonar.com",
                "instagram.com",
                "i.ytimg.com",
                "knaben.org",
                "magnetdl.homes",
                "nyaa.si",
                "pirate-bay.info",
                "piratebay.live",
                "pirateproxylive.org",
                "scontent.cdninstagram.com",
                "sndcdn.com",
                "sndcdn.com",
                "sni.cloudflaressl.com",
                "soundcloud.com",
                "static.frostclick.com",
                "static.frostwire.com",
                "thehiddenbay.com",
                "thepiratebay-unblocked.org",
                "thepiratebay.party",
                "thepiratebay.zone",
                "thepiratebay0.org",
                "thepiratebay10.org",
                "thepiratebay7.com",
                "torrent-paradise.ml",
                "torrents-csv.com",
                "torrentz2.nz",
                "tpb.party",
                "update.frostwire.com",
                "www.1377x.to",
                "www.frostwire.com",
                "www.magnetdl.homes",
                "www.pirate-bay.net",
                "youtu.be",
                "youtube.com",
                "yts-movie.cc",
                "yts.mx",
                "zoink.ch",
                "zooqle.com",
        };
        private static final HashSet<String> validDomainsSet = new HashSet<>();

        static {
            Collections.addAll(validDomainsSet, validDomains);
        }

        public static void addValidDomain(String domain) {
            synchronized (validDomainsSet) {
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
        }

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            if (!validDomainsSet.contains(s)) {
                // check if the s is a subdomain
                // Create a copy to avoid ConcurrentModificationException when adding during iteration
                synchronized (validDomainsSet) {
                    for (String baseDomain : validDomainsSet) {
                        if (s.contains(baseDomain)) {
                            validDomainsSet.add(s);
                            return true;
                        }
                    }
                }
            }
            return validDomainsSet.contains(s);
        }

        public static boolean hostnameIsValid(String hostname) {
            LOG.info("SSL::FWHostnameVerifier::hostnameIsValid: " + hostname + "...");
            if (!validDomainsSet.contains(hostname)) {
                // check if the s is a subdomain
                // Create a copy to avoid ConcurrentModificationException when adding during iteration
                synchronized (validDomainsSet) {
                    for (String baseDomain : validDomainsSet) {
                        if (hostname.contains(baseDomain)) {
                            validDomainsSet.add(hostname);
                            LOG.info("SSL::FWHostnameVerifier::hostnameIsValid: " + hostname + ": TRUE, validDomainSet updated with: " + hostname);

                            return true;
                        }
                    }
                }
            }
            boolean result = validDomainsSet.contains(hostname);
            LOG.info("SSL::FWHostnameVerifier::hostnameIsValid: " + hostname + ": " + result);
            return result;
        }
    }

    private static final class NullTrustManager implements X509TrustManager {
        private final List<X509Certificate> acceptedCertificates = new ArrayList<>();

        public X509Certificate[] getAcceptedIssuers() {
            return acceptedCertificates.toArray(new X509Certificate[0]);
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {

        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
            if (certs != null && certs.length > 0) {
                X509Certificate cert = certs[0];
                String certHostName = cert.getSubjectX500Principal().getName();
                LOG.info("checkServerTrusted: Certificate issued to " + certHostName);

                if (FWHostnameVerifier.hostnameIsValid(certHostName)) {
                    LOG.info("checkServerTrusted: Certificate matches valid domain " + certHostName);
                    if (!acceptedCertificates.contains(cert)) {
                        acceptedCertificates.add(cert);
                    }
                }
            }
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
