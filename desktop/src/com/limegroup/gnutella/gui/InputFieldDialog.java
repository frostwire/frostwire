package com.limegroup.gnutella.gui;

import java.awt.Container;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JDialog;


/**
 * This class creates a generic input field that gets a line of text input
 * from the user in a <tt>JDialog</tt>.  It gives the user "OK" and 
 * "Cancel" button options, and the constructor takes keys for locale-
 * specific strings that allow customization of the dialog caption as well
 * as the label on the input field.  
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class InputFieldDialog {
   
	/**
	 * The return code for when the user has pressed ok but not entered
	 * any text.
	 */
	public static final int NO_TEXT_ENTERED = 77;
	
	/**
	 * The return code for when the user cancelled the action.
	 */
	public static final int CANCELLED = 78;

	/**
	 * The return code for when the user has entered some text.
	 */
	public static final int TEXT_ENTERED = 79;

	/**
	 * The <tt>JDialog</tt> instance for the dialog window.
	 */
	private JDialog _dialog;

	/**
	 * The main panel of the dialog.
	 */
	private final PaddedPanel MAIN_PANEL = new PaddedPanel();

	/**
	 * The text field displayed in the dialog.
	 */
	private final SizedTextField TEXT_FIELD = new SizedTextField();

	/**
	 * Stored value for the return code specifying what action
	 * the user took.
	 */
	private int _returnCode = NO_TEXT_ENTERED;
 
	/**
	 * Constructs an input field with a generic caption and a specialized
	 * label for the field.
	 *
	 * @param LABEL_KEY the key for the locale-specific label of the field
	 */
	public InputFieldDialog(final String LABEL_KEY) {
		this(I18n.tr("Input"), LABEL_KEY);
	}

	/**
	 * Constructs an input field using the specified locale-specific keys
	 * for both the caption and the field label.
	 *
	 * @param CAPTION_KEY the key for the locale-specific dialog caption
	 * @param LABEL_KEY the key for the locale-specific label of the field
	 */
	public InputFieldDialog(final String CAPTION_KEY,
							 final String LABEL_KEY) {
		String caption = I18n.tr(CAPTION_KEY);
		Frame frame = GUIMediator.getAppFrame();
		_dialog = new JDialog(frame, caption, true);
		_dialog.setSize(340, 180);
		LabeledComponent component = 
		    new LabeledComponent(LABEL_KEY, TEXT_FIELD, 
								 LabeledComponent.LEFT_GLUE);
		
		String[] buttonLabelKeys = {
			I18n.tr("OK"),
			I18n.tr("Cancel")
		};

		String[] buttonLabelTips = {
			I18n.tr("Apply Operation"),
			I18n.tr("Cancel Operation")
		};

		ActionListener[] buttonListeners = {
			new OKListener(),
			new CancelListener()
		};
		
		ButtonRow buttons = new ButtonRow(buttonLabelKeys,
										  buttonLabelTips,
										  buttonListeners,
										  ButtonRow.X_AXIS,
										  ButtonRow.LEFT_GLUE);
		Container contentPane = _dialog.getContentPane();
		BoxPanel componentPanel = new BoxPanel(BoxPanel.Y_AXIS);

		componentPanel.add(Box.createVerticalGlue());
		componentPanel.add(component.getComponent());
		componentPanel.add(Box.createVerticalGlue());

		MAIN_PANEL.add(componentPanel);
		MAIN_PANEL.add(Box.createVerticalGlue());
		MAIN_PANEL.add(buttons);
		contentPane.add(MAIN_PANEL);
	}

	/**
	 * Displays the modal dialog, returning the appropriate return code to
	 * the caller. The return code can be one of the following:
	 *
	 * NO_TEXT_ENTERED
	 * CANCELLED
	 * TEXT_ENTERED<p>
	 *
	 * @return the return code for the window
	 */
	public int showDialog() {
		setVisible(true);
		return _returnCode;
	}

	/**
	 * Makes the dialog visible or invisible depending on the parameter.
	 *
	 * @param visible specifies whether the dialog should be made visible or
	 *  invisible
	 */
	private void setVisible(boolean visible) {
		if(visible) {
			_dialog.setLocationRelativeTo(GUIMediator.getAppFrame());
		}
		_dialog.setVisible(visible);
	}

	/**
	 * Returns the text contained in the wrapped text field.
	 *
	 * @return the text contained in the text field
	 */
	public String getText() {
		return TEXT_FIELD.getText();
	}


	/**
	 * Returns the return code of the input window.  This can be one of:<p>
	 * 
	 * NO_TEXT_ENTERED
	 * CANCELLED
	 * TEXT_ENTERED<p>
	 *
	 * @return the return code for the window
	 */
	//private int getReturnCode() {
	//return _returnCode;
	//}

	/**
	 * This class handles the clicking of the ok button in the dialog.
	 */
	private class OKListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			if(TEXT_FIELD.getText().equals(""))
				_returnCode = NO_TEXT_ENTERED;
			else
				_returnCode = TEXT_ENTERED;
			setVisible(false);
		}
	}

	/**
	 * This class handles the clicking of the cancel button in the dialog.
	 */
	private class CancelListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			_returnCode = CANCELLED;
			setVisible(false);
		}
	}
}
