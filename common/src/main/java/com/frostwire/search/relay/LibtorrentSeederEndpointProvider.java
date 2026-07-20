/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.util.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SeederEndpointProvider} backed by the running jlibtorrent session.
 * Uses {@code BTEngine.magnetPeers()} (external address learned via
 * DHT/UPnP + listen endpoints) and replaces wildcard listen addresses
 * ({@code 0.0.0.0} / {@code [::]}) with this host's LAN IPv4 addresses.
 */
public final class LibtorrentSeederEndpointProvider implements SeederEndpointProvider {

    /** Advertise at most this many endpoints (external first, then LAN). */
    public static final int MAX_ENDPOINTS = 4;

    private static final Logger LOG = Logger.getLogger(LibtorrentSeederEndpointProvider.class);

    @Override
    public List<String> seederEndpoints() {
        try {
            String xpe = BTEngine.getInstance().magnetPeers();
            return buildEndpoints(parseXpe(xpe), lanIPv4s());
        } catch (Throwable t) {
            LOG.debug("LibtorrentSeederEndpointProvider: no endpoints", t);
            return Collections.emptyList();
        }
    }

    /** Split a {@code &x.pe=host:port&x.pe=...} parameter string into entries. */
    static List<String> parseXpe(String magnetPeersParams) {
        if (magnetPeersParams == null || magnetPeersParams.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : magnetPeersParams.split("&x\\.pe=")) {
            String ep = part == null ? "" : part.trim();
            if (!ep.isEmpty() && ep.length() <= 256) {
                out.add(ep);
            }
        }
        return out;
    }

    /**
     * Keep non-wildcard entries as-is; replace wildcard listen addresses
     * with each LAN address paired to the wildcard endpoint's port.
     */
    static List<String> buildEndpoints(Collection<String> entries, Collection<String> lanAddrs) {
        List<String> out = new ArrayList<>();
        int wildcardPort = -1;
        for (String e : entries) {
            if (e == null || e.isEmpty()) {
                continue;
            }
            if (isWildcardHost(hostOf(e))) {
                if (wildcardPort <= 0) {
                    wildcardPort = portOf(e);
                }
                continue;
            }
            if (out.size() < MAX_ENDPOINTS && !out.contains(e)) {
                out.add(e);
            }
        }
        if (wildcardPort > 0) {
            for (String ip : lanAddrs) {
                if (out.size() >= MAX_ENDPOINTS) {
                    break;
                }
                String ep = ip + ":" + wildcardPort;
                if (!out.contains(ep)) {
                    out.add(ep);
                }
            }
        }
        return out;
    }

    /** Up to 2 non-loopback, non-link-local IPv4 addresses on up interfaces. */
    static List<String> lanIPv4s() {
        Set<String> out = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isPointToPoint()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address
                            && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                        out.add(a.getHostAddress());
                    }
                }
            }
        } catch (Throwable t) {
            LOG.debug("LibtorrentSeederEndpointProvider: LAN enumeration failed", t);
        }
        List<String> list = new ArrayList<>(out);
        Collections.sort(list);
        return list.size() <= 2 ? list : list.subList(0, 2);
    }

    private static String hostOf(String endpoint) {
        if (endpoint.startsWith("[")) {
            int close = endpoint.indexOf(']');
            return close > 0 ? endpoint.substring(1, close) : endpoint;
        }
        int colon = endpoint.lastIndexOf(':');
        return colon > 0 ? endpoint.substring(0, colon) : endpoint;
    }

    private static int portOf(String endpoint) {
        int colon = endpoint.lastIndexOf(':');
        if (colon < 0 || colon == endpoint.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(endpoint.substring(colon + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isWildcardHost(String host) {
        return host == null || host.isEmpty()
                || host.equals("0.0.0.0") || host.equals("::")
                || host.equals("0:0:0:0:0:0:0:0");
    }
}
