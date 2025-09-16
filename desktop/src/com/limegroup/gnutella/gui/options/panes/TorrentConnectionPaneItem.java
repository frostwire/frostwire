/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.settings.SharingSettings;

import javax.swing.*;

public final class TorrentConnectionPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("BitTorrent Connection Settings");
    private final static String TEXT = I18n.tr("Adjust connection settings to make better use of your internet connection");
    private final static String MAX_ACTIVE_DOWNLOADS = I18n.tr("Maximum active downloads");
    private final static String MAX_GLOBAL_NUM_CONNECTIONS = I18n.tr("Global maximum number of connections");
    private final static String MAX_PEERS = I18n.tr("Maximum number of peers");
    private final static String MAX_ACTIVE_SEEDS = I18n.tr("Maximum active seeds");
    private final static String ENABLE_DISTRIBUTED_HASH_TABLE = I18n.tr("Enable Distributed Hash Table (DHT)");
    private final static String VPN_DROP_PROTECTION = I18n.tr("VPN-Drop Protection. Require VPN connection for BitTorrent");
    private final JCheckBox ENABLE_DISTRIBUTED_HASH_TABLE_CHECKBOX_FIELD = new JCheckBox();
    private final JCheckBox VPN_DROP_PROTECTION_CHECKBOX = new JCheckBox();
    private final WholeNumberField MAX_ACTIVE_DOWNLOADS_FIELD = new SizedWholeNumberField(4);
    private final WholeNumberField MAX_GLOBAL_NUM_CONNECTIONS_FIELD = new SizedWholeNumberField(4);
    private final WholeNumberField MAX_PEERS_FIELD = new SizedWholeNumberField(4);
    private final WholeNumberField MAX_ACTIVE_SEEDS_FIELD = new SizedWholeNumberField(4);

    public TorrentConnectionPaneItem() {
        super(TITLE, TEXT);
        BoxPanel panel = new BoxPanel();
        LabeledComponent comp = new LabeledComponent(ENABLE_DISTRIBUTED_HASH_TABLE,
                ENABLE_DISTRIBUTED_HASH_TABLE_CHECKBOX_FIELD,
                LabeledComponent.LEFT_GLUE,
                LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        panel.addVerticalComponentGap();
        if (GUIConstants.Feature.VPN_DROP_GUARD.enabled()) {
            comp = new LabeledComponent(VPN_DROP_PROTECTION,
                    VPN_DROP_PROTECTION_CHECKBOX,
                    LabeledComponent.LEFT_GLUE,
                    LabeledComponent.LEFT);
            panel.add(comp.getComponent());
            panel.addVerticalComponentGap();
        }
        comp = new LabeledComponent(
                MAX_ACTIVE_DOWNLOADS,
                MAX_ACTIVE_DOWNLOADS_FIELD, LabeledComponent.LEFT_GLUE,
                LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        panel.addVerticalComponentGap();
        comp = new LabeledComponent(
                MAX_ACTIVE_SEEDS,
                MAX_ACTIVE_SEEDS_FIELD, LabeledComponent.LEFT_GLUE,
                LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        panel.addVerticalComponentGap();
        comp = new LabeledComponent(
                MAX_GLOBAL_NUM_CONNECTIONS,
                MAX_GLOBAL_NUM_CONNECTIONS_FIELD, LabeledComponent.LEFT_GLUE,
                LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        panel.addVerticalComponentGap();
        comp = new LabeledComponent(
                MAX_PEERS,
                MAX_PEERS_FIELD, LabeledComponent.LEFT_GLUE,
                LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        panel.addVerticalComponentGap();
        add(panel);
    }

    @Override
    public boolean isDirty() {
        final BTEngine btEngine = BTEngine.getInstance();
        return (btEngine.isDhtRunning() == ENABLE_DISTRIBUTED_HASH_TABLE_CHECKBOX_FIELD.isSelected() ||
                ConnectionSettings.VPN_DROP_PROTECTION.getValue() != VPN_DROP_PROTECTION_CHECKBOX.isSelected() ||
                btEngine.maxActiveDownloads() != MAX_ACTIVE_DOWNLOADS_FIELD.getValue()) ||
                (btEngine.maxConnections() != MAX_GLOBAL_NUM_CONNECTIONS_FIELD.getValue()) ||
                (btEngine.maxPeers() != MAX_PEERS_FIELD.getValue()) ||
                (btEngine.maxActiveSeeds() != MAX_ACTIVE_SEEDS_FIELD.getValue());
    }

    @Override
    public void initOptions() {
        final BTEngine btEngine = BTEngine.getInstance();
        ENABLE_DISTRIBUTED_HASH_TABLE_CHECKBOX_FIELD.setSelected(SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue());
        VPN_DROP_PROTECTION_CHECKBOX.setSelected(ConnectionSettings.VPN_DROP_PROTECTION.getValue());
        MAX_GLOBAL_NUM_CONNECTIONS_FIELD.setValue(btEngine.maxConnections());
        MAX_PEERS_FIELD.setValue(btEngine.maxPeers());
        MAX_ACTIVE_DOWNLOADS_FIELD.setValue(btEngine.maxActiveDownloads());
        MAX_ACTIVE_SEEDS_FIELD.setValue(btEngine.maxActiveSeeds());
    }

    @Override
    public boolean applyOptions() {
        BTEngine btEngine = BTEngine.getInstance();
        applyDHTOptions(btEngine);
        applyVPNDropProtectionOption(btEngine);
        btEngine.maxConnections(MAX_GLOBAL_NUM_CONNECTIONS_FIELD.getValue());
        btEngine.maxPeers(MAX_PEERS_FIELD.getValue());
        btEngine.maxActiveDownloads(MAX_ACTIVE_DOWNLOADS_FIELD.getValue());
        btEngine.maxActiveSeeds(MAX_ACTIVE_SEEDS_FIELD.getValue());
        return isDirty();
    }

    private void applyDHTOptions(BTEngine btEngine) {
        boolean dhtExpectedValue = ENABLE_DISTRIBUTED_HASH_TABLE_CHECKBOX_FIELD.isSelected();
        boolean dhtCurrentStatus = btEngine.isDhtRunning();
        if (dhtCurrentStatus && !dhtExpectedValue) {
            btEngine.stopDht();
            SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.setValue(false);
        } else if (!dhtCurrentStatus && dhtExpectedValue) {
            btEngine.startDht();
            SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.setValue(true);
        }
    }

    private void applyVPNDropProtectionOption(BTEngine btEngine) {
        boolean vpnDropProtectionSelected = VPN_DROP_PROTECTION_CHECKBOX.isSelected();
        if (vpnDropProtectionSelected && !VPNs.isVPNActive()) {
            btEngine.pause();
        } else if (!vpnDropProtectionSelected && btEngine.isPaused()) {
            btEngine.resume();
        }
        ConnectionSettings.VPN_DROP_PROTECTION.setValue(vpnDropProtectionSelected);
        GUIMediator.instance().getStatusLine().updateVPNDropProtectionLabelState();
        GUIMediator.instance().getStatusLine().refresh();
    }
}
