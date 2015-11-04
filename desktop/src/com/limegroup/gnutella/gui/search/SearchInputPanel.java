package com.limegroup.gnutella.gui.search;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import com.limegroup.gnutella.gui.BoxPanel;

/**
 * Inner panel that switches between the various kinds of
 * searching.
 */
class SearchInputPanel extends JPanel {

    SearchInputPanel() {

        createDefaultSearchPanel();

        setBorder(BorderFactory.createEmptyBorder(0, 3, 5, 2));
    }
    
    /**
     * Creates the default search input of:
     *    Filename
     *    [   input box  ]
     */
    private void createDefaultSearchPanel() {
        setLayout(new BoxLayout(this, BoxPanel.Y_AXIS));;
        add(createSearchButtonPanel());
    }

    /**
     * Creates the search button & inserts it in a panel.
     */
    private JPanel createSearchButtonPanel() {

        //The Search Button on a row of it's own
        JPanel b = new JPanel(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 10, 10, 0);


        JPanel filterLabelIconPanel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 10, 0, 0);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1.0;
        c.insets = new Insets(0, 3, 0, 0);

        
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.gridy = 0;
        c.gridy = 1;
        c.weightx = 1.0;
        c.insets = new Insets(10, 0, 10, 0);
        b.add(filterLabelIconPanel, c);

        return b;
    }
}
