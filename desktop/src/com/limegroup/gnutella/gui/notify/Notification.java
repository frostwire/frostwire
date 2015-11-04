package com.limegroup.gnutella.gui.notify;

import javax.swing.Action;
import javax.swing.Icon;

/**
 * Represents a notification. A notification must have a message and can
 * optionally have an icon and associated actions.
 */
public class Notification {

    private String message;

    private Action[] actions;

    private Icon icon;

    private boolean verticleActionOrientation = false;
    
    public Notification(String message, Icon icon, Action... actions) {
        this.message = message;
        this.icon = icon;
        this.actions = actions;
    }

    public Notification(String message, Icon icon, 
            boolean useVerticleActionOrientation, Action... actions) {
        
        this(message, icon, actions);
        
        verticleActionOrientation = true;
    }
    
    public Notification(String message, Icon icon) {
        this(message, icon, (Action[]) null);
    }

    public Notification(String message) {
        this(message, null, (Action[]) null);
    }

    public String getMessage() {
        return message;
    }

    public Action[] getActions() {
        return actions;
    }

    public Icon getIcon() {
        return icon;
    }
    
    public boolean useVerticleActionOrientation() {
        return verticleActionOrientation;
    }

}
