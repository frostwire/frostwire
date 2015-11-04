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
    public int port0;
    public int port1;
    public String iface;
    public boolean optimizeMemory;
}
