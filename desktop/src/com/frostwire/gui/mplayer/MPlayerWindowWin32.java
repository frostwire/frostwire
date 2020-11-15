package com.frostwire.gui.mplayer;

import org.limewire.util.SystemUtils;

public final class MPlayerWindowWin32 extends MPlayerWindow {
    public long getCanvasComponentHwnd() {
        return SystemUtils.getWindowHandle(videoCanvas);
    }

    public long getHwnd() {
        return SystemUtils.getWindowHandle(this);
    }

    public void toggleFullScreen() {
        SystemUtils.toggleFullScreen(getHwnd());
        super.toggleFullScreen();
    }
}
