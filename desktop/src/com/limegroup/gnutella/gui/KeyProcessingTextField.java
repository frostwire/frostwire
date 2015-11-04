package com.limegroup.gnutella.gui;

import java.awt.event.KeyEvent;

import javax.swing.text.Document;

public class KeyProcessingTextField extends LimeTextField {
    
    /**
     * 
     */
    private static final long serialVersionUID = -2092997448111879390L;

    public KeyProcessingTextField() {
        super();
    }
    
    public KeyProcessingTextField(String text) {
        super(text);
    }
    
    public KeyProcessingTextField(int columns) {
        super(columns);
    }
    
    public KeyProcessingTextField(String text, int columns) {
        super(text, columns);
    }
    
    public KeyProcessingTextField(Document doc, String text, int columns) {
        super(doc, text, columns);
    }
    
    // raise access
    public void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
    }
}