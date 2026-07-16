/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.KarmaChainSource;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import javax.swing.table.AbstractTableModel;
import org.junit.jupiter.api.Test;

class PeerDirectorySettingsPaneItemTest {

  @Test
  void peerTableModel_usesPrecomputedTrustScores() throws Exception {
    PeerDirectory directory =
        new PeerDirectory(
            new PeerKarmaCache(
                new RemoteKarmaChainFetcher(
                    new KarmaChainSource() {
                      @Override
                      public Entry fetchManifest(byte[] peerPub) {
                        return null;
                      }
                    })));
    byte[] peerPub = new byte[32];
    peerPub[31] = 1;
    directory.upsert(peerPub, "peer.example", 6888);

    Class<?> modelClass =
        Class.forName(
            "com.limegroup.gnutella.gui.options.panes.PeerDirectorySettingsPaneItem$PeerTableModel");
    Constructor<?> constructor = modelClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    Method setPeers =
        modelClass.getDeclaredMethod("setPeers", java.util.List.class, java.util.List.class);
    setPeers.setAccessible(true);
    AbstractTableModel model = (AbstractTableModel) constructor.newInstance();
    setPeers.invoke(
        model,
        Collections.singletonList(directory.get(peerPub).get()),
        Collections.singletonList(7.5));

    // Trust Score is column 4 after IceBridge version column was added.
    assertEquals("7.50", model.getValueAt(0, 4));
    assertEquals("-", model.getValueAt(0, 2));
  }
}
