/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.jlibtorrent.AnnounceEndpoint;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.util.Logger;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class TransferDetailTrackers extends JPanel implements TransferDetailComponent.TransferDetailPanel {

    private static final Logger LOG = Logger.getLogger(TransferDetailTrackers.class);

    private final TransferDetailTrackersTableMediator tableMediator;
    private BittorrentDownload btDownload;


    TransferDetailTrackers() {
        super(new MigLayout("fillx, gap 0 0, insets 0 0 0 0"));
        tableMediator = new TransferDetailTrackersTableMediator();
        add(tableMediator.getComponent(), "growx, growy");
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        if (btDownload != null && btDownload.getDl() != null) {
            if (this.btDownload != btDownload) {
                tableMediator.clearTable();
            }
            this.btDownload = btDownload;
            try {
                List<AnnounceEntryData> items = trackers(btDownload.getDl().getTorrentHandle().swig());
                if (items != null && items.size() > 0) {
                    if (tableMediator.getSize() == 0) {
                        int i = 0;
                        for (AnnounceEntryData item : items) {
                            tableMediator.add(new TransferDetailTrackers.TrackerItemHolder(i++, item));
                        }
                    } else {
                        int i = 0;
                        for (AnnounceEntryData item : items) {
                            tableMediator.update(new TransferDetailTrackers.TrackerItemHolder(i++, item));
                        }
                    }
                }
            } catch (Throwable e) {
                LOG.error("Error updating data: " + e.getMessage());
            }
        }
    }

    public class TrackerItemHolder {
        final int trackerOffset;
        final AnnounceEntryData announceEntry;

        TrackerItemHolder(int trackerOffset, AnnounceEntryData announceEntry) {
            this.trackerOffset = trackerOffset;
            this.announceEntry = announceEntry;
        }

        @Override
        public int hashCode() {
            return trackerOffset;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TrackerItemHolder && ((TrackerItemHolder) obj).trackerOffset == trackerOffset;
        }
    }

    // TODO: fix jlibtorrent
    // This is necessary because the internal swig vector returns a const reference,
    // that means that the owner of the element is the vector, once that vector
    // is GCed, all bets are off with individual announce entry.
    // The other issue is that keeping heap native memory pinned is not particular
    // nice to the GC world, better keep the data in pure java memory, this would
    // be changed in libtorrent in the next version
    public static List<AnnounceEntryData> trackers(torrent_handle th) {
        announce_entry_vector v = th.trackers();

        int size = (int) v.size();
        ArrayList<AnnounceEntryData> l = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            l.add(new AnnounceEntryData(v.get(i)));
        }

        return l;
    }

    private static List<AnnounceEndpointData> get_endpoints(announce_entry e) {
        announce_endpoint_vector v = e.getEndpoints();
        int size = (int) v.size();
        ArrayList<AnnounceEndpointData> l = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            l.add(new AnnounceEndpointData(v.get(i)));
        }

        return l;
    }

    public static final class AnnounceEntryData {

        private final String url;
        private final List<AnnounceEndpointData> endpoints;

        public AnnounceEntryData(announce_entry e) {
            this.url = Vectors.byte_vector2ascii(e.get_url());
            this.endpoints = get_endpoints(e);
        }

        public String url() {
            return url;
        }

        public List<AnnounceEndpointData> endpoints() {
            return endpoints;
        }
    }

    public static final class AnnounceEndpointData {

        private final int scrapeComplete;
        private final int scrapeIncomplete;
        private final int scrapeDownloaded;

        public AnnounceEndpointData(announce_endpoint e) {
            this.scrapeComplete = e.getScrape_complete();
            this.scrapeIncomplete = e.getScrape_incomplete();
            this.scrapeDownloaded = e.getScrape_downloaded();
        }

        public int scrapeComplete() {
            return scrapeComplete;
        }

        public int scrapeIncomplete() {
            return scrapeIncomplete;
        }

        public int scrapeDownloaded() {
            return scrapeDownloaded;
        }
    }
}
