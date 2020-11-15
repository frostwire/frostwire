package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.StatusBarSettings;

import javax.swing.*;

/**
 * This class defines the panel in the options window that allows the user
 * to change whether the firewall indicator is shown in the status bar.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class StatusBarFirewallPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Firewall Indicator");
    private final static String LABEL = I18n.tr("You can display your firewall status in the status bar.");
    private final JCheckBox CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     *
     */
    public StatusBarFirewallPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant for the key of the locale-specific <tt>String</tt> for whether
          the firewall status should be displayed in the status bar.
         */
        String CHECK_BOX_LABEL = I18n.tr("Show Firewall Indicator:");
        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL,
                CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        CHECK_BOX.setSelected(StatusBarSettings.FIREWALL_DISPLAY_ENABLED.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        if (!isDirty())
            return false;
        StatusBarSettings.FIREWALL_DISPLAY_ENABLED.setValue(CHECK_BOX.isSelected());
        GUIMediator.instance().getStatusLine().refresh();
        return false;
    }

    public boolean isDirty() {
        return StatusBarSettings.FIREWALL_DISPLAY_ENABLED.getValue() != CHECK_BOX.isSelected();
    }
}
