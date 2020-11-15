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
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.Logger;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.List;

public final class TransferDetailFiles extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private static final Logger LOG = Logger.getLogger(TransferDetailFiles.class);
    private final TransferDetailFilesTableMediator tableMediator;
    private BittorrentDownload btDownload;

    TransferDetailFiles() {
        tableMediator = new TransferDetailFilesTableMediator();
        setLayout(new MigLayout("fillx, insets 0 0 0 0"));
        add(tableMediator.getComponent(), "gap 0 0 0 0, growx, growy");
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        if (btDownload != null && btDownload.getDl() != null) {
            try {
                List<TransferItem> items = btDownload.getDl().getItems();
                if (items != null && items.size() > 0) {
                    if (this.btDownload != btDownload) {
                        this.btDownload = btDownload;
                        tableMediator.clearTable();
                        int i = 0;
                        for (TransferItem item : items) {
                            if (!item.isSkipped()) {
                                tableMediator.add(new TransferItemHolder(i++, item));
                            }
                        }
                    } else {
                        int i = 0;
                        for (TransferItem item : items) {
                            if (!item.isSkipped()) {
                                tableMediator.update(new TransferItemHolder(i++, item));
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                LOG.error("Error updating transfer files details", e);
            }
        }
    }

    /**
     * A Transfer item does not know its own position within the torrent item list,
     * this class keeps that number and a reference to the transfer item.
     * <p>
     * Also, this is necessary to update the table, since tableMediator.update() doesn't work with plain TransferItems
     */
    public class TransferItemHolder {
        public final TransferItem transferItem;
        final int fileOffset;

        TransferItemHolder(int fileOffset, TransferItem transferItem) {
            this.fileOffset = fileOffset;
            this.transferItem = transferItem;
        }

        @Override
        public int hashCode() {
            return fileOffset;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TransferItemHolder && ((TransferItemHolder) obj).fileOffset == fileOffset;
        }
    }
}
