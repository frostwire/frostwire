/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.android.gui.adapters;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Address;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.transfers.BittorrentDownload;

import java.io.File;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MagnetUriBuilder {

    private static Logger LOG = Logger.getLogger(MagnetUriBuilder.class);
    private final String torrentFilePath;
    private final BittorrentDownload btDownload;

    MagnetUriBuilder(String torrentFilePath, BittorrentDownload btDownload) {
        this.torrentFilePath = torrentFilePath;
        this.btDownload = btDownload;
    }

    public MagnetUriBuilder(String torrentFilePath) {
        this(torrentFilePath, null);
    }

    public MagnetUriBuilder(BittorrentDownload download) {
        this(null, download);
    }

    @Override
    public String toString() {
        if (this.torrentFilePath != null) {
            try {
                String magnetUri = new TorrentInfo(new File(this.torrentFilePath)).makeMagnetUri();
                magnetUri = appendXPEParameter(magnetUri);
                return magnetUri;
            } catch (Throwable e) {
                LOG.warn("Error trying to get magnet", e);
            }
        } else if (this.btDownload != null) {
            return appendXPEParameter(btDownload.magnetUri());
        }
        return super.toString();
    }

    private String appendXPEParameter(String magnetUri) {
        String xpe = null;
        final BTEngine btEngine = BTEngine.getInstance();
        if (btEngine.getExternalAddress() != null && btEngine.getListenPort() != -1) {
            Address externalAddress = btEngine.getExternalAddress();
            xpe = externalAddress.toString() + ":" + btEngine.getListenPort();
        }
        if (xpe != null) {
            magnetUri = magnetUri + "&x.pe=" + xpe;
        }

        // since I don't know what the internal port is and usually
        // when ports are mapped they match, I'll also go through the list
        // of internal addresses
        if (btEngine.getListenPort() != -1) {
            try {
                final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

                if (networkInterfaces != null) {
                    while (networkInterfaces.hasMoreElements()) {
                        NetworkInterface iface = networkInterfaces.nextElement();
                        final List<InterfaceAddress> interfaceAddresses = iface.getInterfaceAddresses();
                        for (InterfaceAddress ifaceAddress : interfaceAddresses) {
                            String address = ifaceAddress.getAddress().getHostAddress();
                            if (!address.startsWith("::") &&
                                !address.equals("127.0.0.1") &&
                                !address.contains("dummy") &&
                                !address.contains("wlan")) {
                                magnetUri = magnetUri + "&x.pe=" + address +":" + btEngine.getListenPort();
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        return magnetUri;
    }
}
