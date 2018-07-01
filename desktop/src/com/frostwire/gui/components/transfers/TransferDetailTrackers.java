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
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.swig.status_flags_t;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.ArrayList;

public final class TransferDetailTrackers extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private final TransferDetailTrackersTableMediator tableMediator;
    private BittorrentDownload btDownload;


    TransferDetailTrackers() {
        super(new MigLayout("fill"));
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
                ArrayList<AnnounceEntry> items = (ArrayList<AnnounceEntry>) btDownload.getDl().getTorrentHandle().trackers();
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
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public class TrackerItemHolder {
        final int trackerOffset;
        final AnnounceEntry announceEntry;

        TrackerItemHolder(int trackerOffset, AnnounceEntry announceEntry) {
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
}