package com.limegroup.gnutella.gui.shell;

import org.limewire.setting.BooleanSetting;

/**
 * Links a ShellAssociation with FrostWire settings, name & description,
 * in order to have a single object that can display the association
 * and determine if it should be registered or not.
 */
public class LimeAssociationOption {
    /**
     * The association.
     */
    private final ShellAssociation association;
    /**
     * Whether or not to try automatically grabbing it.
     */
    private final BooleanSetting setting;
    /**
     * A short name & description
     */
    private final String name, description;

    /**
     * Constructs a new LimeAssociationOption linking a ShellAssociation
     * to a setting, short name & description.
     *
     * @param association The ShellAssociation
     * @param setting     The Setting to control whether it should be registered automatically
     * @param name        A short name of the association
     * @param description A description of the association
     */
    LimeAssociationOption(ShellAssociation association,
                          BooleanSetting setting,
                          String name, String description) {
        this.association = association;
        this.setting = setting;
        this.name = name;
        this.description = description;
    }

    /**
     * Determines if the association is currently registered to this program.
     */
    public boolean isEnabled() {
        return association.isRegistered();
    }

    /**
     * Either links or de-links the association to this program.
     * This does not effect the setting.
     */
    public void setEnabled(boolean enabled) {
        if (enabled) {
            if (!isAllowed())
                throw new IllegalStateException("cannot enable something that isn't allowed");
            association.unregister();
            association.register();
        } else if (association.isRegistered())
            association.unregister();
    }

    /**
     * Determines if the association is allowed to be enabled.
     */
    public boolean isAllowed() {
        return setting == null || setting.getValue();
    }

    /**
     * Either allows or disallows the program from registering the association.
     */
    public void setAllowed(boolean allow) {
        if (setting != null)
            setting.setValue(allow);
    }

    /**
     * Retrieves the short name of the association.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the long name of the association.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns true if the association is currently unhandled by any application.
     */
    public boolean isAvailable() {
        return association.isAvailable();
    }
}
