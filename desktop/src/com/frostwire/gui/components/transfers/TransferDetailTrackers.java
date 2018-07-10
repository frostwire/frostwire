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
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.util.Logger;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
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
                List<AnnounceEntry> items = btDownload.getDl().getTorrentHandle().trackers();
                if (items != null && items.size() > 0) {
                    if (tableMediator.getSize() == 0) {
                        int i = 0;
                        for (AnnounceEntry item : items) {
                            tableMediator.add(new TransferDetailTrackers.TrackerItemHolder(i++, item));
                        }
                    } else {
                        int i = 0;
                        for (AnnounceEntry item : items) {
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
        final boolean isActive;
        public final int seeds;
        public final int peers;
        public final int downloaded;
        public String url;

        TrackerItemHolder(int trackerOffset, AnnounceEntry announceEntry) {
            this.trackerOffset = trackerOffset;
            this.url = announceEntry.url();
            int s = 0;
            int p = 0;
            int d = 0;
            boolean a = false;
            for (AnnounceEndpoint endPoint : announceEntry.endpoints()) {
                s = Math.max(endPoint.scrapeComplete(), s);
                p = Math.max(endPoint.scrapeIncomplete(), p);
                d = Math.max(endPoint.scrapeDownloaded(), d);
                if (!a && endPoint.isWorking()) {
                    a = true;
                }
            }
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
