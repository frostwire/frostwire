/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.swig.announce_endpoint;
import com.frostwire.jlibtorrent.swig.announce_infohash;
import com.frostwire.util.Logger;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.List;

public final class TransferDetailTrackers extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private static final Logger LOG = Logger.getLogger(TransferDetailTrackers.class);
    private TransferDetailTrackersTableMediator tableMediator;

    TransferDetailTrackers() {
        super(new MigLayout("fillx, gap 0 0, insets 0 0 0 0"));
        // Defer table mediator creation to avoid EDT violation
        // JTable initialization triggers expensive UI updates (>2 second EDT block)
        SwingUtilities.invokeLater(() -> {
            tableMediator = new TransferDetailTrackersTableMediator();
            add(tableMediator.getComponent(), "growx, growy");
            revalidate();
            repaint();
        });
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        if (tableMediator == null || btDownload == null || btDownload.getDl() == null) {
            return;
        }
        try {
            tableMediator.clearTable();
            TorrentHandle torrentHandle = btDownload.getDl().getTorrentHandle();
            if (torrentHandle == null) {
                return;
            }
            TorrentStatus status = torrentHandle.status();
            // Let's create the DHT, LSD and PEX TrackerItemHolders
            List<PeerInfo> peerInfos = torrentHandle.peerInfo();
            List<AnnounceEntry> items = torrentHandle.trackers();
            if (items != null && items.size() > 0) {
                int i = 0;
                for (AnnounceEntry item : items) {
                    tableMediator.add(new TransferDetailTrackers.TrackerItemHolder(i++, item));
                }
            }
            TrackerItemHolder dhtTrackerItemHolder = getSpecialAnnounceEntry(SpecialAnnounceEntryType.DHT, status, peerInfos);
            TrackerItemHolder lsdTrackerItemHolder = getSpecialAnnounceEntry(SpecialAnnounceEntryType.LSD, status, peerInfos);
            TrackerItemHolder pexTrackerItemHolder = getSpecialAnnounceEntry(SpecialAnnounceEntryType.PEX, status, peerInfos);
            // gotta add them last and in reverse order so they appear at the top by default
            tableMediator.add(pexTrackerItemHolder);
            tableMediator.add(lsdTrackerItemHolder);
            tableMediator.add(dhtTrackerItemHolder);
        } catch (Throwable e) {
            LOG.error("Error updating data: " + e.getMessage());
        }
    }

    private TrackerItemHolder getSpecialAnnounceEntry(SpecialAnnounceEntryType entryType, TorrentStatus status, List<PeerInfo> peerInfosCopy) {
        boolean isActive = false;
        int seeds = 0;
        int peers = 0;
        long downloaded = 0;
        int trackerOffset = tableMediator.getSize();
        if (status != null) {
            switch (entryType) {
                case DHT:
                    trackerOffset += 2;
                    isActive = status.announcingToDht();
                    break;
                case LSD:
                    trackerOffset += 1;
                    isActive = status.announcingToLsd();
                    break;
                case PEX:
                    // will be set to true if we find a single peer that came via PEX
                    isActive = false;
                    break;
            }
        }
        if (!peerInfosCopy.isEmpty()) {
            for (PeerInfo peer : peerInfosCopy) {
                byte PEER_SOURCE_FLAG_DHT = 2;
                boolean entryDHTMatchesSource = entryType == SpecialAnnounceEntryType.DHT &&
                        (peer.source() & PEER_SOURCE_FLAG_DHT) == PEER_SOURCE_FLAG_DHT;
                byte PEER_SOURCE_FLAG_LSD = 8;
                boolean entryLSDMatchesSource = entryType == SpecialAnnounceEntryType.LSD &&
                        (peer.source() & PEER_SOURCE_FLAG_LSD) == PEER_SOURCE_FLAG_LSD;
                byte PEER_SOURCE_FLAG_PEX = 4;
                boolean entryPEXMatchesSource = entryType == SpecialAnnounceEntryType.PEX &&
                        (peer.source() & PEER_SOURCE_FLAG_PEX) == PEER_SOURCE_FLAG_PEX;
                if (entryDHTMatchesSource || entryLSDMatchesSource || entryPEXMatchesSource) {
                    int PEER_FLAG_SEED = 1024;
                    if ((peer.flags() & PEER_FLAG_SEED) == PEER_FLAG_SEED) {
                        seeds++;
                        // QUESTION for aldenml: shouldn't we consider seeds as peers too?
                        // qTorrent isn't but I was under the impression that peers were also counted as peers
                    } else {
                        peers++;
                    }
                    downloaded += peer.totalDownload();
                }
            }
        }
        if (entryType == SpecialAnnounceEntryType.PEX) {
            isActive = (peers > 0) || (seeds > 0);
        }
        boolean stateObjAvailable = status != null;
        boolean activeState = false;
        if (stateObjAvailable) {
            TorrentStatus.State state = status.state();
            activeState = !state.equals(TorrentStatus.State.FINISHED) &&
                    !state.equals(TorrentStatus.State.UNKNOWN);
        }
        isActive = isActive && activeState;
        return new TrackerItemHolder(trackerOffset, isActive, seeds, peers, downloaded, entryType.name());
    }

    private enum SpecialAnnounceEntryType {
        DHT,
        LSD,
        PEX
    }

    public static final class TrackerItemHolder {
        final int trackerOffset;
        final boolean isActive;
        final int seeds;
        final int peers;
        final long downloaded;
        final String url;

        TrackerItemHolder(int trackerOffset, boolean isActive, int seeds, int peers, long downloaded, String url) {
            this.trackerOffset = trackerOffset;
            this.isActive = isActive;
            this.seeds = seeds;
            this.peers = peers;
            this.downloaded = downloaded;
            this.url = url;
        }

        TrackerItemHolder(int trackerOffset, AnnounceEntry announceEntry) {
            int s = 0;
            int p = 0;
            long d = 0;
            boolean a = false;

            for (AnnounceEndpoint endPoint : announceEntry.endpoints()) {
                announce_endpoint announceEndpoint = endPoint.swig();

                announce_infohash infohashV1 = announceEndpoint.get_infohash_v1();
                announce_infohash infohashV2 = announceEndpoint.get_infohash_v2();

                if (infohashV1 != null) {
                    s = Math.max(infohashV1.getScrape_complete(), s);
                    p = Math.max(infohashV1.getScrape_incomplete(), p);
                    d = Math.max(infohashV1.getScrape_downloaded(), d);
                }
                if (infohashV2 != null) {
                    s = Math.max(infohashV2.getScrape_complete(), s);
                    p = Math.max(infohashV2.getScrape_incomplete(), p);
                    d = Math.max(infohashV2.getScrape_downloaded(), d);
                }

                if (!a && announceEndpoint.getEnabled()) {
                    boolean v1IsWorking = infohashV1.getFails() == 0;
                    boolean v2IsWorking = infohashV2.getFails() == 0;
                    a = v1IsWorking || v2IsWorking;
                }

            }
            this.trackerOffset = trackerOffset;
            this.url = announceEntry.url();
            this.seeds = s;
            this.peers = p;
            this.downloaded = d;
            this.isActive = a;
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
}
