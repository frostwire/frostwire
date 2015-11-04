package com.limegroup.gnutella.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;

/**
 * This is a convenience class that extends <tt>JPanel<tt> and gives the 
 * panel a padded border and gives it a <tt>BoxLayout</tt> instead of the 
 * default <tt>FlowLayout</tt>.
 */
public class PaddedPanel extends JPanel {

	/**
     * 
     */
    private static final long serialVersionUID = 4986909888220284988L;
    /**
	 * Stores an internal reference to the optional title label,
	 * whose text may be altered later by setTitle()
	 */
	private JLabel _titleLabel = null;

	/** 
	 * Constructor for a padded panel with the margins and the label text
	 * specified as parameters.
	 */
	public PaddedPanel(String label, int top, int left, 
					   int bottom, int right) {
        BoxLayout layout = new BoxLayout(this, BoxLayout.Y_AXIS);
        Border border = BorderFactory.createEmptyBorder(top, left, 
														bottom, right);
        setLayout(layout);
        setBorder(border);		

		if(label != null && !label.equals("")) {
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
			_titleLabel = new JLabel(label);
			panel.add(_titleLabel);
			panel.add(Box.createHorizontalGlue());
			add(panel);
		}

	}

	/** 
	 * Constructor for a padded panel that allows you to specify the borders 
	 * on all sides with no label 
	 */
	public PaddedPanel(int top, int left, int bottom, int right) {
		this("", top, left, bottom, right);
	}

    /** 
	 * The constructor defaults to BoxLayout oriented along the Y_AXIS. 
	 */
    public PaddedPanel(int padding) {
		this(padding, padding, padding, padding);
    }

	/** 
	 * Creates a panel with the specified label and the specified padding 
	 * on all sides. 
	 */
	public PaddedPanel(String label, int padding) {
		this(label, padding, padding, padding, padding);
	}

	/** 
	 * Convenience constructor that uses the default padding. 
	 */
	public PaddedPanel() {
		this(GUIConstants.OUTER_MARGIN);
	}

	/** 
	 * Convenience constructor that adds a label to the top of the panel. 
	 */
	public PaddedPanel(String label) {
		this(label, GUIConstants.OUTER_MARGIN);
	}

	/**
	 * Sets the title for the <tt>PaddedPanel</tt>.
	 *
	 * @param title the title to add
	 */
	public void setTitle(String title) {
		if (_titleLabel == null) {
			BoxPanel panel = new BoxPanel(BoxPanel.X_AXIS);
			_titleLabel = new JLabel(title);
			panel.add(_titleLabel);
			panel.add(Box.createHorizontalGlue());
			add(panel);
		} else {
			_titleLabel.setText(title);
		}
	}
}









