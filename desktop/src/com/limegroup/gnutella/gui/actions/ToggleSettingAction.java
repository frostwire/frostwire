package com.limegroup.gnutella.gui.actions;

import java.awt.event.ActionEvent;

import javax.swing.AbstractButton;

import org.limewire.setting.BooleanSetting;

/**
 * Toggles a boolean setting.
 * 
 * Note, does not update the button state if the setting is changed elsewhere. Also,
 * button has to be initialized with the correct value.
 * 
 * Setting is interpreted positively, if button is selected, setting is set to true.
 */
public class ToggleSettingAction extends AbstractAction {

    /**
     * 
     */
    private static final long serialVersionUID = -7756628550520833921L;
    
    private final BooleanSetting setting;
    
    public ToggleSettingAction(BooleanSetting setting, String name) { 
        this(setting, name, null);
    }
    
    public ToggleSettingAction(BooleanSetting setting, String name, String description) {
        super(name);
        putValue(LONG_DESCRIPTION, description);
        this.setting = setting;
    }
    
    public BooleanSetting getSetting() {
        return setting;
    }
    
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof AbstractButton) {
            boolean selected = ((AbstractButton)e.getSource()).isSelected();
            setting.setValue(selected);
        }
        else {
            throw new IllegalArgumentException("toggle action can only used with abstract buttons");
        }
    }

}
