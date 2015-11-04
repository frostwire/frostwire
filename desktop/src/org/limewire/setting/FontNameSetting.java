package org.limewire.setting;

import java.util.Properties;


/**
 * Provides a font name setting value. As a subclass of 
 * <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>FontNameSetting</code> object with a 
 * {@link SettingsFactory#createFontNameSetting(String, String)}.
 */

 /* TODO: look into creating a true 'FontSetting' that keeps a Font
 * object rather than just the name.  This will require changes
 * to the themes.txt format since right now it has three properties
 * (name, style, size) that define a single font.
 */
public final class FontNameSetting extends AbstractSetting {
   
    private String _fontName;

    FontNameSetting(Properties defaultProps, Properties props, String key,
                                                           String defaultStr) {
        super(defaultProps, props, key, defaultStr);
        _fontName = defaultStr;
    }
    /**
      * @param fontName
      */
    public void setValue(String fontName) {
        setValueInternal(fontName);
    }

    public String getValue() {
        return _fontName;
    }
    
    /**
     * Most of the theme files have a font (like Verdana)
     * specified that can not display languages other than
     * those using Roman alphabets. Therefore, if the locale 
     * is determined not to be one that uses a Roman alphabet 
     * then do not set _fontName.  The variable _fontName
     * is set to the default (dialog) in the constructor.
     */
    protected void loadValue(String sValue) {
        _fontName = sValue;
    }
}
