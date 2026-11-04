/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop;

import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import java.io.File;

/**
 * Sets up {@link BTEngine#ctx} for the length of a test, the way startup does before the MCP server
 * starts, so {@link BTEngine#getInstance()} answers instead of waiting for startup forever. The
 * engine is not started (it has no session), so nothing reaches the network. {@link #close()} puts
 * the previous context back, so tests that check behavior before startup still see no context.
 */
public final class EngineContext implements AutoCloseable {

  private final BTContext previous;

  private EngineContext(File home) {
    previous = BTEngine.ctx;
    BTContext ctx = new BTContext();
    ctx.homeDir = home;
    ctx.torrentsDir = home;
    ctx.dataDir = home;
    BTEngine.ctx = ctx;
  }

  public static EngineContext install(File home) {
    return new EngineContext(home);
  }

  @Override
  public void close() {
    BTEngine.ctx = previous;
  }
}
