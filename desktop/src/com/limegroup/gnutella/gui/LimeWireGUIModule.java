package com.limegroup.gnutella.gui;

public class LimeWireGUIModule {
    private static LimeWireGUIModule INSTANCE;

    private LimeWireGUIModule() {
    }

    public static LimeWireGUIModule instance() {
        if (INSTANCE == null) {
            INSTANCE = new LimeWireGUIModule();
        }
        return INSTANCE;
    }

    public LimeWireGUI getLimeWireGUI() {
        return LimeWireGUI.instance();
    }
}
