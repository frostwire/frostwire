/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.DisplayKarma;
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

  /**
   * frostwire/frostwire#1418: the Karma column shows {@link PeerDirectory#displayKarma}, the value
   * search results show, read off the EDT by the pane's refresh, and sorts it by value.
   */
  @Test
  void peerTableModel_showsThePrecomputedDisplayKarma() throws Exception {
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
    DisplayKarma karma = DisplayKarma.forPeer(directory, peerPub);
    setPeers.invoke(
        model,
        Collections.singletonList(directory.get(peerPub).get()),
        Collections.singletonList(karma));

    // Karma is column 4 after IceBridge version column was added.
    assertEquals(DisplayKarma.of(directory.displayKarma(peerPub)), model.getValueAt(0, 4));
    // The karma the peer received (#1417): nobody endorsed it, so 0, not the trust score's 1.
    assertEquals(DisplayKarma.of(0).text(), model.getValueAt(0, 4).toString());
    assertEquals("Karma", model.getColumnName(4));
    assertEquals(DisplayKarma.class, model.getColumnClass(4));
    assertEquals("-", model.getValueAt(0, 2));
  }
}
