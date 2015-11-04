package com.frostwire.gui.theme;

import javax.swing.Action;
import javax.swing.JRadioButtonMenuItem;

public class SkinRadioButtonMenuItem extends JRadioButtonMenuItem {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1832775757974178224L;

	public SkinRadioButtonMenuItem(Action a) {
        super(a);
    }
    
    public SkinRadioButtonMenuItem(String text, boolean b) {
        super(text, b);
    }
}
