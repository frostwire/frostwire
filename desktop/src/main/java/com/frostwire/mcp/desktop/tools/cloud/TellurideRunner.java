/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.cloud;

import com.frostwire.search.telluride.TellurideLauncher;
import com.frostwire.search.telluride.TellurideListener;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.io.File;

/**
 * Finds and starts the Telluride executable for the cloud tools. Test seam: tests pass a fake one,
 * since the real one downloads from the web.
 */
interface TellurideRunner {

  TellurideRunner INSTALLED =
      new TellurideRunner() {
        @Override
        public File executable() {
          return FrostWireUtils.getTellurideLauncherFile();
        }

        @Override
        public void launch(
            File executable,
            String url,
            File saveDir,
            boolean audioOnly,
            boolean metaOnly,
            TellurideListener listener) {
          TellurideLauncher.launch(
              executable, url, saveDir, audioOnly, metaOnly, false, false, listener);
        }
      };

  /** The Telluride executable, or null when there is none for this platform. */
  File executable();

  void launch(
      File executable,
      String url,
      File saveDir,
      boolean audioOnly,
      boolean metaOnly,
      TellurideListener listener);
}
