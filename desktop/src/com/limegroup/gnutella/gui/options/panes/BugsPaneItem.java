package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LimeWireModule;
import com.limegroup.gnutella.gui.LocalClientInfoFactory;
import com.limegroup.gnutella.gui.bugs.BugManager;
import com.limegroup.gnutella.gui.bugs.LocalClientInfo;
import com.limegroup.gnutella.settings.BugSettings;

import javax.swing.*;
import java.awt.*;

/**
 * This class defines the panel in the options window that allows
 * the user to handle bugs.
 */
public final class BugsPaneItem extends AbstractPaneItem {
    private final LocalClientInfoFactory localClientInfoFactory;
    /**
     * Checkbox for deadlock.
     */
    private final JCheckBox DEADLOCK_OPTION = new JCheckBox();
    /**
     * Radiobutton for sending
     */
    private final JRadioButton SEND_BOX = new JRadioButton();
    /**
     * Radiobutton for reviewing
     */
    private final JRadioButton REVIEW_BOX = new JRadioButton();
    /**
     * Radiobutton for discarding
     */
    private final JRadioButton DISCARD_BOX = new JRadioButton();
    /**
     * Buttongroup for radiobuttons.
     */
    private final ButtonGroup BGROUP = new ButtonGroup();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     */
    public BugsPaneItem() {
        super(
                I18n.tr("Bug Reports"),
                I18n
                        .tr("You can choose how bug reports should be sent. To view an example bug report, click \'View Example\'. Choosing \'Always Send Immediately\' will immediately contact the bug server when FrostWire encounters an internal error. Choosing \'Always Ask for Review\' will tell FrostWire to ask for your approval before sending a bug to the bug server. Choosing \'Always Discard All Errors\' will cause FrostWire to ignore all bugs (this is not recommended)."));
        localClientInfoFactory = LimeWireModule.instance().getLimeWireGUIModule().getLimeWireGUI().getLocalClientInfoFactory();
        /*
          The 'View Example Bug' string
         */
        String VIEW_EXAMPLE = I18n.tr("View Example");
        JButton example = new JButton(VIEW_EXAMPLE);
        example.addActionListener(ae -> {
            Exception e = new Exception("Example Bug");
            LocalClientInfo info = localClientInfoFactory.
                    createLocalClientInfo(e, Thread.currentThread().getName(), "Example", false);
            BugManager.instance().handleBug(e, "test-bug-report-thread", info.toBugReport());
        });
        SEND_BOX.setText(I18n.tr("Always Send Immediately"));
        REVIEW_BOX.setText(I18n.tr("Always Ask For Review"));
        DISCARD_BOX.setText(I18n.tr("Always Discard All Errors"));
        DEADLOCK_OPTION.setText(I18n.tr("Send Errors Automatically if FrostWire is Frozen"));
        BGROUP.add(SEND_BOX);
        BGROUP.add(REVIEW_BOX);
        BGROUP.add(DISCARD_BOX);
        add(SEND_BOX);
        add(REVIEW_BOX);
        add(DISCARD_BOX);
        add(DEADLOCK_OPTION);
        add(getVerticalSeparator());
        add(getVerticalSeparator());
        JPanel examplePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        examplePanel.add(example);
        GUIUtils.restrictSize(examplePanel, SizePolicy.RESTRICT_HEIGHT);
        add(examplePanel);
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        if (BugSettings.IGNORE_ALL_BUGS.getValue())
            BGROUP.setSelected(DISCARD_BOX.getModel(), true);
        else if (BugSettings.USE_AUTOMATIC_BUG.getValue())
            BGROUP.setSelected(SEND_BOX.getModel(), true);
        else
            BGROUP.setSelected(REVIEW_BOX.getModel(), true);
        DEADLOCK_OPTION.setSelected(BugSettings.SEND_DEADLOCK_BUGS.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        ButtonModel bm = BGROUP.getSelection();
        if (bm.equals(SEND_BOX.getModel())) {
            BugSettings.IGNORE_ALL_BUGS.setValue(false);
            BugSettings.USE_AUTOMATIC_BUG.setValue(true);
        } else if (bm.equals(DISCARD_BOX.getModel())) {
            BugSettings.IGNORE_ALL_BUGS.setValue(true);
            BugSettings.USE_AUTOMATIC_BUG.setValue(false);
        } else if (bm.equals(REVIEW_BOX.getModel())) {
            BugSettings.IGNORE_ALL_BUGS.setValue(false);
            BugSettings.USE_AUTOMATIC_BUG.setValue(false);
        }
        BugSettings.SEND_DEADLOCK_BUGS.setValue(DEADLOCK_OPTION.isSelected());
        return false;
    }

    public boolean isDirty() {
        if (DEADLOCK_OPTION.isSelected() != BugSettings.SEND_DEADLOCK_BUGS.getValue())
            return true;
        if (BGROUP.getSelection().equals(DISCARD_BOX.getModel()))
            return !BugSettings.IGNORE_ALL_BUGS.getValue();
        if (BGROUP.getSelection().equals(SEND_BOX.getModel()))
            return BugSettings.IGNORE_ALL_BUGS.getValue() ||
                    !BugSettings.USE_AUTOMATIC_BUG.getValue();
        return BugSettings.IGNORE_ALL_BUGS.getValue() ||
                BugSettings.USE_AUTOMATIC_BUG.getValue();
    }
}
