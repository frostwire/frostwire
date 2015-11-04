package com.limegroup.gnutella.gui.notify;

import java.util.EventListener;

public interface AnimatedWindowListener extends EventListener {

    void animationStarted(AnimatedWindowEvent event);
    
    void animationStopped(AnimatedWindowEvent event);

    void animationCompleted(AnimatedWindowEvent event);
    
}
