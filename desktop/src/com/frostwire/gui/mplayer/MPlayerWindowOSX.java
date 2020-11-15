package com.frostwire.gui.mplayer;

public class MPlayerWindowOSX extends MPlayerWindow {
    private static final long serialVersionUID = 3358797658253564084L;

    @Override
    public void toggleFullScreen() {
        mplayerComponent.toggleFullScreen();
        super.toggleFullScreen();
    }
}
