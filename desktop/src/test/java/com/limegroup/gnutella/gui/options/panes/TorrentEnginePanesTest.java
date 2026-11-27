/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.mcp.desktop.EngineContext;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.WholeNumberField;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Options &gt; BitTorrent &gt; Advanced: the speed limits and the connection settings, over an
 * engine that was never started (so nothing reaches libtorrent or the network). The work the panes
 * queue for the engine is captured and run by the test.
 */
class TorrentEnginePanesTest {
  @TempDir Path home;

  private EngineContext context;
  private Locale locale;
  private boolean dht;
  private boolean vpn;
  private boolean multiple;
  private Consumer<Runnable> speedBackground;
  private Consumer<Runnable> connectionBackground;
  private final List<Runnable> queued = new ArrayList<>();

  @BeforeEach
  void installTheEngineContext() {
    locale = Locale.getDefault();
    Locale.setDefault(Locale.US);
    context = EngineContext.install(home.resolve("engine").toFile());
    dht = SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue();
    vpn = ConnectionSettings.VPN_DROP_PROTECTION.getValue();
    multiple = ConnectionSettings.ALLOW_MULTIPLE_CONNECTIONS_PER_PID.getValue();
    speedBackground = TorrentGlobalSpeedPaneItem.background;
    connectionBackground = TorrentConnectionPaneItem.background;
    TorrentGlobalSpeedPaneItem.background = queued::add;
    TorrentConnectionPaneItem.background = queued::add;
  }

  @AfterEach
  void restore() {
    // the engine is a process-wide singleton and the jobs the test ran changed its cached limits
    BTEngine engine = BTEngine.getInstance();
    engine.stopDht();
    engine.maxConnections(0);
    engine.maxPeers(0);
    engine.maxActiveDownloads(0);
    engine.maxActiveSeeds(0);
    engine.downloadRateLimit(0);
    engine.uploadRateLimit(0);
    TorrentGlobalSpeedPaneItem.background = speedBackground;
    TorrentConnectionPaneItem.background = connectionBackground;
    SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.setValue(dht);
    ConnectionSettings.VPN_DROP_PROTECTION.setValue(vpn);
    ConnectionSettings.ALLOW_MULTIPLE_CONNECTIONS_PER_PID.setValue(multiple);
    context.close();
    Locale.setDefault(locale);
  }

  private static List<JSlider> sliders(TorrentGlobalSpeedPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JSlider.class);
  }

  /** The value labels, which sit on the page after the description labels. */
  private static List<String> speedTexts(TorrentGlobalSpeedPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JLabel.class).stream()
        .map(JLabel::getText)
        .filter(t -> t != null && (t.endsWith("KB/s") || t.equals(I18n.tr("Unlimited"))))
        .toList();
  }

  @Test
  void anEngineWithNoLimitsShowsBothSlidersAtUnlimited() throws Exception {
    TorrentGlobalSpeedPaneItem pane = PaneSupport.onEdt(TorrentGlobalSpeedPaneItem::new);
    pane.initOptions();
    assertEquals(2, sliders(pane).size());
    for (JSlider slider : sliders(pane)) {
      assertEquals(slider.getMaximum(), slider.getValue(), "no limit is the far end");
    }
    assertEquals(List.of(I18n.tr("Unlimited"), I18n.tr("Unlimited")), speedTexts(pane));
    assertFalse(pane.isDirty());
  }

  @Test
  void aSpeedLimitIsShownInKilobytesAndSavedToTheEngine() throws Exception {
    TorrentGlobalSpeedPaneItem pane = PaneSupport.onEdt(TorrentGlobalSpeedPaneItem::new);
    pane.initOptions();
    sliders(pane).get(0).setValue(500);
    sliders(pane).get(1).setValue(2048);
    assertEquals(List.of("500 KB/s", "2,048 KB/s"), speedTexts(pane));

    assertFalse(pane.applyOptions());
    assertEquals(1, queued.size(), "one job for the engine, none run on the EDT");
    queued.get(0).run();
    assertEquals(500 * 1024, BTEngine.getInstance().sessionLimits().downloadRateLimit());
    assertEquals(2048 * 1024, BTEngine.getInstance().sessionLimits().uploadRateLimit());

    TorrentGlobalSpeedPaneItem reopened = PaneSupport.onEdt(TorrentGlobalSpeedPaneItem::new);
    reopened.initOptions();
    assertEquals(500, sliders(reopened).get(0).getValue());
    assertEquals(2048, sliders(reopened).get(1).getValue());
  }

  @Test
  void theFarEndOfASliderMeansUnlimitedAndIsSentAsZero() throws Exception {
    TorrentGlobalSpeedPaneItem pane = PaneSupport.onEdt(TorrentGlobalSpeedPaneItem::new);
    pane.initOptions();
    sliders(pane).get(0).setValue(300);
    sliders(pane).get(1).setValue(300);
    pane.applyOptions();
    queued.remove(0).run();
    assertEquals(300 * 1024, BTEngine.getInstance().sessionLimits().downloadRateLimit());

    sliders(pane).get(0).setValue(sliders(pane).get(0).getMaximum());
    sliders(pane).get(1).setValue(sliders(pane).get(1).getMaximum());
    assertEquals(List.of(I18n.tr("Unlimited"), I18n.tr("Unlimited")), speedTexts(pane));
    pane.applyOptions();
    queued.remove(0).run();
    assertEquals(0, BTEngine.getInstance().sessionLimits().downloadRateLimit());
    assertEquals(0, BTEngine.getInstance().sessionLimits().uploadRateLimit());
  }

  private static WholeNumberField field(TorrentConnectionPaneItem pane, int index) {
    return PaneSupport.all(pane.getContainer(), WholeNumberField.class).get(index);
  }

  private static JCheckBox dhtBox(TorrentConnectionPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JCheckBox.class).get(0);
  }

  @Test
  void theConnectionPageShowsTheStoredChoicesAndTheEngineLimits() throws Exception {
    SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.setValue(true);
    ConnectionSettings.ALLOW_MULTIPLE_CONNECTIONS_PER_PID.setValue(true);
    TorrentConnectionPaneItem pane = PaneSupport.onEdt(TorrentConnectionPaneItem::new);
    pane.initOptions();
    assertTrue(dhtBox(pane).isSelected());
    List<JCheckBox> boxes = PaneSupport.all(pane.getContainer(), JCheckBox.class);
    assertTrue(boxes.get(boxes.size() - 1).isSelected(), "multiple connections per peer id");
    assertEquals(4, PaneSupport.all(pane.getContainer(), WholeNumberField.class).size());
  }

  @Test
  void aChangeOnTheConnectionPageIsSavedAndQueuedForTheEngine() throws Exception {
    SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.setValue(false);
    ConnectionSettings.ALLOW_MULTIPLE_CONNECTIONS_PER_PID.setValue(false);
    TorrentConnectionPaneItem pane = PaneSupport.onEdt(TorrentConnectionPaneItem::new);
    pane.initOptions();
    boolean cleanBefore = !pane.isDirty();

    dhtBox(pane).setSelected(true);
    List<JCheckBox> boxes = PaneSupport.all(pane.getContainer(), JCheckBox.class);
    boxes.get(boxes.size() - 1).setSelected(true);
    assertTrue(pane.isDirty());

    assertFalse(pane.applyOptions());
    assertTrue(cleanBefore, "a freshly opened page has nothing to save");
    assertTrue(SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue());
    assertTrue(ConnectionSettings.ALLOW_MULTIPLE_CONNECTIONS_PER_PID.getValue());
    assertEquals(1, queued.size(), "the engine work is queued, not run on the EDT");
  }

  @Test
  void theLimitFieldsFeedTheEngineJob() throws Exception {
    TorrentConnectionPaneItem pane = PaneSupport.onEdt(TorrentConnectionPaneItem::new);
    pane.initOptions();
    // order on the page: active downloads, active seeds, connections, peers
    field(pane, 0).setValue(3);
    field(pane, 1).setValue(4);
    field(pane, 2).setValue(200);
    field(pane, 3).setValue(50);
    assertTrue(pane.isDirty());
    pane.applyOptions();
    assertEquals(1, queued.size());
    try {
      queued.get(0).run();
    } catch (RuntimeException engineNotStarted) {
      // starting DHT or applying libtorrent settings needs a session; the limits are set first
    }
    assertEquals(3, BTEngine.getInstance().sessionLimits().maxActiveDownloads());
  }
}
