/**
 * Code taken freely from
 * http://www.java-engineer.com/java/auto-complete.html
 */
 
//------------------------------------------------------------------------------
// Copyright (c) 1999-2001 Matt Welsh.  All Rights Reserved.
//------------------------------------------------------------------------------
package com.limegroup.gnutella.gui;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.text.Document;

import org.limewire.collection.AutoCompleteDictionary;
import org.limewire.collection.StringTrieSet;

import com.limegroup.gnutella.settings.UISettings;

/**
 *
 * @author Matt Welsh (matt@matt-welsh.com)
 *
 * @modified Sam Berlin
 *      .. to not implement AutoComplete, not take
 *      the dictionary in the constructor, allow the Dictionary
 *      and listeners to be lazily created, and update the dictionary at will.
 * 
 */
public class AutoCompleteTextField extends KeyProcessingTextField {

    public AutoCompleteTextField() { super(); }
    public AutoCompleteTextField(Document a, String b, int c) {super(a, b, c);}
    public AutoCompleteTextField(int a) { super(a); }
    public AutoCompleteTextField(String a) { super(a); }
    public AutoCompleteTextField(String a, int b) { super(a, b); }
    
    //----------------------------------------------------------------------------
    // Public methods
    //----------------------------------------------------------------------------
    
    /**
    * Set the dictionary that autocomplete lookup should be performed by.
    *
    * @param dict The dictionary that will be used for the autocomplete lookups.
    */
    public void setDictionary(AutoCompleteDictionary dict) {
        // lazily create the listeners
        if ( this.dict == null ) setUp();
        this.dict = dict;
    }

    /**
    * Gets the dictionary currently used for lookups.
    *
    * @return dict The dictionary that will be used for the autocomplete lookups.
    */
    public AutoCompleteDictionary getDictionary() {
        return dict;
    }

    /**
    * Creates the default dictionary object
    */
    public AutoCompleteDictionary createDefaultDictionary() {
        return new StringTrieSet(true);
    }
    
    /**
    * Sets whether the component is currently performing autocomplete lookups as
    * keystrokes are performed.
    *
    * @param val True or false.
    */
    public void setAutoComplete(boolean val) {
        UISettings.AUTOCOMPLETE_ENABLED.setValue(val);
    }
    
    /**
    * Gets whether the component is currently performing autocomplete lookups as
    * keystrokes are performed. Looks up the value in UISettings.
    *
    * @return True or false.
    */
    public boolean getAutoComplete() {
        return UISettings.AUTOCOMPLETE_ENABLED.getValue();
    }

    /**
    * Adds the current value of the field underlying dictionary
    */
    public void addToDictionary() {
        if( !getAutoComplete() ) return;

        if ( dict == null ) {
            setUp();
            this.dict = createDefaultDictionary();
        }
        dict.addEntry(getText().trim());
    }
    
    /**
     * Adds the specified string to the underlying dictionary
     */
    public void addToDictionary(String s) {
        if( !getAutoComplete() ) return;

        if ( dict == null ) {
            setUp();
            this.dict = createDefaultDictionary();
        }
        dict.addEntry(s.trim());
    }
    
    //----------------------------------------------------------------------------
    // Protected methods
    //----------------------------------------------------------------------------
    protected void setUp() {
        addKeyListener( new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                char charPressed = e.getKeyChar();
                int charCodePressed = e.getKeyCode();
      
                if (charCodePressed == KeyEvent.VK_DELETE ||
                  charPressed == KeyEvent.CHAR_UNDEFINED ||
                  charCodePressed == KeyEvent.VK_BACK_SPACE ) {
                    return;
                }
                autoCompleteInput();
            }
        });
    }
    
    public void autoCompleteInput() {
        // this part theoretically should be handled by the
        // JTextField itself, but for some reason it gets
        // confused during fast typing.
        if (getSelectionStart() != getSelectionEnd()) {
            setText(getText().substring(0, getSelectionStart()));
        }
  
        final String input = getText();
        final String newText = lookup(input);
        int length = input.length();
        if(newText != null && length < newText.length()) {
            setText( input + newText.substring(length) );
            setSelectionStart(input.length());
            setSelectionEnd(getText().length());
        }
    }
    
    protected String lookup(String s) {
        if(dict != null && getAutoComplete() && !s.equals(""))
            return dict.lookup(s);
        return null;
    }

    //----------------------------------------------------------------------------
    // Fields
    //----------------------------------------------------------------------------
    protected AutoCompleteDictionary dict;
}

