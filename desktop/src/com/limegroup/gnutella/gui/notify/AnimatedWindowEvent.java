package com.limegroup.gnutella.gui.notify;

import com.limegroup.gnutella.gui.notify.AnimatedWindow.AnimationType;

import java.util.EventObject;

public class AnimatedWindowEvent extends EventObject {

    /**
     * 
     */
    private static final long serialVersionUID = 7036029827507522939L;
    private final AnimationType animationType;
    
    public AnimatedWindowEvent(Object source, AnimationType animationType) {
        super(source);
        
        this.animationType = animationType;
    }

    public AnimationType getAnimationType() {
        return animationType;
    }
    
}
