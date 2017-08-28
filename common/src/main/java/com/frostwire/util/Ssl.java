/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Util class to provide SSL/TLS specific features.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Ssl {

    private static final HostnameVerifier NULL_HOSTNAME_VERIFIER = new NullHostnameVerifier();
    private static final X509TrustManager NULL_TRUST_MANAGER = new NullTrustManager();

    private Ssl() {
    }

    /**
     * Returns a hostname verifier instance that simply accepts all hostnames
     * as valid.
     * <p>
     * The instance returned is always the same and it's thread safe.
     *
     * @return the hostname verifier.
     */
    public static HostnameVerifier nullHostnameVerifier() {
        return NULL_HOSTNAME_VERIFIER;
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

    private static final class NullHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
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
}
