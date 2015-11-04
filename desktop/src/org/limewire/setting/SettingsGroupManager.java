package org.limewire.setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.setting.evt.SettingsGroupManagerEvent;
import org.limewire.setting.evt.SettingsGroupManagerListener;
import org.limewire.setting.evt.SettingsGroupManagerEvent.EventType;


/**
 * Groups all {@link SettingsGroup} objects in one location to reload, revert to
 * a default value, save, or mark as save-able all <code>Settings</code> 
 * objects at once.
 */
public final class SettingsGroupManager {
    
    /**
     * The singleton instance of SettingsHandler
     */
    private static final SettingsGroupManager INSTANCE = new SettingsGroupManager();
    
    /**
     * Returns the singleton instance of the SettingsHandler
     */
    public static SettingsGroupManager instance() {
        return INSTANCE;
    }
    
    /**
     * A list of Settings this SettingsHandler is managing
     */
    private final Collection<SettingsGroup> PROPS = Collections.synchronizedList(new ArrayList<SettingsGroup>());

    /**
     * A list of {@link SettingsGroupManagerListener}s
     */
    private Collection<SettingsGroupManagerListener> listeners;
    
    /**
     * The Executor for the Events
     */
    private volatile Executor executor = ExecutorsHelper.newFixedSizeThreadPool(1, "SettingsHandlerEventDispatcher");
    
    // never instantiate this class.
    private SettingsGroupManager() {}
    
    /**
     * Registers a {@link SettingsGroupManagerListener}
     */
    public void addSettingsHandlerListener(SettingsGroupManagerListener l) {
        if (l == null) {
            throw new NullPointerException("SettingsGroupManagerListener is null");
        }
        
        synchronized (this) {
            if (listeners == null) {
                listeners = new ArrayList<SettingsGroupManagerListener>();
            }
            listeners.add(l);
        }        
    }
    
    /**
     * Removes a {@link SettingsGroupManagerListener}
     */
    public void removeSettingsHandlerListener(SettingsGroupManagerListener l) {
        if (l == null) {
            throw new NullPointerException("SettingsGroupManagerListener is null");
        }
        
        synchronized (this) {
            if (listeners != null) {
                listeners.remove(l);
                
                if (listeners.isEmpty()) {
                    listeners = null;
                }
            }
        }
    }
    
    /**
     * Returns all {@link SettingsGroupManagerListener}s or null if there are none
     */
    public SettingsGroupManagerListener[] getSettingsHandlerListeners() {
        synchronized (this) {
            if (listeners == null) {
                return null;
            }
            
            return listeners.toArray(new SettingsGroupManagerListener[0]);
        }
    }
    
    /**
     * Adds a settings class to the list of factories that 
     * this handler will act upon.
     */
    public void addSettingsGroup(SettingsGroup group) {
        PROPS.add(group);
        fireSettingsHandlerEvent(EventType.SETTINGS_GROUP_ADDED, group);
    }
    
    /**
     * Removes a settings class from the list of factories that
     * this handler will act upon.
     */
    public void removeSettingsGroup(SettingsGroup group) {
        if (PROPS.remove(group)) {
            fireSettingsHandlerEvent(EventType.SETTINGS_GROUP_REMOVED, group);
        }
    }

    /**
     * Returns all {@link SettingsGroup}s that are currently registered
     */
    public SettingsGroup[] getSettingsGroups() {
        return PROPS.toArray(new SettingsGroup[0]);
    }
    
    /**
     * Reload settings from both the property and configuration files.
     */
    public void reload() {
        synchronized (PROPS) {
            for (SettingsGroup group : PROPS) {
                group.reload();
            }
        }
        
        fireSettingsHandlerEvent(EventType.RELOAD, null);
    }
    
    /**
     * Save property settings to the property file.
     */
    public boolean save() {
        boolean any = false;
        synchronized (PROPS) {
            for (SettingsGroup group : PROPS) {
                any |= group.save();
            }
        }
        
        if (any) {
            fireSettingsHandlerEvent(EventType.SAVE, null);
        }
        
        return any;
    }
    
    /**
     * Revert all settings to their default value.
     */
    public boolean revertToDefault() {
        boolean any = false;
        synchronized (PROPS) {
            for (SettingsGroup group : PROPS) {
                any |= group.revertToDefault();
            }
        }
        
        if (any) {
            fireSettingsHandlerEvent(EventType.REVERT_TO_DEFAULT, null);
        }
        
        return any;
    }
    
    /**
     * Mutator for shouldSave.
     */
    public void setShouldSave(boolean shouldSave) {
        synchronized (PROPS) {
            for (SettingsGroup group : PROPS) {
                group.setShouldSave(shouldSave);
            }
        }
        
        fireSettingsHandlerEvent(EventType.SHOULD_SAVE, null);
    }
    
    /**
     * Fires a SettingsHandlerEvent
     */
    protected void fireSettingsHandlerEvent(EventType type, SettingsGroup group) {
        fireSettingsHandlerEvent(new SettingsGroupManagerEvent(type, this, group));
    }
    
    /**
     * Fires a SettingsHandlerEvent
     */
    protected void fireSettingsHandlerEvent(final SettingsGroupManagerEvent evt) {
        if (evt == null) {
            throw new NullPointerException("SettingsHandlerEvent is null");
        }
        
        final SettingsGroupManagerListener[] listeners = getSettingsHandlerListeners();
        if (listeners != null) {
            Runnable command = new Runnable() {
                public void run() {
                    for (SettingsGroupManagerListener l : listeners) {
                        l.handleGroupManagerEvent(evt);
                    }
                }
            };
            
            execute(command);
        }
    }
    
    /**
     * Fires a event on the Executor Thread
     */
    protected void execute(Runnable evt) {
        executor.execute(evt);
    }
    
    /**
     * Replaces the current Executor
     */
    public void setExecutor(Executor executor) {
        if (executor == null) {
            throw new NullPointerException("Executor is null");
        }
        
        this.executor = executor;
    }
}    