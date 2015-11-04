package com.limegroup.gnutella.gui.options.panes;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.shell.LimeAssociationOption;
import com.limegroup.gnutella.gui.shell.FrostAssociations;
import com.limegroup.gnutella.settings.QuestionsHandler;

/**
 * This class defines the panel in the options window that allows the user
 * to choose if FrostWire should open magnet: links and .torrent files.
 */
public final class AssociationPreferencePaneItem extends AbstractPaneItem {

    public final static String TITLE = I18n.tr("Links and File Types");
    
    public final static String LABEL = I18n.tr("You can use FrostWire to open certain filetypes and protocols. You can also instruct FrostWire to always regain these associations if another program takes them.");

	/** a mapping of checkboxes to associations */
	private Map<JCheckBox, LimeAssociationOption> associations =
		new HashMap<JCheckBox, LimeAssociationOption>();

	/** Check box to check associations on startup. */
	private JRadioButton always, never, ask;

	/**
	 * The constructor constructs all of the elements of this 
	 * <tt>AbstractPaneItem</tt>.
	 *
	 * @param key the key for this <tt>AbstractPaneItem</tt> that the
	 *            superclass uses to generate locale-specific keys
	 */
	public AssociationPreferencePaneItem() {
		super(TITLE, LABEL);
        
		Iterable<LimeAssociationOption> options = FrostAssociations.getSupportedAssociations();
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(5, 0, 2, 0);
        panel.add(new JLabel(I18n.tr("Use FrostWire for...")), gbc);
        
        gbc.insets = new Insets(1, 4, 1, 0);
        for(LimeAssociationOption option : options) {
            JCheckBox box = new JCheckBox(option.getDescription());
            associations.put(box, option);
            panel.add(box, gbc);
        }
        
        gbc.insets = new Insets(9, 0, 2, 0);
        panel.add(new JLabel(I18n.tr("What should FrostWire do with the selected associations on startup?")), gbc);				
		int value = QuestionsHandler.GRAB_ASSOCIATIONS.getValue();
		always = new JRadioButton(I18n.tr("Always take the selected associations."),
				value == DialogOption.YES.toInt());
		never = new JRadioButton(I18n.tr("Ignore all missing associations."),
				value == DialogOption.NO.toInt());
		ask = new JRadioButton(I18n.tr("Ask me what to do when an association is missing."),
		        DialogOption.parseInt(value) != DialogOption.YES && DialogOption.parseInt(value) != DialogOption.NO);
		ButtonGroup grabGroup = new ButtonGroup();
		grabGroup.add(always);
		grabGroup.add(ask);
		grabGroup.add(never);
        
        gbc.insets = new Insets(1, 4, 1, 0);
        panel.add(always, gbc);
        panel.add(ask, gbc);
        panel.add(never, gbc);
        
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridheight = GridBagConstraints.REMAINDER;
        panel.add(Box.createGlue(), gbc);
        add(panel);
	}

	/**
	 * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
	 *
	 * Sets the options for the fields in this <tt>PaneItem</tt> when the 
	 * window is shown.
	 */
	public void initOptions() {
		for (JCheckBox box : associations.keySet()) 
			box.setSelected(associations.get(box).isEnabled());
		DialogOption choice = DialogOption.parseInt(QuestionsHandler.GRAB_ASSOCIATIONS.getValue());
        switch (choice) {
        case YES:
            always.setSelected(true);
            break;
        case NO:
            never.setSelected(true);
            break;
        default:
            ask.setSelected(true);
        }
    }

	/**
	 * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
	 *
	 * Applies the options currently set in this window, displaying an
	 * error message to the user if a setting could not be applied.
	 *
	 * @throws IOException if the options could not be applied for some reason
	 */
	public boolean applyOptions() throws IOException {
		for (Map.Entry<JCheckBox, LimeAssociationOption>entry : associations.entrySet()) {
			LimeAssociationOption option = entry.getValue();
			if (entry.getKey().isSelected()) {
				option.setAllowed(true);
				option.setEnabled(true);
			} else {
				// only disallow options that were previously enabled.
				if (option.isEnabled())
					option.setAllowed(false);
				option.setEnabled(false);
			}
		}
		
		DialogOption value = DialogOption.INVALID;
		if (always.isSelected())
			value = DialogOption.YES;
		else if (never.isSelected())
			value = DialogOption.NO;
		QuestionsHandler.GRAB_ASSOCIATIONS.setValue(value.toInt());
        return false;
	}

    public boolean isDirty() {
    	for (Map.Entry<JCheckBox, LimeAssociationOption>option : associations.entrySet()) {
    		if (option.getKey().isSelected() != option.getValue().isEnabled())
    			return true;
    	}
    	
    	switch (DialogOption.parseInt(QuestionsHandler.GRAB_ASSOCIATIONS.getValue())) {
        case YES:
            return !always.isSelected();
        case NO:
            return !never.isSelected();
        default:
            return !ask.isSelected();
        }
    }
}
