/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class KeyedRowSyncTest {

  record Peer(String endpoint, long uploaded) {}

  public static final class PeerLine extends AbstractDataLine<Peer> {
    public PeerLine() {}

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
      return new LimeTableColumn(col, "COL" + col, "col" + col, 50, true, Long.class);
    }

    @Override
    public boolean isDynamic(int col) {
      return true;
    }

    @Override
    public boolean isClippable(int col) {
      return false;
    }

    @Override
    public Object getValueAt(int col) {
      return getInitializeObject().uploaded();
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }

    @Override
    public void update() {}
  }

  static final class PeerModel extends BasicDataLineModel<PeerLine, Peer> {
    PeerModel() {
      super(PeerLine.class);
    }
  }

  private static Map<String, Long> shown(PeerModel model) {
    Map<String, Long> rows = new TreeMap<>();
    for (int i = 0; i < model.getRowCount(); i++) {
      Peer peer = model.get(i).getInitializeObject();
      rows.put(peer.endpoint(), (Long) model.get(i).getValueAt(0));
    }
    return rows;
  }

  @Test
  void rowsFollowTheirPeerWhenTheSnapshotIsReorderedAndChanges() {
    PeerModel model = new PeerModel();
    KeyedRowSync.sync(model, List.of(new Peer("a:1", 10), new Peer("b:2", 20)), Peer::endpoint);
    assertEquals(Map.of("a:1", 10L, "b:2", 20L), shown(model));

    // libtorrent returns peers in a different order with new totals
    KeyedRowSync.sync(model, List.of(new Peer("b:2", 25), new Peer("a:1", 15)), Peer::endpoint);

    assertEquals(Map.of("a:1", 15L, "b:2", 25L), shown(model));
  }

  @Test
  void departedPeersAreRemovedAndNewOnesAdded() {
    PeerModel model = new PeerModel();
    KeyedRowSync.sync(
        model, List.of(new Peer("a:1", 1), new Peer("b:2", 2), new Peer("c:3", 3)), Peer::endpoint);

    KeyedRowSync.sync(model, List.of(new Peer("c:3", 30), new Peer("d:4", 4)), Peer::endpoint);

    assertEquals(Map.of("c:3", 30L, "d:4", 4L), shown(model));
  }

  @Test
  void anEmptySnapshotClearsTheTable() {
    PeerModel model = new PeerModel();
    KeyedRowSync.sync(model, List.of(new Peer("a:1", 1)), Peer::endpoint);

    KeyedRowSync.sync(model, List.of(), Peer::endpoint);

    assertEquals(0, model.getRowCount());
  }
}
