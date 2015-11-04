package org.limewire.setting.evt;

import org.limewire.setting.SettingsGroup;
import org.limewire.setting.SettingsGroupManager;

/**
 * SettingsHandlerEvent are fired when a {@link SettingsGroupManager} instance changed 
 */
public class SettingsGroupManagerEvent {
    
    /**
     * Various types of events that may occur
     */
    public static enum EventType {
        
        /**
         * Fired when Settings were added to the handler
         */
        SETTINGS_GROUP_ADDED,
        
        /**
         * Fired when Settings were removed from the handler
         */
        SETTINGS_GROUP_REMOVED,
        
        /**
         * Fired when all Settings were reloaded
         */
        RELOAD,
        
        /**
         * Fired when all Settings were saved
         */
        SAVE,
        
        /**
         * Fired when all Settings were revered back to
         * the default values
         */
        REVERT_TO_DEFAULT,
        
        /**
         * Fired when the should save flag was changed
         */
        SHOULD_SAVE;
    }
    
    private final EventType type;
    
    private final SettingsGroupManager manager;
    
    private final SettingsGroup group;
    
    /**
     * Constructs a SettingsHandlerEvent
     * 
     * @param type The type of the event
     * @param manager The handler that triggered this event
     * @param group The SettingsGroup instance that was added or removed (null in other cases)
     */
    public SettingsGroupManagerEvent(EventType type, SettingsGroupManager manager, SettingsGroup group) {
        if (type == null) {
            throw new NullPointerException("EventType is null");
        }
        
        if (manager == null) {
            throw new NullPointerException("SettingsGroupManager is null");
        }
        
        this.type = type;
        this.manager = manager;
        this.group = group;
    }
    
    /**
     * Returns the type of the event
     */
    public EventType getEventType() {
        return type;
    }
    
    /**
     * Returns the SettingsHandler instance that triggered this event
     */
    public SettingsGroupManager getSettingsManager() {
        return manager;
    }
    
    /**
     * The SettingsGroup instance that was added or removed. It's null in
     * all other cases
     */
    public SettingsGroup getSettingsGroup() {
        return group;
    }
    
    public String toString() {
        return type.toString();
    }
}
