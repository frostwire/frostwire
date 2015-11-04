package org.limewire.setting;

import java.util.ArrayList;
import java.util.Collection;

import org.limewire.setting.evt.SettingsGroupEvent;
import org.limewire.setting.evt.SettingsGroupListener;
import org.limewire.setting.evt.SettingsGroupEvent.EventType;

/**
 * An abstract implementation of SettingsGroup
 */
public abstract class AbstractSettingsGroup implements SettingsGroup {
    
    /**
     * List of {@link SettingsGroupListener}s
     */
    private Collection<SettingsGroupListener> listeners;
    
    /**
     * Value for whether or not settings should be saved to file.
     */
    private volatile boolean shouldSave = true;
    
    /* (non-Javadoc)
     * @see org.limewire.setting.SettingsGroup#addSettingsGroupListener(org.limewire.setting.evt.SettingsGroupListener)
     */
    public void addSettingsGroupListener(SettingsGroupListener l) {
        if (l == null) {
            throw new NullPointerException("SettingsGroupListener is null");
        }
        
        synchronized (this) {
            if (listeners == null) {
                listeners = new ArrayList<SettingsGroupListener>();
            }
            listeners.add(l);
        }        
    }
    
    /* (non-Javadoc)
     * @see org.limewire.setting.SettingsGroup#removeSettingsGroupListener(org.limewire.setting.evt.SettingsGroupListener)
     */
    public void removeSettingsGroupListener(SettingsGroupListener l) {
        if (l == null) {
            throw new NullPointerException("SettingsGroupListener is null");
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
     * Returns all {@link SettingsGroupListener}s or null if there are none
     */
    public SettingsGroupListener[] getSettingsGroupListeners() {
        synchronized (this) {
            if (listeners == null) {
                return null;
            }
            
            return listeners.toArray(new SettingsGroupListener[0]);
        }
    }
    
    /* (non-Javadoc)
     * @see org.limewire.setting.SettingsGroup#setShouldSave(boolean)
     */
    public void setShouldSave(boolean shouldSave) {
        if (this.shouldSave != shouldSave) {
            this.shouldSave = shouldSave;
            fireSettingsEvent(EventType.SHOULD_SAVE);
        }
    }
    
    /* (non-Javadoc)
     * @see org.limewire.setting.SettingsGroup#getShouldSave()
     */
    public boolean getShouldSave() {
        return shouldSave;
    }
    
    /**
     * Fires a SettingsEvent
     */
    protected void fireSettingsEvent(EventType type) {
        fireSettingsEvent(new SettingsGroupEvent(type, this));
    }
    
    /**
     * Fires a SettingsEvent
     */
    protected void fireSettingsEvent(final SettingsGroupEvent evt) {
        if (evt == null) {
            throw new NullPointerException("SettingsEvent is null");
        }
        
        final SettingsGroupListener[] listeners = getSettingsGroupListeners();
        if (listeners != null) {
            Runnable command = new Runnable() {
                public void run() {
                    for (SettingsGroupListener l : listeners) {
                        l.settingsGroupChanged(evt);
                    }
                }
            };
            
            SettingsGroupManager.instance().execute(command);
        }
    }
}
