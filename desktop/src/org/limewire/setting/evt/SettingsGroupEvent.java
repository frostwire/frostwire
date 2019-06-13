package org.limewire.setting.evt;

import org.limewire.setting.SettingsGroup;

/**
 * SettingsEvent are fired when a {@link SettingsGroup} instance changed
 */
public class SettingsGroupEvent {
    /**
     * The type of the event
     */
    private final EventType type;
    /**
     * The {@link SettingsGroup} instance that created this event
     */
    private final SettingsGroup group;

    /**
     * Constructs a SettingsEvent
     *
     * @param type  The type of the event
     * @param group The {@link SettingsGroup} instance that triggered this event
     */
    public SettingsGroupEvent(EventType type, SettingsGroup group) {
        if (type == null) {
            throw new NullPointerException("EventType is null");
        }
        if (group == null) {
            throw new NullPointerException("SettingsGroup is null");
        }
        this.type = type;
        this.group = group;
    }

    public String toString() {
        return type + ": " + group;
    }

    /**
     * Various SettingsEvent that may occur
     */
    public enum EventType {
        /**
         * The Settings were saved
         */
        SAVE,
        /**
         * The Settings were reloaded
         */
        RELOAD,
        /**
         * The Settings were reverted back to default
         */
        REVERT_TO_DEFAULT,
        /**
         * The 'should save' state of the Settings changed
         */
        SHOULD_SAVE
    }
}
