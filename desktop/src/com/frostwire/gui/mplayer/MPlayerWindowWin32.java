package com.frostwire.gui.mplayer;

import java.awt.peer.ComponentPeer;

import org.limewire.util.SystemUtils;

import sun.awt.windows.WComponentPeer;

public class MPlayerWindowWin32 extends MPlayerWindow {

	private static final long serialVersionUID = 5711345717783989492L;

	public long getCanvasComponentHwnd() {
        @SuppressWarnings("deprecation")
        ComponentPeer cp = videoCanvas.getPeer();
        if ((cp instanceof WComponentPeer)) {
            return ((WComponentPeer) cp).getHWnd();
        } else {
            return 0;
        }
    }
	
	public long getHwnd() {
		@SuppressWarnings("deprecation")
        ComponentPeer cp = getPeer();
        if ((cp instanceof WComponentPeer)) {
            return ((WComponentPeer) cp).getHWnd();
        } else {
            return 0;
        }
    }
	
	public void toggleFullScreen() {
		SystemUtils.toggleFullScreen(getHwnd());
		super.toggleFullScreen();
	}
}
