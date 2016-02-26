package com.frostwire.bittorrent;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTContext {

    public File homeDir;
    public File torrentsDir;
    public File dataDir;
    public String interfaces;
    public int retries;
    public boolean optimizeMemory;
}
