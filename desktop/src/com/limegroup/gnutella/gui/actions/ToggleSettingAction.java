package com.limegroup.gnutella.gui.actions;

import org.limewire.setting.BooleanSetting;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Toggles a boolean setting.
 * <p>
 * Note, does not update the button state if the setting is changed elsewhere. Also,
 * button has to be initialized with the correct value.
 * <p>
 * Setting is interpreted positively, if button is selected, setting is set to true.
 */
public class ToggleSettingAction extends AbstractAction {
    /**
     *
     */
    private static final long serialVersionUID = -7756628550520833921L;
    private final BooleanSetting setting;

    protected ToggleSettingAction(BooleanSetting setting, String name) {
        this(setting, name, null);
    }

    protected ToggleSettingAction(BooleanSetting setting, String name, String description) {
        super(name);
        putValue(LONG_DESCRIPTION, description);
        this.setting = setting;
    }

    public BooleanSetting getSetting() {
        return setting;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof AbstractButton) {
            boolean selected = ((AbstractButton) e.getSource()).isSelected();
            setting.setValue(selected);
        } else {
            throw new IllegalArgumentException("toggle action can only used with abstract buttons");
        }
    }
}
