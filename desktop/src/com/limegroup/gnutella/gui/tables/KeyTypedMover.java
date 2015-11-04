package com.limegroup.gnutella.gui.tables;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.table.TableModel;
import javax.swing.text.Position;

/**
 * Listener to key-typed events, to move focus to the closest possible match
 * in the model.
 *
 * This works exactly like (and is modelled off of) JList's BasicListUI's
 * Handler.
 */
public class KeyTypedMover implements KeyListener {
    
	private String prefix = "";
	private String typedString = "";
	private long lastTime = 0L;
	
    /**
     * The time factor to treate the series of typed alphanumeric key
     * as prefix for first letter navigation.
     */
    private final long timeFactor = 500L;
    	
	/**
	 * Invoked when a key has been typed.
	 * 
	 * Moves the keyboard focus to the first element whose prefix matches the
	 * sequence of alphanumeric keys pressed by the user with delay less
	 * than value of <code>timeFactor</code>.
	 * Subsequent same key presses move the keyboard
	 * focus to the next object that starts with the same letter until another
	 * key is pressed, then it is treated as the prefix with appropriate number
	 * of the same letters followed by first typed anothe letter.
	 */
	public void keyTyped(KeyEvent e) {
	    LimeJTable src = (LimeJTable)e.getSource();
	    TableModel model = src.getModel();
	    
        if (model.getRowCount() == 0 ||
          e.isAltDown() || e.isControlDown() || e.isMetaDown() ||
	      isNavigationKey(e)) {
            // Nothing to select
            return;
        }
        
	    boolean startingFromSelection = true;
	    char c = e.getKeyChar();
	    long time = e.getWhen();
	    int startIndex = src.getSelectionModel().getLeadSelectionIndex();
	    
	    if (time - lastTime < timeFactor) {
    		typedString += c;
       		if((prefix.length() == 1) && (c == prefix.charAt(0))) {
       		    // Subsequent same key presses move the keyboard focus to the next 
       		    // object that starts with the same letter.
       		    startIndex++;
     		} else {
     		    prefix = typedString;
     		}
        } else {
            startIndex++;
    		typedString = "" + c;
       		prefix = typedString;
	    }
	    lastTime = time;

	    if (startIndex < 0 || startIndex >= model.getRowCount()) {
		    startingFromSelection = false;
		    startIndex = 0;
	    }
	    int index = src.getNextMatch(prefix, startIndex, Position.Bias.Forward);
	    if (index >= 0) {
	        src.setSelectedRow(index);
    		src.ensureRowVisible(index);
	    } else if (startingFromSelection) { // wrap
		    index = src.getNextMatch(prefix, 0, Position.Bias.Forward);
    		if (index >= 0) {
    	        src.setSelectedRow(index);
        		src.ensureRowVisible(index);
    		}
	    }
	}
	
	/**
	 * Invoked when a key has been pressed.
	 *
	 * Checks to see if the key event is a navigation key to prevent
	 * dispatching these keys for the first letter navigation.
	 */
	public void keyPressed(KeyEvent e) {
	    if ( isNavigationKey(e) ) {
    		prefix = "";
		    typedString = "";
		    lastTime = 0L;
	    }
	}

	public void keyReleased(KeyEvent e) {}

	/**
	 * Returns whether or not the supplied key event maps to a key that is used for
	 * navigation.  This is used for optimizing key input by only passing non-
	 * navigation keys to the first letter navigation mechanism.
	 */
	private boolean isNavigationKey(KeyEvent event) {
	    InputMap inputMap = 
	        ((JComponent)event.getSource()).getInputMap(
	            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
	    KeyStroke key = KeyStroke.getKeyStrokeForEvent(event);
        return (inputMap != null && inputMap.get(key) != null);
	}  
}		