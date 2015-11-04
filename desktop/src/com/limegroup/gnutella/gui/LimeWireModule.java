package com.limegroup.gnutella.gui;

/** The master LimeWire module. */
public class LimeWireModule {
    
    private static LimeWireModule INSTANCE;

    public static LimeWireModule instance() {
        if (INSTANCE == null) {
            INSTANCE = new LimeWireModule();
        }
        return INSTANCE;
    }
    
    private final LimeWireGUIModule limeWireGUIModule;
    
    private LimeWireModule() {
        limeWireGUIModule = LimeWireGUIModule.instance();
    }

    public LimeWireGUIModule getLimeWireGUIModule() {
        return limeWireGUIModule;
    }
}
