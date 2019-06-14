package org.limewire.setting;

import org.limewire.setting.evt.SettingEvent;
import org.limewire.setting.evt.SettingEvent.EventType;
import org.limewire.setting.evt.SettingListener;

import java.util.Collection;
import java.util.Properties;

/**
 * Provides a key-value property as an abstract class. The value is typed
 * in subclasses to avoid casting and ensure your settings are type-safe.
 * The value has a unique key.
 * <p>
 * When you add a new <code>AbstractSetting</code> subclass, add a public synchronized
 * method to {@link SettingsFactory} to create an instance of the setting.
 * For example, subclass {@link IntSetting}, <code>SettingsFactory</code> has
 * {@link SettingsFactory#createIntSetting(String, int)} and
 * <p>
 * <code>AbstractSetting</code> includes an abstract method to load a <code>String</code>
 * into the key-value property. You are responsible to convert the
 * <code>String</code> to the appropriate type in a subclass.
 * <p>
 * For example, if your subclass is for an integer setting you can
 * have a integer field i.e. <code>myIntValue</code>, in the class. Then you
 * would set <code>myIntValue</code> with the integer converted
 * <code>String<code> argument, for example:
 * <pre>
 * protected void loadValue(String sValue) {
 * try {
 * value = Integer.parseInt(sValue.trim());
 * } catch(NumberFormatException nfe) {
 * revertToDefault();
 * }
 * }
 * </pre>
 * <p>
 * This class, includes fields for the <code>AbstractSetting</code>'s visibility
 * (public vs. private) and persistence (always save vs don't save).
 * <p>
 * Visibility and persistence are just fields for a property; what the field
 * means to your application is up to you. For example, you could give the
 * setting a "don't save" value and when it's time to store the setting to a
 * database, you check the setting and take appropriate actions.
 * <p>
 * See {@link SettingsFactory} for an example of creating an
 * <code>IntSetting</code> object which is a
 * subclass of <code>AbstractSetting</code> . Additionally the
 * example shows how to load and save the setting to disk.
 */
public abstract class AbstractSetting implements Setting {
    /**
     * Constant for the default value for this <tt>Setting</tt>.
     */
    final String DEFAULT_VALUE;
    /**
     * Protected default <tt>Properties</tt> instance for subclasses.
     */
    private final Properties DEFAULT_PROPS;
    /**
     * Protected <tt>Properties</tt> instance containing properties for any
     * subclasses.
     */
    private final Properties PROPS;
    /**
     * The constant key for this property, specified upon construction.
     */
    private final String KEY;
    /**
     * Value for whether or not this setting should always save.
     */
    private boolean alwaysSave = false;
    /**
     * Setting for whether or not this setting is private and should
     * not be reported in bug reports.
     */
    private boolean isPrivate = false;
    /**
     * List of {@link SettingListener}
     */
    private Collection<SettingListener> listeners = null;

    /**
     * Constructs a new setting with the specified key and default
     * value.  Private access ensures that only this class can construct
     * new <tt>Setting</tt>s.
     *
     * @param key          the key for the setting
     * @param defaultValue the defaultValue for the setting
     *                     setting is already contained in the map of default settings
     */
    AbstractSetting(Properties defaultProps, Properties props, String key,
                    String defaultValue) {
        DEFAULT_PROPS = defaultProps;
        PROPS = props;
        KEY = key;
        DEFAULT_VALUE = defaultValue;
        if (DEFAULT_PROPS.containsKey(key))
            throw new IllegalArgumentException("(AbstractSetting constructor) duplicate setting key: " + key);
        DEFAULT_PROPS.put(KEY, defaultValue);
        loadValue(defaultValue);
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#getSettingListeners()
     */
    public SettingListener[] getSettingListeners() {
        synchronized (this) {
            if (listeners == null) {
                return null;
            }
            return listeners.toArray(new SettingListener[0]);
        }
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#reload()
     */
    public void reload() {
        String value = PROPS.getProperty(KEY);
        if (value == null) {
            value = DEFAULT_VALUE;
        }
        // Ensure that PROPS is always backed with the default value.
        // This is necessary for saving the PROPS file, as the backing
        // default map isn't serialized with the properties.
        // So any defaults that want to be "always saved" need to
        // be explicitly inserted.
        if (isDefault()) {
            PROPS.setProperty(KEY, DEFAULT_VALUE);
        }
        loadValue(value);
        fireSettingEvent(EventType.RELOAD);
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#revertToDefault()
     */
    public boolean revertToDefault() {
        if (!isDefault()) {
            setValueInternal(DEFAULT_VALUE);
            fireSettingEvent(EventType.REVERT_TO_DEFAULT);
            return true;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#shouldAlwaysSave()
     */
    public boolean shouldAlwaysSave() {
        return alwaysSave;
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#setAlwaysSave(boolean)
     */
    public AbstractSetting setAlwaysSave(boolean alwaysSave) {
        if (this.alwaysSave != alwaysSave) {
            this.alwaysSave = alwaysSave;
            fireSettingEvent(EventType.ALWAYS_SAVE_CHANGED);
        }
        return this;
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#setPrivate(boolean)
     */
    public Setting setPrivate(boolean isPrivate) {
        if (this.isPrivate != isPrivate) {
            this.isPrivate = isPrivate;
            fireSettingEvent(EventType.PRIVACY_CHANGED);
        }
        return this;
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#isPrivate()
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#isDefault()
     */
    public boolean isDefault() {
        String value = PROPS.getProperty(KEY);
        if (value == null)
            return true;
        return value.equals(DEFAULT_PROPS.getProperty(KEY));
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#getKey()
     */
    public String getKey() {
        return KEY;
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.Setting#getValueAsString()
     */
    public String getValueAsString() {
        String prop = PROPS.getProperty(KEY);
        return prop == null ? DEFAULT_VALUE : prop;
    }

    /**
     * Set new property value
     *
     * @param value new property value
     *              <p>
     *              NOTE: This is protected so that only this package
     *              can update all kinds of settings using a String value.
     *              StringSetting updates the access to public.
     */
    void setValueInternal(String value) {
        String old = PROPS.getProperty(KEY);
        if (old == null || !old.equals(value)) {
            PROPS.setProperty(KEY, value);
            loadValue(value);
            fireSettingEvent(EventType.VALUE_CHANGED);
        }
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    abstract protected void loadValue(String sValue);

    public String toString() {
        return KEY + "=" + getValueAsString();
    }

    /**
     * Fires a SettingEvent
     */
    private void fireSettingEvent(EventType type) {
        fireSettingEvent(new SettingEvent(type, this));
    }

    /**
     * Fires a SettingEvent
     */
    private void fireSettingEvent(final SettingEvent evt) {
        if (evt == null) {
            throw new NullPointerException("SettingEvent is null");
        }
        final SettingListener[] listeners = getSettingListeners();
        if (listeners != null) {
            Runnable command = () -> {
                for (SettingListener l : listeners) {
                    l.settingChanged(evt);
                }
            };
            SettingsGroupManager.instance().execute(command);
        }
    }

    /**
     * Returns the default value
     */
    Object getDefaultValue() {
        return DEFAULT_PROPS.getProperty(KEY);
    }
}
