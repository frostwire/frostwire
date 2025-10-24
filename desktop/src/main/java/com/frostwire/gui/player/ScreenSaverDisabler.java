package com.frostwire.gui.player;

import java.awt.*;
import java.awt.event.KeyEvent;

public class ScreenSaverDisabler implements Runnable {
    private Thread thread;
    private Robot r;

    public ScreenSaverDisabler() {
        try {
            r = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        thread = null;
    }

    public void run() {
        while (thread != null) {
            if (r != null) {
                r.waitForIdle();
                r.keyPress(KeyEvent.VK_CONTROL);
                r.keyRelease(KeyEvent.VK_CONTROL);
            }
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
