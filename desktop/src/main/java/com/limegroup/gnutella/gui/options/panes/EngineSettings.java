/*
 *     Created by Angel Leon (@gubatron)
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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.SettingsPack;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.limewire.util.NetworkUtils;

/**
 * The libtorrent side of the Options pages' OK button. Every call here goes to libtorrent's
 * session, which can take seconds to answer on a busy or starved machine, so the pages hand it to
 * the background queue and never make it on the EDT (frostwire/frostwire#1546,
 * frostwire/frostwire#1568). Pages call it only when one of their settings changed.
 */
final class EngineSettings {
  /** The engine calls the Options pages make. */
  interface Engine {
    void applySettings(SettingsPack settings);

    void listenInterfaces(String listenInterfaces);
  }

  /** Where the engine's work runs; serial, so work queued by one OK runs in order. */
  static Consumer<Runnable> background = BackgroundQueuedExecutorService::schedule;

  static Supplier<Engine> engine =
      () -> {
        BTEngine btEngine = BTEngine.getInstance();
        return new Engine() {
          @Override
          public void applySettings(SettingsPack settings) {
            btEngine.applySettings(settings);
          }

          @Override
          public void listenInterfaces(String listenInterfaces) {
            btEngine.listenInterfaces(listenInterfaces);
          }
        };
      };

  private EngineSettings() {}

  /**
   * Fills a new settings pack with {@code fill} and applies it to the engine, in the background.
   */
  static void apply(Consumer<SettingsPack> fill) {
    background.accept(
        () -> {
          SettingsPack settings = new SettingsPack();
          fill.accept(settings);
          engine.get().applySettings(settings);
        });
  }

  /** Rebinds the engine to the saved network interface and port range, in the background. */
  static void rebindListenInterface() {
    background.accept(
        () -> {
          int port =
              NetworkUtils.getPortInRange(
                  ConnectionSettings.MANUAL_PORT_RANGE.getValue(),
                  ConnectionSettings.PORT_RANGE_0.getDefaultValue(),
                  ConnectionSettings.PORT_RANGE_1.getDefaultValue(),
                  ConnectionSettings.PORT_RANGE_0.getValue(),
                  ConnectionSettings.PORT_RANGE_1.getValue());
          engine
              .get()
              .listenInterfaces(
                  NetworkUtils.getLibtorrentFormattedNetworkInterface(
                      ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.getValue(),
                      "0.0.0.0",
                      ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue(),
                      port));
        });
  }
}
