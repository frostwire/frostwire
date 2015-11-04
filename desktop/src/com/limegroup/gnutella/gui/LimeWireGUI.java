package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.LimeWireCore;

public class LimeWireGUI {
    
    private static LimeWireGUI INSTANCE;
    
    static LimeWireGUI instance() {
        if (INSTANCE == null) {
            INSTANCE = new LimeWireGUI();
        }
        return INSTANCE;
    }
    
    private final LimeWireCore limewireCore;
    private final LocalClientInfoFactory localClientInfoFactory;
    
    private LimeWireGUI() {
        limewireCore = LimeWireCore.instance();
        localClientInfoFactory = LocalClientInfoFactoryImpl.instance();
    }
    
    public LimeWireCore getLimeWireCore() {
        return limewireCore;
    }
    
    public LocalClientInfoFactory getLocalClientInfoFactory() {
        return localClientInfoFactory;
    }
}
