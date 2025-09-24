package org.limewire.setting;

import com.frostwire.service.Switch;

/**
 * Provides a boolean setting value. As a subclass of
 * <code>Setting</code>, the setting has a key.
 * <p>
 * You can create a <code>BooleanSetting</code> object with a
 * {@link SettingsFactory#createBooleanSetting(String, boolean)}.
 */
public interface BooleanSetting extends Setting, Switch {
    /**
     * Returns the value of this setting.
     *
     * @return the value of this setting
     */
    boolean getValue();

    /**
     * Mutator for this setting.
     *
     * @param bool the `boolean` to store
     */
    void setValue(boolean bool);

    /**
     * Inverts the value of this setting.  If it was true,
     * sets it to false and vice versa.
     */
    void invert();
}